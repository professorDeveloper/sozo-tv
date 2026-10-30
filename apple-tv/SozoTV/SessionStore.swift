import Foundation
import Combine

@MainActor
final class SessionStore: ObservableObject {
    @Published private(set) var token: String?
    @Published private(set) var profiles: [HouseholdProfile] = []
    @Published private(set) var activeProfile: HouseholdProfile?
    @Published private(set) var maxProfiles = 4
    @Published private(set) var busy = false
    @Published var error: String?
    @Published private(set) var revision: UInt64 = 0

    var canManage: Bool { activeProfile.map { !$0.isKids } ?? false }
    var canAdd: Bool { canManage && profiles.count < maxProfiles }
    var profileScopeIdentity: String { "\(credentials?.sessionId ?? "signed-out"):\(activeProfile?.id ?? "default")" }

    private let api: APIClient
    private let storage: CredentialStorage
    private let defaults: UserDefaults
    private let profileKey = "sozo.appleTV.profileId"
    private var credentials: DeviceCredentials?
    private var accountGeneration: UInt64 = 0
    private var refreshTask: Task<DeviceCredentials, Error>?
    private var profileLoadGeneration: UInt64 = 0

    init(api: APIClient = .shared, storage: CredentialStorage = KeychainCredentialStorage(), defaults: UserDefaults = .standard) {
        self.api = api; self.storage = storage; self.defaults = defaults
        // The early prototype stored a short-lived bearer in preferences. It cannot rotate safely.
        defaults.removeObject(forKey: "sozo.appleTV.accessToken")
        do { credentials = try storage.load(); token = credentials?.accessToken }
        catch { self.error = error.localizedDescription }
    }

    func createDeviceCode() async throws -> DeviceCode {
        try await api.request("auth/device/code", method: "POST", body: ["deviceName": "Apple TV"])
    }
    func pollDeviceToken(_ code: String) async throws -> DeviceToken {
        try await api.request("auth/device/token", method: "POST", body: ["device_code": code])
    }

    func accept(_ result: DeviceToken) async throws {
        guard let access = result.accessToken, !access.isEmpty,
              let refresh = result.refreshToken, !refresh.isEmpty else { throw APIError.incompleteDeviceSession }
        let value = DeviceCredentials(accessToken: access, refreshToken: refresh, sessionId: result.sessionId ?? UUID().uuidString)
        try storage.save(value)
        accountGeneration &+= 1; revision &+= 1
        credentials = value; token = value.accessToken; refreshTask = nil
        profiles = []; activeProfile = nil; error = nil
        defaults.removeObject(forKey: profileKey)
        // RootView loads profiles after sign-in; the pairing view task is canceled on dismissal.
    }

    /// Authenticated calls capture their profile before awaiting; replies from old scopes never escape.
    func request<T: Decodable>(_ path: String, query: [URLQueryItem] = [], method: String = "GET",
                               body: [String: Any]? = nil, profileScoped: Bool = true) async throws -> T {
        let generation = accountGeneration, scopeRevision = revision
        let profile = profileScoped ? activeProfile?.id : nil
        guard let initial = credentials?.accessToken else { throw APIError.unauthenticated }
        func validate() throws {
            guard generation == accountGeneration, (!profileScoped || scopeRevision == revision) else { throw APIError.staleSession }
            try Task.checkCancellation()
        }
        do {
            let value: T = try await api.request(path, query: query, method: method, body: body, token: initial, profileId: profile)
            try validate(); return value
        } catch let failure as APIError where failure.statusCode == 401 {
            try validate()
            let fresh = try await refreshedToken(rejected: initial)
            try validate()
            let value: T = try await api.request(path, query: query, method: method, body: body, token: fresh, profileId: profile)
            try validate(); return value
        }
    }

    private func refreshedToken(rejected: String) async throws -> String {
        guard let current = credentials else { throw APIError.unauthenticated }
        if current.accessToken != rejected { return current.accessToken }
        if let refreshTask { return try await refreshTask.value.accessToken }
        let generation = accountGeneration
        let task = Task<DeviceCredentials, Error> { [self] in
            defer { if generation == accountGeneration { refreshTask = nil } }
            do {
                let response: DeviceToken = try await api.request("auth/device/refresh", method: "POST",
                                                                body: ["refreshToken": current.refreshToken])
                guard let access = response.accessToken, !access.isEmpty,
                      let refresh = response.refreshToken, !refresh.isEmpty else { throw APIError.incompleteDeviceSession }
                let value = DeviceCredentials(accessToken: access, refreshToken: refresh, sessionId: current.sessionId)
                guard generation == accountGeneration else {
                    // A logout racing successful rotation must revoke the newly issued token too.
                    let _: MessageResponse? = try? await api.request("auth/device/logout", method: "POST", body: ["refreshToken": refresh])
                    throw APIError.staleSession
                }
                do { try storage.save(value) }
                catch {
                    let _: MessageResponse? = try? await api.request("auth/device/logout", method: "POST", body: ["refreshToken": refresh])
                    if generation == accountGeneration { clearSession() }
                    throw error
                }
                credentials = value; token = access
                return value
            } catch {
                if generation == accountGeneration,
                   let failure = error as? APIError, failure.statusCode == 401 || failure.statusCode == 403 {
                    clearSession()
                }
                throw error
            }
        }
        refreshTask = task
        return try await task.value.accessToken
    }

