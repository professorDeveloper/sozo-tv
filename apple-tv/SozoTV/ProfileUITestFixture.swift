#if DEBUG
import Foundation

/// In-memory only. All requests use URLProtocol; this session can never contact a live backend.
enum ProfileUITestFixture {
    @MainActor static func makeDeviceLinkSession(reset: Bool) -> SessionStore {
        FixtureProtocol.reset()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [FixtureProtocol.self]
        let api = APIClient(base: URL(string: "https://fixture.invalid/api")!, session: URLSession(configuration: config))
        let storage = KeychainCredentialStorage(service: "com.saikou.sozotv.device-link-smoke")
        if reset { try? storage.clear() }
        let defaults = UserDefaults(suiteName: "sozo.tv.device-link-fixture")!
        return SessionStore(api: api, storage: storage, defaults: defaults)
    }
    @MainActor static func makeSession() -> SessionStore {
        FixtureProtocol.reset()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [FixtureProtocol.self]
        let api = APIClient(base: URL(string: "https://fixture.invalid/api")!, session: URLSession(configuration: config))
        let defaults = UserDefaults(suiteName: "sozo.tv.ui-fixture")!
        defaults.removePersistentDomain(forName: "sozo.tv.ui-fixture")
        for id in ["adult", "locked", "kids"] { HouseholdProfileSettings(scope: "fixture-device:\(id)").remove() }
        return SessionStore(api: api, storage: FixtureCredentials(), defaults: defaults)
    }
    @MainActor static func bootstrap(_ session: SessionStore) async { await session.loadProfiles() }
}

private final class FixtureCredentials: CredentialStorage {
    private var value: DeviceCredentials? = .init(accessToken: "fixture-access", refreshToken: "fixture-refresh", sessionId: "fixture-device")
    func load() throws -> DeviceCredentials? { value }
    func save(_ credentials: DeviceCredentials) throws { value = credentials }
    func clear() throws { value = nil }
}

