import Foundation

private struct Stub {
    let status: Int
    let body: String
    var delay: Double = 0
    var headers: [String: String] = [:]
}
private final class StubProtocol: URLProtocol, @unchecked Sendable {
    static let lock = NSLock()
    static var handler: ((URLRequest) -> Stub)!
    private var stopped = false
    private let stateLock = NSLock()
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock(); let stub = Self.handler(request); Self.lock.unlock()
        DispatchQueue.global().asyncAfter(deadline: .now() + stub.delay) { [self] in
            stateLock.lock(); let canceled = stopped; stateLock.unlock()
            guard !canceled else { return }
            client?.urlProtocol(self, didReceive: HTTPURLResponse(url: request.url!, statusCode: stub.status,
                httpVersion: "HTTP/1.1", headerFields: stub.headers.merging(["Content-Type": "application/json"]) { a, _ in a })!, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data(stub.body.utf8))
            client?.urlProtocolDidFinishLoading(self)
        }
    }
    override func stopLoading() { stateLock.lock(); stopped = true; stateLock.unlock() }
}
private final class MemoryCredentials: CredentialStorage {
    var value: DeviceCredentials? = .init(accessToken: "old", refreshToken: "refresh-old", sessionId: "device-A")
    func load() throws -> DeviceCredentials? { value }
    func save(_ credentials: DeviceCredentials) throws { value = credentials }
    func clear() throws { value = nil }
}
private struct Value: Decodable { let value: Int }
private let profileJSON = """
{"profiles":[{"id":"p1","name":"One","isKids":false,"isDefault":true},{"id":"p2","name":"Two","isKids":false,"isDefault":false}],"activeProfileId":"p1","max":4}
"""
private func require(_ condition: @autoclosure () -> Bool, _ label: String) {
    guard condition() else { fatalError("FAILED: \(label)") }
}

