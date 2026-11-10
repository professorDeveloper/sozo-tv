import SwiftUI

/// Flexible envelopes keep optional server fields from blanking an entire profile screen.
indirect enum ProfileJSON: Decodable {
    case object([String: ProfileJSON]), array([ProfileJSON]), string(String), number(Double), bool(Bool), null
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode([String: ProfileJSON].self) { self = .object(v) }
        else { self = .array(try c.decode([ProfileJSON].self)) }
    }
    subscript(_ key: String) -> ProfileJSON { if case .object(let v) = self { return v[key] ?? .null }; return .null }
    var string: String { if case .string(let v) = self { return v }; return "" }
    var number: Double { if case .number(let v) = self { return v }; return 0 }
    var bool: Bool { if case .bool(let v) = self { return v }; return false }
    var array: [ProfileJSON] { if case .array(let v) = self { return v }; return [] }
    var identity: String { self["id"].string.isEmpty ? self["_id"].string : self["id"].string }
    var libraryKey: String {
        if !self["key"].string.isEmpty { return self["key"].string }
        if !identity.isEmpty { return identity }
        return self["provider"].string + "|" + self["contentUrl"].string
    }
    var progress: Double {
        let duration = self["durationMs"].number, position = self["positionMs"].number
        guard duration.isFinite, position.isFinite, duration > 0 else { return 0 }
        return max(0, min(position / duration, 1))
    }
}

struct ProfileDestinationView: View {
    let destination: ProfileDestination
    @ViewBuilder var body: some View {
        switch destination {
        case .profiles: ManageProfilesView()
        case .security: ProfileSecurityView()
        case .library: ProfileLibraryView(history: false)
        case .history: ProfileLibraryView(history: true)
        case .activity: ProfileActivityView()
        case .connections: ProfileConnectionsView()
        case .sources: ProfileSourcesView()
        case .support: ProfileSupportView()
        case .about: ProfileAboutView()
        case .account: ProfileAccountView()
        case .notifications: NotificationsView()
        case .friends: FriendsView()
        }
    }
}

struct ProfileErrorView: View {
    let message: String
    let retry: () -> Void
    var body: some View {
        VStack(spacing: 16) {
            Label(message, systemImage: "exclamationmark.circle").foregroundStyle(.secondary)
            Button(action: retry) { Label("Try again", systemImage: "arrow.clockwise").padding(.vertical, 14).padding(.horizontal, 24) }
                .accessibilityIdentifier("profile.error.retry")
        }.frame(maxWidth: .infinity).padding(32).sozoContentSurface().accessibilityElement(children: .contain)
    }
}

struct ProfileAccountView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var user: ProfileJSON = .null
    @State private var name = ""
    @StateObject private var request = ProfileRequestState()
    @State private var savedName = ""
    private var busy: Bool { request.busy }
    private var error: String? { request.error }
    @State private var saved = false
    @State private var retrySave = false
    var body: some View {
        Form {
            Section("Account") {
                Text(user["email"].string).foregroundStyle(.secondary)
                TextField("Display name", text: $name).disabled(busy || session.activeProfile?.isKids == true).accessibilityIdentifier("account.name")
                Text("\(name.count) / 80 characters").font(.caption).foregroundStyle(name.count > 80 ? .red : .secondary)
                Button("Save name") { Task { await save() } }.disabled(busy || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || name.count > 80 || name == savedName || session.activeProfile?.isKids == true)
                if saved && name == savedName { Label("Saved", systemImage: "checkmark.circle") }
            }
            if let error { ProfileErrorView(message: error) { Task { if retrySave { await save() } else { await load() } } } }
            if busy { ProgressView("Updating account") }
            Section("Household") { NavigationLink("Manage profiles") { ManageProfilesView() } }
            Section("Privacy") { Text("Account identity is shared by your household. Viewing history and saved lists belong to the selected profile.").foregroundStyle(.secondary) }
        }.navigationTitle("My account").task { await load() }.onDisappear { request.cancel() }
    }
    private func load() async {
        retrySave = false
        await request.run {
            let result: ProfileJSON = try await session.request("auth/profile", profileScoped: false); return result["user"]
        } apply: { value in
            user = value; name = value["displayName"].string.isEmpty ? value["username"].string : value["displayName"].string
            savedName = name
            #if DEBUG
            if let argument = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--account-draft=") }) {
                name = String(argument.dropFirst("--account-draft=".count))
            }
            #endif
        }
    }
    private func save() async {
        let value = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !busy, session.activeProfile?.isKids != true, !value.isEmpty, value.count <= 80 else { return }
        saved = false; retrySave = true
        await request.run {
            let _: ProfileJSON = try await session.request("auth/profile", method: "PUT", body: ["displayName": value], profileScoped: false)
        } apply: { _ in saved = true; savedName = value; name = value }
    }
}