private final class FixtureProtocol: URLProtocol, @unchecked Sendable {
    static let lock = NSLock()
    static var profiles: [[String: Any]] = []
    static var pins: [String: String] = [:]
    static var nextID = 4
    static var notificationRead = false
    static var displayName = "Sozo Tester"
    static var friendsSearchAttempts = 0
    static var accountAttempts = 0
    static var supportAttempts: [Int:Int] = [:]
    static var notificationAttempts: [Int:Int] = [:]
    static var rich: Bool { ProcessInfo.processInfo.arguments.contains("--browse-ui-fixture") }
    static func reset() {
        lock.lock(); defer { lock.unlock() }
        profiles = [
            ["id": "adult", "name": "Azamov", "avatar": "fox", "color": "#e50914", "isKids": false, "isDefault": true, "hasPin": false],
            ["id": "locked", "name": "Private", "avatar": "owl", "color": "#2563eb", "isKids": false, "isDefault": false, "hasPin": true],
            ["id": "kids", "name": "Kids", "avatar": "panda", "color": "#22c55e", "isKids": true, "isDefault": false, "hasPin": false]
        ]
        pins = ["locked": "1234"]; nextID = 4; notificationRead = false; displayName = "Sozo Tester"; accountAttempts = 0; friendsSearchAttempts = 0; notificationAttempts = [:]; supportAttempts = [:]
    }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock()
        let result = Self.handle(request)
        Self.lock.unlock()
        let response = HTTPURLResponse(url: request.url!, statusCode: result.0, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
        let data = (try? JSONSerialization.data(withJSONObject: result.1, options: [.fragmentsAllowed])) ?? Data("{}".utf8)
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
    private static func body(_ request: URLRequest) -> [String: Any] {
        var data = request.httpBody ?? Data()
        if let stream = request.httpBodyStream {
            stream.open(); defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 2048)
            while stream.hasBytesAvailable { let n = stream.read(&buffer, maxLength: buffer.count); if n <= 0 { break }; data.append(buffer, count: n) }
        }
        return ((try? JSONSerialization.jsonObject(with: data)) as? [String: Any]) ?? [:]
    }
    private static func fail(_ status: Int, _ code: String, _ message: String) -> (Int, Any) {
        (status, ["code": code, "message": message])
    }
    private static func mediaCard(_ index: Int) -> [String: Any] {
        ["provider":"fixture","title":index == 1 ? "The Glass Horizon" : "Discovery \(index)","contentUrl":"https://fixture.invalid/title/\(index)","type":"series"]
    }
    private static func handle(_ request: URLRequest) -> (Int, Any) {
        guard request.url?.host == "fixture.invalid" else { return fail(400, "FIXTURE_ONLY", "Live requests are disabled") }
        let path = request.url!.path.replacingOccurrences(of: "/api/", with: "")
        let method = request.httpMethod ?? "GET", input = body(request)
        let scope = request.value(forHTTPHeaderField: "X-Sozo-Profile") ?? "adult"
        let manager = profiles.first { $0["id"] as? String == scope }
        if path == "auth/device/code" { return (201, ["device_code": "fixture-code", "user_code": "TEST1234", "expires_in": 600, "interval": 1]) }
        if path == "auth/device/token" || path == "auth/device/refresh" {
            return (200, ["status": "approved", "accessToken": "fixture-access", "refreshToken": "fixture-refresh", "sessionId": "fixture-device"])
        }
        if path == "auth/device/logout" { return (200, ["message": "Signed out"]) }
        if path == "auth/profiles", method == "GET" {
            return (200, ["profiles": profiles, "max": 4, "activeProfileId": scope])
        }
        if path.hasPrefix("auth/profiles") {
            if path.hasSuffix("/verify-pin") {
                let id = path.split(separator: "/").dropLast().last.map(String.init) ?? ""
                guard let profile = profiles.first(where: { $0["id"] as? String == id }) else { return fail(404,"PROFILE_NOT_FOUND","Profile not found") }
                guard pins[id] == nil || pins[id] == input["pin"] as? String else { return fail(403,"PIN_INVALID","Incorrect PIN. Please try again.") }
                return (200, ["ok": true, "profile": profile])
            }
            guard manager?["isKids"] as? Bool != true else { return fail(403,"KIDS_PROFILE","Choose an adult profile") }
            if method == "POST" {
                guard profiles.count < 4 else { return fail(409,"PROFILE_LIMIT","Maximum four profiles") }
                let name = (input["name"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !name.isEmpty else { return fail(400,"NAME_INVALID","Enter a name") }
                guard !profiles.contains(where: { ($0["name"] as? String)?.lowercased() == name.lowercased() }) else { return fail(409,"PROFILE_NAME_TAKEN","Name already used") }
                let id = "fixture-\(nextID)"; nextID += 1
                var profile = input; profile["id"] = id; profile["name"] = name; profile["isDefault"] = false
                let pin = input["pin"] as? String
                profile["hasPin"] = pin?.isEmpty == false; profile.removeValue(forKey: "pin")
                if let pin, !pin.isEmpty { pins[id] = pin }
                profiles.append(profile); return (201, ["profile": profile])
            }
            let id = path.split(separator: "/").last.map(String.init) ?? ""
            guard let index = profiles.firstIndex(where: { $0["id"] as? String == id }) else { return fail(404,"PROFILE_NOT_FOUND","Profile not found") }
            let sensitive = method == "DELETE" || input["pin"] != nil || (input["isKids"] as? Bool).map { $0 != (profiles[index]["isKids"] as? Bool ?? false) } == true
            let ownerOverride = manager?["isDefault"] as? Bool == true && profiles[index]["isDefault"] as? Bool != true
            if sensitive, !ownerOverride, let pin = pins[id], input["currentPin"] as? String != pin { return fail(403,"PIN_INVALID","Current PIN is incorrect") }
            if method == "DELETE" {
                guard profiles[index]["isDefault"] as? Bool != true else { return fail(403,"DEFAULT_PROFILE","Cannot delete the default profile") }
                profiles.remove(at:index); pins.removeValue(forKey:id); return (200,["success":true])
            }
            if method == "PATCH" {
                for field in ["name", "avatar", "color", "isKids"] { if let value = input[field] { profiles[index][field] = value } }
                if let pin = input["pin"] as? String { pins[id] = pin.isEmpty ? nil : pin; profiles[index]["hasPin"] = !pin.isEmpty }
                return (200,["profile":profiles[index]])
            }
        }
        if path == "auth/profile" {
            if method == "PUT" && ProcessInfo.processInfo.arguments.contains("--retry-fixture") {
                accountAttempts += 1
                if accountAttempts == 1 { return fail(503,"RETRY_NAME","Name save failed once") }
            }
            if let name = input["displayName"] as? String { displayName = name }
            return (200,["user":["id":"fixture-user","username":"sozotester","displayName":displayName,"email":"fixture@example.test"]])
        }
        if path == "contents/providers" { return (200,["items":[["id":"fixture","name":"Demo source","browseOnly":false,"mode":"server","category":"movies","lang":"en"], ["id":"alternate","name":"Alternate source","mode":"server","category":"anime","lang":"ja"], ["id":"phone-only","name":"Phone only","mode":"client"], ["id":"unavailable","name":"Unavailable source","mode":"server"]]]) }
        if path == "contents/home" {
            if URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "provider" })?.value == "unavailable" { return fail(503,"SOURCE_DOWN","This source is temporarily unavailable.") }
            if rich && ProcessInfo.processInfo.arguments.contains("--season-scroll-fixture") {
                return (200,["banner":[mediaCard(1)],"sections":(0..<8).map { row in ["key":"row-\(row)","label":"Shelf \(row + 1)","items":(0..<18).map { mediaCard(100 + row * 100 + $0) }] }])
            }
            if rich { return (200,["banner":[mediaCard(1),mediaCard(2),mediaCard(3)],"sections":[["key":"popular","label":"Popular now","items":[mediaCard(1),mediaCard(2),mediaCard(3)]],["key":"latest","label":"Recently added","items":[mediaCard(4),mediaCard(5)]]]]) }
            return (200,["banner":[],"sections":[]])
        }
        if path == "contents/search", rich {
            let query = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []
            if query.first(where: { $0.name == "q" })?.value == "Error" { return fail(502,"SOURCE_UNAVAILABLE","This source is temporarily unavailable.") }
            if query.first(where: { $0.name == "q" })?.value == "Missing" { return (200,["items":[],"totalPages":1]) }
            let page = Int(query.first(where: { $0.name == "page" })?.value ?? "1") ?? 1
            if ProcessInfo.processInfo.arguments.contains("--duplicate-pages") {
                return (200,["items":page <= 2 ? [mediaCard(1),mediaCard(2)] : [mediaCard(3)],"totalPages":3])
            }
            return (200,["items":page == 1 ? [mediaCard(1),mediaCard(2)] : [mediaCard(2),mediaCard(3)],"totalPages":2])
        }
        if path == "contents/detail", rich { return (200,["title":"The Glass Horizon","description":"A journey beyond the familiar. Explore a complete title page, episodes and available video sources using your remote.","isSerial":true,"year":2026,"genres":["Adventure","Drama"]]) }
        if path == "contents/episodes", rich {
            if ProcessInfo.processInfo.arguments.contains("--resume-pages") {
                let page = Int(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "page" })?.value ?? "1") ?? 1
                return (200,["episodes": page == 1 ? [["episode":1,"label":"S1E1 · First","mediaRef":"fixture:1"],["episode":2,"label":"S1E2 · Second","mediaRef":"fixture:2"]] : [["episode":3,"label":"S2E1 · Resumed","mediaRef":"fixture:3"]],"totalPages":2])
            }
            if ProcessInfo.processInfo.arguments.contains("--season-scroll-fixture") {
                return (200,["isSerial":true,"totalPages":1,"episodes":(1...4).flatMap { season in (1...12).map { episode in ["episode":(season - 1) * 12 + episode,"label":"S\(season)E\(episode) · Chapter \(episode)","mediaRef":"vidapi:tv:tt123:42:\(season):\(episode)"] as [String:Any] } }])
            }
            let page = Int(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "page" })?.value ?? "1") ?? 1
            if ProcessInfo.processInfo.arguments.contains("--next-page-overlap") {
                let episodes: [[String:Any]] = page == 1 ? [["episode":1,"label":"Episode 1 · First light","mediaRef":"fixture:episode:1"],["episode":2,"label":"Episode 2 · The crossing","mediaRef":"fixture:episode:2"]] : page == 2 ? [["episode":2,"label":"Episode 2 · The crossing","mediaRef":"fixture:episode:2"]] : [["episode":3,"label":"Episode 3 · Homeward","mediaRef":"fixture:episode:3"]]
                return (200,["episodes":episodes,"totalPages":3])
            }
            let episodes: [[String:Any]] = page == 1 ? [["episode":1,"label":"Episode 1 · First light","mediaRef":"fixture:episode:1"],["episode":2,"label":"Episode 2 · The crossing","mediaRef":"fixture:episode:2"]] : [["episode":2,"label":"Episode 2 · The crossing","mediaRef":"fixture:episode:2"],["episode":3,"label":"Episode 3 · Homeward","mediaRef":"fixture:episode:3"]]
            return (200,["episodes":episodes,"totalPages":2])
        }
        if path == "contents/media", ProcessInfo.processInfo.arguments.contains("--native-detail-playback") {
            return (200,["videoSources":[["quality":"720p","videoUrl":"http://127.0.0.1:38657/clip.mp4","type":"mp4"]],"subtitles":[["label":"English","file":"http://127.0.0.1:38657/captions.vtt"]]])
        }
        if path == "contents/media", rich { return (200,["videoSources":[["quality":"Embed","videoUrl":"https://fixture.invalid/e/not-video","type":"iframe"],["quality":"720p","videoUrl":"https://fixture.invalid/720.mp4","type":"mp4"],["quality":"1080p","videoUrl":"https://fixture.invalid/1080.mp4","type":"mp4"]]]) }
        if path == "auth/history", method == "GET", ProcessInfo.processInfo.arguments.contains("--resume-pages") {
            return (200,["items":[["provider":"fixture","contentUrl":"https://fixture.invalid/title/1","contentId":"https://fixture.invalid/title/1","title":"The Glass Horizon","isSerial":true,"episodeNumber":3,"episodeIndex":2,"positionMs":120000,"durationMs":600000]]])
        }
        if path == "auth/history", method == "GET", rich { return (200,["items":[["provider":"fixture","contentUrl":"https://fixture.invalid/title/1","contentId":"https://fixture.invalid/title/1","title":"The Glass Horizon","isSerial":true,"episodeNumber":2,"episodeIndex":1,"positionMs":120000,"durationMs":600000]]]) }

        if path == "auth/devices" { return (200,["devices":[["id":"fixture-device","deviceName":"Apple TV fixture","lastSeenAt":"2026-10-06T12:00:00Z"]]]) }
        if path.hasPrefix("auth/devices/") { return (200,["success":true]) }
        if path == "notifications", ProcessInfo.processInfo.arguments.contains("--retry-fixture") {
            let page = Int(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "page" })?.value ?? "1") ?? 1
            notificationAttempts[page, default: 0] += 1
            if page == 2 && notificationAttempts[page] == 1 { return fail(503,"RETRY_NOTICES","Notification page failed once") }
            return (200,["items":[["id":"notice-\(page)","title":"Page \(page)","body":"Notification from page \(page)","read":true]],"totalPages":2])
        }
        if path == "notifications" { return (200,["items":[["id":"notice","title":"Welcome to Sozo","body":"Your household is ready.","read":notificationRead]],"totalPages":1]) }
        if path == "notifications/notice/read" { notificationRead = true; return (200,["id":"notice","title":"Welcome to Sozo","body":"Your household is ready.","read":true]) }
        if path == "social/users/search", ProcessInfo.processInfo.arguments.contains("--friends-retry-fixture") {
            friendsSearchAttempts += 1
            if friendsSearchAttempts == 1 { return fail(503,"TEMPORARY","Friend search failed once") }
            return (200,["items":[["id":"alex","username":"alex","displayName":"Alex found","relation":"none"]]])
        }
        if path == "social/friends" { return (200,["items":[["user":["id":"friend","username":"sam","displayName":"Sam"]]]]) }
        if path.hasPrefix("social/") { return (200,["items":[]]) }
        if path == "support/tickets", rich {
            if ProcessInfo.processInfo.arguments.contains("--support-retry-fixture") {
                let page = Int(URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "page" })?.value ?? "1") ?? 1
                supportAttempts[page, default: 0] += 1
                if page == 2 && supportAttempts[page] == 1 { return fail(503,"TEMPORARY","Support page failed once") }
                return (200,["items":[["id":"ticket-\(page)","preview":"Support page \(page)","category":"playback","status":"open","userUnreadCount":0]],"totalPages":2])
            }
            return (200,["items":[["id":"closed-ticket","preview":"Resolved playback question","category":"playback","status":"closed","userUnreadCount":0]],"totalPages":1])
        }
        if path == "support/tickets/closed-ticket", rich {
            return (200,["ticket":["id":"closed-ticket","status":"closed","messages":[["id":"message-1","from":"admin","body":"Your request has been resolved.","at":"2026-10-06T12:00:00Z"]]]])
        }
        if path.hasPrefix("support") { return (200,["items":[],"success":true]) }
        if path.hasPrefix("auth/history") || path.hasPrefix("auth/favorites") || path.hasPrefix("auth/lists/") || path == "contents/search" { return (200,["items":[]]) }
        return fail(404,"FIXTURE_ROUTE","No fixture for \(path)")
    }
}
#endif