    func loadProfiles() async {
        guard token != nil else { return }
        profileLoadGeneration &+= 1
        let load = profileLoadGeneration, generation = accountGeneration, before = revision
        busy = true; error = nil
        defer { if load == profileLoadGeneration { busy = false } }
        do {
            let value: ProfilesEnvelope = try await request("auth/profiles")
            guard generation == accountGeneration, before == revision, load == profileLoadGeneration else { return }
            profiles = value.profiles; maxProfiles = value.max
            if let selected = activeProfile {
                let updated = value.profiles.first { $0.id == selected.id }
                if let updated, updated != selected { activate(updated) }
                else { activeProfile = updated }
                if activeProfile == nil { revision &+= 1; defaults.removeObject(forKey: profileKey) }
            } else {
                // PIN profiles always pass through selection verification after relaunch.
                let saved = defaults.string(forKey: profileKey)
                let candidate = value.profiles.first { $0.id == saved && !$0.hasPin }
                    ?? (value.profiles.count == 1 ? value.profiles.first(where: { !$0.hasPin }) : nil)
                if let candidate { activate(candidate) }
            }
        } catch {
            if generation == accountGeneration, before == revision, load == profileLoadGeneration,
               !(error is CancellationError) { self.error = error.localizedDescription }
        }
    }

    func select(_ profile: HouseholdProfile, pin: String? = nil) async throws {
        // Cards and dialogs may retain a snapshot while a refresh changes protection or kids mode.
        // Selection must use the latest server-backed profile, never the caller's stale flags.
        guard let current = profiles.first(where: { $0.id == profile.id }) else { throw APIError.staleSession }
        let selected: HouseholdProfile
        if current.hasPin {
            guard let pin, !pin.isEmpty else { throw APIError.pinRequired }
            let response: ProfileResponse = try await request("auth/profiles/\(current.id)/verify-pin", method: "POST", body: ["pin": pin])
            selected = response.profile
        } else { selected = current }
        guard profiles.contains(where: { $0.id == selected.id }) else { throw APIError.staleSession }
        activate(selected)
    }

    private func activate(_ profile: HouseholdProfile) {
        revision &+= 1
        activeProfile = profile
        defaults.set(profile.id, forKey: profileKey)
        error = nil
    }

    func createProfile(_ draft: ProfileDraft) async throws -> HouseholdProfile {
        guard canAdd else { throw APIError.server(status: 403, code: "PROFILE_LIMIT", message: "A new profile cannot be added.") }
        let response: ProfileResponse = try await request("auth/profiles", method: "POST", body: draft.body)
        invalidateProfileLoad()
        profiles.append(response.profile)
        return response.profile
    }

    func updateProfile(id: String, draft: ProfileDraft, currentPin: String? = nil) async throws -> HouseholdProfile {
        guard canManage else { throw APIError.server(status: 403, code: "KIDS_PROFILE", message: "Switch to an adult profile to manage profiles.") }
        var body = draft.body
        if let currentPin { body["currentPin"] = currentPin }
        let response: ProfileResponse = try await request("auth/profiles/\(id)", method: "PATCH", body: body)
        invalidateProfileLoad()
        if let index = profiles.firstIndex(where: { $0.id == id }) { profiles[index] = response.profile }
        if activeProfile?.id == id { activate(response.profile) }
        return response.profile
    }

    func deleteProfile(id: String, currentPin: String? = nil) async throws {
        guard canManage, profiles.first(where: { $0.id == id })?.isDefault == false else {
            throw APIError.server(status: 403, code: "DEFAULT_PROFILE", message: "This profile cannot be deleted.")
        }
        var body: [String: Any] = [:]
        if let currentPin { body["currentPin"] = currentPin }
        let _: SuccessResponse = try await request("auth/profiles/\(id)", method: "DELETE", body: body)
        invalidateProfileLoad()
        HouseholdProfileSettings(scope: "\(credentials?.sessionId ?? "signed-out"):\(id)", defaults: defaults).remove()
        profiles.removeAll { $0.id == id }
        if activeProfile?.id == id {
            activeProfile = nil; revision &+= 1; defaults.removeObject(forKey: profileKey)
        }
    }

    private func invalidateProfileLoad() {
        profileLoadGeneration &+= 1; busy = false
    }

    func signOut() {
        let refresh = credentials?.refreshToken
        clearSession()
        if let refresh {
            Task { let _: MessageResponse? = try? await api.request("auth/device/logout", method: "POST", body: ["refreshToken": refresh]) }
        }
    }

    private func clearSession() {
        accountGeneration &+= 1; revision &+= 1; profileLoadGeneration &+= 1
        // Leave a rotating request alive so it can revoke its result if it finishes after logout.
        refreshTask = nil
        for profile in profiles {
            HouseholdProfileSettings(scope: "\(credentials?.sessionId ?? "signed-out"):\(profile.id)", defaults: defaults).remove()
        }
        do { try storage.clear() } catch { self.error = error.localizedDescription }
        defaults.removeObject(forKey: profileKey)
        credentials = nil; token = nil; profiles = []; activeProfile = nil; busy = false
    }
}
