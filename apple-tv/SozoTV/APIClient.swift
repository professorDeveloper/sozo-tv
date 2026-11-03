import Foundation

actor APIClient {
    static let shared = APIClient()
    private let base: URL
    private let session: URLSession
    private let decoder = JSONDecoder()

    // Encoded the same way as AppConstants in the phone app.
    private static let defaultBase: URL = {
        let key = Array("1v_a2f9_y3k_n1p_0Z0s".utf8.reversed())
        let bytes = Array(Data(base64Encoded: "G0QuQCxKHkE+G1oKMEMJHAAlF1wcRnRdOl9QHjY=")!)
        let plain = bytes.enumerated().map { $1 ^ key[$0 % key.count] }
        return URL(string: String(decoding: plain, as: UTF8.self))!
    }()

    /// The backend allows /contents/media 60 s to resolve a slow provider before it answers.
    static let mediaTimeout: TimeInterval = 75
    static let defaultTimeout: TimeInterval = 30
    /// A throttled GET waits this long at most before its single retry.
    static let maximumRetryWait: TimeInterval = 5
    static let mediaSegmentEndpoint = defaultBase.appendingPathComponent("contents/media-segment")

    init(base: URL = APIClient.defaultBase, session: URLSession? = nil) {
        self.base = base
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = APIClient.mediaTimeout + 15
        self.session = session ?? URLSession(configuration: config, delegate: SameOriginRedirects(), delegateQueue: nil)
    }

    func request<T: Decodable>(
        _ path: String, query: [URLQueryItem] = [], method: String = "GET",
        body: [String: Any]? = nil, token: String? = nil, profileId: String? = nil,
        timeout: TimeInterval = APIClient.defaultTimeout
    ) async throws -> T {
        guard !path.hasPrefix("/"), !path.contains("://"), !path.split(separator: "/").contains("..") else {
            throw APIError.invalidRequest
        }
        var parts = URLComponents(url: base.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { parts.queryItems = query }
        guard let url = parts.url else { throw APIError.invalidRequest }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = timeout
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("en", forHTTPHeaderField: "Accept-Language")
        request.setValue("SozoTV/tvOS", forHTTPHeaderField: "User-Agent")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let profileId { request.setValue(profileId, forHTTPHeaderField: "X-Sozo-Profile") }
        if let body {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        do { return try await send(request) }
        catch let failure as APIError where method == "GET" && failure.isThrottled {
            guard let wait = failure.retryAfter, wait <= APIClient.maximumRetryWait else { throw failure }
            try await Task.sleep(nanoseconds: UInt64(max(wait, 0.5) * 1_000_000_000))
            return try await send(request)
        }
    }

    private func send<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.http(-1) }
        guard 200..<300 ~= http.statusCode else {
            let payload = try? decoder.decode(ErrorPayload.self, from: data)
            throw APIError.server(status: http.statusCode, code: payload?.code, message: payload?.message,
                                  retryAfter: http.value(forHTTPHeaderField: "Retry-After").flatMap(APIError.retryAfter))
        }
        do { return try decoder.decode(T.self, from: data) }
        catch { throw APIError.invalidResponse }
    }

    func providers() async throws -> [Provider] { let catalog: ProviderCatalog = try await request("contents/providers"); return catalog.items }

    func home(provider: String) async throws -> HomePayload {
        try await request("contents/home", query: [.init(name: "provider", value: provider)])
    }

    func detail(provider: String, url: String) async throws -> MediaDetail {
        try await request("contents/detail", query: [
            .init(name: "provider", value: provider), .init(name: "url", value: url),
        ])
    }

    func episodes(provider: String, url: String) async throws -> EpisodesEnvelope {
        try await request("contents/episodes", query: [
            .init(name: "provider", value: provider), .init(name: "url", value: url),
            .init(name: "page", value: "1"), .init(name: "size", value: "100"),
        ])
    }

    func media(provider: String, ref: String) async throws -> MediaEnvelope {
        try await request("contents/media", query: [
            .init(name: "provider", value: provider), .init(name: "ref", value: ref),
            .init(name: "lang", value: "sub"),
        ], timeout: APIClient.mediaTimeout)
    }
}

private struct ErrorPayload: Decodable { let message: String?; let code: String? }

/// Never forward authentication to a redirect outside the configured API origin.
private final class SameOriginRedirects: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard let original = task.originalRequest?.url, let next = request.url,
              original.scheme == next.scheme, original.host == next.host, original.port == next.port else {
            completionHandler(nil); return
        }
        completionHandler(request)
    }
}