struct ProfileLibraryView: View {
    let history: Bool
    @EnvironmentObject private var session: SessionStore
    @State private var kind = "favorites"
    @State private var items: [ProfileJSON] = []
    @StateObject private var request = ProfileRequestState()
    @State private var loadedScope: ProfileContentScope?
    private var busy: Bool { request.busy }
    private var error: String? { request.error }
    private struct PendingRemoval {
        let item: ProfileJSON
        let path: String
        let scope: String
    }
    @State private var deleting: PendingRemoval?
    private var path: String { history ? "auth/history" : kind == "favorites" ? "auth/favorites" : "auth/lists/\(kind)" }
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 24) {
                if !history {
                    Picker("Collection", selection: $kind) { Text("Favorites").tag("favorites"); Text("Watch later").tag("watch-later"); Text("Watched").tag("watched") }.pickerStyle(.segmented).disabled(busy)
                }
                if busy { ProgressView(history ? "Loading watch history" : "Loading collection") }
                if let notice = request.notice { Label(notice, systemImage: "checkmark.circle").foregroundStyle(.green) }
                if let error { ProfileErrorView(message: error) { Task { await load() } } }
                if items.isEmpty && !busy && error == nil { Label(history ? "No viewing history in this profile yet" : "This collection is empty", systemImage: history ? "clock" : "bookmark").foregroundStyle(.secondary).padding(30) }
                ForEach(items, id: \.libraryKey) { item in
                    HStack(spacing: 22) {
                        TVArtwork(url: item["thumbnail"].string, width: 110, height: 150, cornerRadius: 14)
                        VStack(alignment: .leading, spacing: 10) {
                            Text(item["title"].string).font(.headline).lineLimit(2)
                            Text(item["provider"].string).foregroundStyle(.secondary)
                            if history && item["durationMs"].number > 0 { ProgressView(value: item.progress).frame(width: 380) }
                            if canOpen(item) {
                                NavigationLink(history && item["positionMs"].number > 0 ? "Resume" : "Watch") { DetailView(card: MediaCard(provider: item["provider"].string, title: item["title"].string, contentUrl: item["contentUrl"].string, slug: nil, thumbnail: item["thumbnail"].string, type: nil), provider: item["provider"].string, resume: history ? resumeContext(item) : nil) }
                            } else { Text("This item needs its original source on your other device.").font(.callout).foregroundStyle(.secondary) }
                        }
                        Spacer()
                        Button(role: .destructive) { deleting = PendingRemoval(item: item, path: path, scope: session.profileScopeIdentity) } label: { Label("Remove", systemImage: "trash") }.disabled(busy)
                    }.padding(24).sozoContentSurface()
                }
            }.padding(60)
        }.navigationTitle(history ? "Watch history" : "Library")
            .task(id: "\(session.profileScopeIdentity):\(kind)") { await load() }
            .onDisappear { request.cancel() }
            .confirmationDialog("Remove this item from this profile?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
                Button("Remove", role: .destructive) { if let pending = deleting { Task { await remove(pending) } }; deleting = nil }
                Button("Cancel", role: .cancel) { deleting = nil }
            }
    }
    private func canOpen(_ item: ProfileJSON) -> Bool {
        let provider = item["provider"].string, url = item["contentUrl"].string, contentID = item["contentId"].string
        return !provider.isEmpty && !provider.contains(":") && !url.isEmpty && !url.contains("\u{1}") && (contentID.isEmpty || contentID == url)
    }
    private func resumeContext(_ item: ProfileJSON) -> PlaybackHistoryContext {
        func optionalInt(_ value: ProfileJSON) -> Int? { if case .number(let number) = value { return Int(exactly: number.rounded(.towardZero)) }; return nil }
        return PlaybackHistoryContext(provider: item["provider"].string, contentUrl: item["contentUrl"].string, title: item["title"].string, thumbnail: item["thumbnail"].string, isSerial: item["isSerial"].bool, episodeIndex: optionalInt(item["episodeIndex"]), episodeNumber: optionalInt(item["episodeNumber"]), positionMs: max(0, Int64(exactly: item["positionMs"].number.rounded(.towardZero)) ?? 0))
    }
    private func load() async {
        let target = path
        let scope = ProfileContentScope(profile: session.profileScopeIdentity, path: target)
        if loadedScope != scope { items = []; deleting = nil; request.notice = nil; loadedScope = scope }
        await request.run {
            let value: ProfileJSON = try await session.request(target); return value["items"].array
        } apply: { rows in
            var seen = Set<String>()
            items = rows.filter { $0["deletedAt"].string.isEmpty && seen.insert($0.libraryKey).inserted }
        }
    }
    private func remove(_ pending: PendingRemoval) async {
        guard !busy else { return }
        guard pending.scope == session.profileScopeIdentity else { request.error = APIError.staleSession.localizedDescription; return }
        let item = pending.item
        let body = history && !item["key"].string.isEmpty ? ["key": item["key"].string] : ["contentUrl": item["contentUrl"].string]
        await request.run {
            let _: ProfileJSON = try await session.request(history ? "auth/history/item" : pending.path, method: "DELETE", body: body)
        } apply: { _ in
            if path == pending.path { items.removeAll { $0.libraryKey == item.libraryKey } }
            request.notice = "Removed from this profile"
        }
    }
}