@main struct SessionTransportChecks {
    @MainActor private static func make(_ memory: MemoryCredentials) -> SessionStore {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubProtocol.self]
        let api = APIClient(base: URL(string: "https://fixture.test/api")!, session: URLSession(configuration: config))
        let defaults = UserDefaults(suiteName: "sozo-session-test-\(UUID().uuidString)")!
        return SessionStore(api: api, storage: memory, defaults: defaults)
    }
    @MainActor static func main() async throws {
        try await concurrentRefresh()
        try await scopedResponse()
        try await logoutDuringRefresh()
        try await pinAndDraft()
        try await staleProfileSelection()
        try await removedProfile()
        try await errorPresentation()
        print("PASS: refresh coalescing and atomic persistence; profile response isolation; logout rotation revocation; PIN and draft contracts; stale profile selection protection; removed profile recovery; error presentation and Retry-After")
    }
    @MainActor static func concurrentRefresh() async throws {
        let memory = MemoryCredentials(); let session = make(memory)
        var refreshes = 0
        StubProtocol.handler = { request in
            if request.url!.path.hasSuffix("/refresh") {
                refreshes += 1
                return Stub(status: 200, body: "{\"accessToken\":\"new\",\"refreshToken\":\"refresh-new\"}", delay: 0.05)
            }
            return request.value(forHTTPHeaderField: "Authorization") == "Bearer new"
                ? Stub(status: 200, body: "{\"value\":7}") : Stub(status: 401, body: "{\"message\":\"expired\"}")
        }
        async let one: Value = session.request("protected")
        async let two: Value = session.request("protected")
        let values = try await [one, two]
        require(values.allSatisfy { $0.value == 7 }, "both requests retried")
        require(refreshes == 1, "refresh rotations must coalesce")
        require(memory.value?.refreshToken == "refresh-new", "rotated pair persisted")
        require(memory.value?.sessionId == "device-A", "device namespace survives rotation")
    }
    @MainActor static func scopedResponse() async throws {
        let session = make(MemoryCredentials())
        var header: String?
        StubProtocol.handler = { request in
            if request.url!.path.hasSuffix("/profiles") { return Stub(status: 200, body: profileJSON) }
            header = request.value(forHTTPHeaderField: "X-Sozo-Profile")
            return Stub(status: 200, body: "{\"value\":9}", delay: 0.1)
        }
        await session.loadProfiles()
        try await session.select(session.profiles[0])
        let pending = Task { let _: Value = try await session.request("history") }
        try await Task.sleep(nanoseconds: 20_000_000)
        try await session.select(session.profiles[1])
        do { try await pending.value; fatalError("Old-profile response escaped") }
        catch APIError.staleSession { }
        require(header == "p1", "request retains originating profile header")
        require(session.activeProfile?.id == "p2", "new active profile retained")
    }
    @MainActor static func logoutDuringRefresh() async throws {
        let memory = MemoryCredentials(); let session = make(memory)
        var revoked: [String] = []
        StubProtocol.handler = { request in
            if request.url!.path.hasSuffix("/logout") {
                // URLSession may provide POST bodies as a stream to URLProtocol.
                var data = request.httpBody ?? Data()
                if let stream = request.httpBodyStream { stream.open(); defer { stream.close() }; var b = [UInt8](repeating:0,count:1024)
                    while stream.hasBytesAvailable { let n = stream.read(&b,maxLength:b.count); if n <= 0 { break }; data.append(b,count:n) } }
                let json = (try? JSONSerialization.jsonObject(with: data)) as? [String:String]
                revoked.append(json?["refreshToken"] ?? "missing")
                return Stub(status:200,body:"{\"message\":\"bye\"}")
            }
            if request.url!.path.hasSuffix("/refresh") {
                return Stub(status:200,body:"{\"accessToken\":\"new\",\"refreshToken\":\"refresh-new\"}",delay:0.1)
            }
            return Stub(status:401,body:"{}")
        }
        let pending = Task { let _: Value = try await session.request("history") }
        try await Task.sleep(nanoseconds:30_000_000)
        session.signOut()
        do { try await pending.value; fatalError("Logged-out request succeeded") } catch { }
        try await Task.sleep(nanoseconds:30_000_000)
        require(memory.value == nil && session.token == nil, "logout cannot be undone by refresh")
        require(revoked.contains("refresh-new"), "rotation finishing after logout revoked")
    }
    @MainActor static func staleProfileSelection() async throws {
        let session = make(MemoryCredentials())
        var locked = false
        StubProtocol.handler = { request in
            if request.url!.path.hasSuffix("verify-pin") {
                return Stub(status: 403, body: "{\"message\":\"Wrong PIN\",\"code\":\"PIN_INVALID\"}")
            }
            return Stub(status: 200, body: "{\"profiles\":[{\"id\":\"p1\",\"name\":\"One\",\"hasPin\":\(locked),\"isKids\":\(locked)}],\"max\":4}")
        }
        await session.loadProfiles()
        let stale = session.profiles[0]
        locked = true
        await session.loadProfiles()
        require(session.profiles[0].hasPin, "server updated PIN state received")
        do {
            try await session.select(stale)
            fatalError("FAILED: stale unlocked snapshot bypassed current PIN protection")
        } catch APIError.pinRequired { }
        do { try await session.select(stale, pin: "1111"); fatalError("Stale profile skipped server PIN verification") }
        catch let error as APIError { require(error.code == "PIN_INVALID", "stale profile uses current verification requirement") }
        // Metadata-only changes must also use current restrictions, even without a PIN.
        StubProtocol.handler = { _ in
            Stub(status: 200, body: "{\"profiles\":[{\"id\":\"p1\",\"name\":\"Updated child\",\"hasPin\":false,\"isKids\":true}],\"max\":4}")
        }
        await session.loadProfiles()
        try await session.select(stale)
        require(session.activeProfile?.isKids == true && !session.canManage,
                "stale adult snapshot cannot restore management permission")
        require(session.activeProfile?.name == "Updated child", "selection keeps current display metadata")
    }

    @MainActor static func removedProfile() async throws {
        let session = make(MemoryCredentials())
        var listing = "{\"profiles\":[{\"id\":\"p1\",\"name\":\"One\",\"isDefault\":true},{\"id\":\"p2\",\"name\":\"Two\"},{\"id\":\"p3\",\"name\":\"Three\"}],\"max\":4}"
        StubProtocol.handler = { request in
            if request.url!.path.hasSuffix("/profiles") { return Stub(status: 200, body: listing) }
            return Stub(status: 404, body: "{\"message\":\"Profil topilmadi\",\"code\":\"PROFILE_NOT_FOUND\"}")
        }
        func settle(_ ids: [String]) async throws {
            for _ in 0..<50 where session.profiles.map(\.id) != ids { try await Task.sleep(nanoseconds: 10_000_000) }
            require(session.profiles.map(\.id) == ids, "profiles reloaded to \(ids)")
        }
        await session.loadProfiles()
        try await session.select(session.profiles[1])

        // Another device deleted the profile being managed, not the one watching.
        listing = "{\"profiles\":[{\"id\":\"p1\",\"name\":\"One\",\"isDefault\":true},{\"id\":\"p2\",\"name\":\"Two\"}],\"max\":4}"
        do { try await session.deleteProfile(id: "p3"); fatalError("Deleting a missing profile succeeded") }
        catch let error as APIError { require(error.code == "PROFILE_NOT_FOUND", "missing target error surfaces") }
        try await settle(["p1", "p2"])
        require(session.activeProfile?.id == "p2", "a missing target profile leaves the active profile selected")

        listing = "{\"profiles\":[{\"id\":\"p1\",\"name\":\"One\",\"hasPin\":true},{\"id\":\"p3\",\"name\":\"Three\"}],\"max\":4}"
        do { let _: Value = try await session.request("history"); fatalError("Removed profile request succeeded") }
        catch let error as APIError { require(error.code == "PROFILE_NOT_FOUND", "removed profile error surfaces") }
        try await settle(["p1", "p3"])
        require(session.activeProfile == nil, "removed active profile is dropped, so the picker shows")
    }

    @MainActor static func errorPresentation() async throws {
        let session = make(MemoryCredentials())
        var calls = 0
        StubProtocol.handler = { request in
            calls += 1
            if request.url!.path.hasSuffix("busy") {
                return calls == 1 ? Stub(status: 503, body: "{\"message\":\"Server hozir bandlik darajasida\"}", headers: ["Retry-After": "0"])
                    : Stub(status: 200, body: "{\"value\":5}")
            }
            if request.url!.path.hasSuffix("limited") {
                return Stub(status: 429, body: "{\"message\":\"Juda ko'p so'rov\"}", headers: ["Retry-After": "30"])
            }
            return Stub(status: 200, body: "{\"unexpected\":true}")
        }
        let value: Value = try await session.request("busy", profileScoped: false)
        require(value.value == 5 && calls == 2, "short Retry-After on a GET is waited out once")
        calls = 0
        do { let _: Value = try await session.request("limited", profileScoped: false); fatalError("429 succeeded") }
        catch let error as APIError {
            require(calls == 1 && error.retryAfter == 30, "long Retry-After is reported, not slept through")
            let text = error.localizedDescription
            require(text == "Too many requests. Try again in 30 seconds." && !text.contains("so'rov"), "no Uzbek backend text: \(text)")
        }
        do { let _: Value = try await session.request("shape", profileScoped: false); fatalError("bad shape decoded") }
        catch let error as APIError {
            guard case .invalidResponse = error else { fatalError("FAILED: decode error kept Foundation text") }
        }
        require(APIError.server(status: 403, code: nil, message: "Hisobingiz bloklangan").localizedDescription == "This account is not allowed to do this.", "uncoded 403 is English")
        require(APIError.retryAfter("Wed, 21 Oct 2015 07:28:00 GMT") == 0, "past HTTP date clamps to zero")
        require(!APIError.http(-1).localizedDescription.contains("-1"), "internal sentinel status is not shown")
    }

    @MainActor static func pinAndDraft() async throws {
        let session = make(MemoryCredentials())
        StubProtocol.handler = { request in
            if request.url!.path.hasSuffix("verify-pin") { return Stub(status:403,body:"{\"message\":\"Wrong PIN\",\"code\":\"PIN_INVALID\"}") }
            return Stub(status:200,body:"{\"profiles\":[{\"id\":\"pin\",\"name\":\"Locked\",\"hasPin\":true}],\"max\":4}")
        }
        await session.loadProfiles()
        require(session.activeProfile == nil, "locked profile never auto-selected")
        do { try await session.select(session.profiles[0]); fatalError("PIN bypass") } catch APIError.pinRequired { }
        do { try await session.select(session.profiles[0], pin:"1111"); fatalError("Bad PIN accepted") }
        catch let error as APIError { require(error.code == "PIN_INVALID", "machine-readable PIN error") }
        let draft = ProfileDraft(name:"Test")
        require(draft.body["avatar"] is NSNull, "nil avatar clears old avatar")
        require(draft.body["pin"] == nil, "nil PIN leaves unchanged")
        require(ProfileDraft(name:"Test",pin:"").body["pin"] as? String == "", "empty PIN removes")
    }
}