enum APIError: LocalizedError {
    case http(Int), server(status: Int, code: String?, message: String?, retryAfter: TimeInterval? = nil)
    case noPlayableSource, invalidRequest, invalidResponse, unauthenticated, staleSession, pinRequired, incompleteDeviceSession
    case keychain(Int32)
    var statusCode: Int? {
        switch self { case .http(let code): code; case .server(let code, _, _, _): code; default: nil }
    }
    var code: String? { if case .server(_, let code, _, _) = self { code } else { nil } }
    var retryAfter: TimeInterval? { if case .server(_, _, _, let wait) = self { wait } else { nil } }
    var isThrottled: Bool { statusCode == 429 || statusCode == 503 }

    /// Retry-After is either delay-seconds or an HTTP date.
    static func retryAfter(_ header: String) -> TimeInterval? {
        let value = header.trimmingCharacters(in: .whitespaces)
        if let seconds = TimeInterval(value), seconds >= 0 { return seconds }
        let format = DateFormatter()
        format.locale = Locale(identifier: "en_US_POSIX"); format.timeZone = TimeZone(identifier: "GMT")
        format.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        return format.date(from: value).map { max(0, $0.timeIntervalSinceNow) }
    }

    // Backend messages are Uzbek and mostly uncoded, so only the code and status choose the text.
    var errorDescription: String? {
        switch self {
        case .http(let code) where code < 0: String(localized: "Sozo could not be reached. Please try again.")
        case .http(let code): String(localized: "Sozo could not be reached (\(code)). Please try again.")
        case .server(let status, let code, _, let wait):
            switch code {
            case "PIN_INVALID": String(localized: "Incorrect PIN. Please try again.")
            case "PIN_REQUIRED": String(localized: "Enter the PIN for this profile.")
            case "PROFILE_LIMIT": String(localized: "The maximum number of profiles has been reached.")
            case "KIDS_PROFILE": String(localized: "Switch to an adult profile to continue.")
            case "DEFAULT_PROFILE": String(localized: "The default profile cannot be deleted or made a kids profile.")
            case "PROFILE_NOT_FOUND": String(localized: "This profile was removed. Choose a profile to continue.")
            case "PROFILE_NAME_TAKEN": String(localized: "This profile name is already in use.")
            case let code? where code == "SOURCE_DOWN" || code == "SOURCE_UNAVAILABLE" || code.hasPrefix("UPSTREAM_"):
                String(localized: "This source is temporarily unavailable.")
            default: Self.describe(status: status, retryAfter: wait)
            }
        case .noPlayableSource: String(localized: "This video could not be opened on Apple TV.")
        case .invalidRequest: String(localized: "The request address is invalid.")
        case .invalidResponse: String(localized: "Sozo sent a response this app could not read. Please try again later.")
        case .unauthenticated: String(localized: "Link this Apple TV again.")
        case .staleSession: String(localized: "The profile or account changed. Please try again.")
        case .pinRequired: String(localized: "Enter the PIN for this profile.")
        case .incompleteDeviceSession: String(localized: "The sign-in response was incomplete. Link this Apple TV again.")
        case .keychain: String(localized: "Your sign-in could not be saved securely.")
        }
    }

    static func describe(status: Int, retryAfter: TimeInterval?) -> String {
        let seconds = retryAfter.map { max(1, Int($0.rounded(.up))) }
        switch status {
        case 429:
            if let seconds { return String(localized: "Too many requests. Try again in \(seconds) seconds.") }
            return String(localized: "Too many requests. Please wait a moment and try again.")
        case 503:
            if let seconds { return String(localized: "Sozo is busy right now. Try again in \(seconds) seconds.") }
            return String(localized: "Sozo is busy right now. Please try again in a moment.")
        case 400: return String(localized: "The request could not be completed. Please try again.")
        case 401: return String(localized: "Link this Apple TV again.")
        case 403: return String(localized: "This account is not allowed to do this.")
        case 404: return String(localized: "This could not be found. It may have been removed.")
        case 409: return String(localized: "This changed on another device. Refresh and try again.")
        case 410: return String(localized: "This is no longer available.")
        case 408, 504: return String(localized: "Sozo took too long to respond. Please try again.")
        case 500...599: return String(localized: "Sozo is having trouble right now (\(status)). Please try again.")
        default: return String(localized: "Something went wrong (\(status)). Please try again.")
        }
    }
}