struct ProfileActivityView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var entries: [ProfileJSON] = []
    @StateObject private var request = ProfileRequestState()
    private var busy: Bool { request.busy }
    private var error: String? { request.error }
    var body: some View {
        List {
            Section("Recent viewing") {
                if busy { ProgressView() }
                if let error { ProfileErrorView(message: error) { Task { await load() } } }
                if entries.isEmpty && !busy && error == nil { Text("No activity yet in this profile.") }
                ForEach(entries.indices, id: \.self) { i in
                    let e = entries[i]
                    VStack(alignment: .leading, spacing: 8) {
                        Text(e["title"].string).font(.headline)
                        Text(e["watchedAt"].string.prefix(10)).foregroundStyle(.secondary)
                        if e["durationMs"].number > 0 { Text("\(Int(e.progress * 100))% progress") }
                    }.padding(.vertical, 16).focusable()
                }
            }
        }.navigationTitle("Activity").task(id: session.profileScopeIdentity) { entries = []; await load() }.onDisappear { request.cancel() }
    }
    private func load() async {
        await request.run {
            let value: ProfileJSON = try await session.request("auth/history"); return value["items"].array
        } apply: { entries = $0.filter { $0["deletedAt"].string.isEmpty } }
    }
}

struct ProfileConnectionsView: View {
    @EnvironmentObject private var session: SessionStore
    @StateObject private var request = ProfileRequestState()
    @State private var devices: [ProfileJSON] = []
    private struct Removal { let id: String; let scope: String }
    @State private var pending: Removal?
    private var isKids: Bool { session.activeProfile?.isKids == true }
    var body: some View {
        Form {
            if isKids { Text("Choose an adult profile to manage linked devices.") }
            else {
                Section("Linked devices") {
                    if let error = request.error { ProfileErrorView(message: error) { Task { await load() } }.disabled(request.busy) }
                    if request.busy { ProgressView("Updating linked devices") }
                    if let notice = request.notice { Label(notice, systemImage: "checkmark.circle").foregroundStyle(.green) }
                    if devices.isEmpty && request.error == nil && !request.busy { Label("No linked devices", systemImage: "tv") }
                    ForEach(devices, id: \.identity) { device in
                        HStack(spacing: 28) {
                            Image(systemName: "tv").font(.title2)
                            VStack(alignment: .leading, spacing: 10) {
                                Text(device["deviceName"].string.isEmpty ? "TV" : device["deviceName"].string)
                                if session.profileScopeIdentity.hasPrefix(device.identity + ":") { Text("This Apple TV").foregroundStyle(SozoTheme.accent) }
                                if !device["lastSeenAt"].string.isEmpty { Text("Last active: \(device["lastSeenAt"].string.prefix(10))").foregroundStyle(.secondary) }
                            }
                            Spacer()
                            Button("Unlink", role: .destructive) { pending = Removal(id: device.identity, scope: session.profileScopeIdentity) }
                                .disabled(request.busy).accessibilityLabel("Unlink \(device["deviceName"].string)")
                        }.padding(.vertical, 16)
                    }
                    Button("Refresh devices") { Task { await load() } }.disabled(request.busy)
                }
            }
            Section("AniList, MyAnimeList & phone") { Text("Manage tracker connections in Sozo on your phone. Your household profiles are shared, while viewing history and saved titles stay with the profile you choose.").foregroundStyle(.secondary) }
        }.navigationTitle("Connections").task(id: session.profileScopeIdentity) { await load() }.onDisappear { request.cancel() }
        .confirmationDialog("Unlink this device? It will need to sign in again.", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } })) {
            Button("Unlink", role: .destructive) { if let value = pending { Task { await unlink(value) } }; pending = nil }
            Button("Cancel", role: .cancel) { pending = nil }
        }
    }
    private func load() async {
        guard !isKids else { request.cancel(); devices = []; return }
        await request.run {
            let value: ProfileJSON = try await session.request("auth/devices", profileScoped: false); return value["devices"].array
        } apply: { devices = $0.filter { !$0.identity.isEmpty } }
    }
    private func unlink(_ value: Removal) async {
        guard !request.busy, !value.id.isEmpty, !isKids, value.scope == session.profileScopeIdentity else { return }
        await request.run {
            let _: ProfileJSON = try await session.request("auth/devices/\(value.id)", method: "DELETE", profileScoped: false)
        } apply: { _ in
            devices.removeAll { $0.identity == value.id }; request.notice = "Device unlinked"
            if session.profileScopeIdentity.hasPrefix(value.id + ":") { session.signOut() }
        }
    }
}


struct ProfileAboutView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 26) {
            Text("SOZO").font(.system(size: 64, weight: .black)).foregroundStyle(SozoTheme.accent)
            Text("Apple TV").font(.largeTitle.bold())
            Text("Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")")
            Text("Made for your big screen").foregroundStyle(.secondary)
            Text("One household, a space for everyone. Your Sozo account keeps supported saved titles and viewing history connected across your devices.").frame(maxWidth: 1000, alignment: .leading)
        }.padding(70).navigationTitle("About")
    }
}
