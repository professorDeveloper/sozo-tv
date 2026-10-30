import Foundation

actor APIClient {
    static let shared = APIClient()
    private let base: URL
    private let session: URLSession
    private let decoder = JSONDecoder()

    init(base: URL = URL(string: "https://apisozo.azamov.me/api")!, session: URLSession? = nil) {
        self.base = base
        self.session = session ?? URLSession(configuration: .ephemeral, delegate: SameOriginRedirects(), delegateQueue: nil)
    }

    func request<T: Decodable>(
        _ path: String, query: [URLQueryItem] = [], method: String = "GET",
        body: [String: Any]? = nil, token: String? = nil, profileId: String? = nil
    ) async throws -> T {
        guard !path.hasPrefix("/"), !path.contains("://"), !path.split(separator: "/").contains("..") else {
            throw APIError.invalidRequest
        }
        var parts = URLComponents(url: base.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { parts.queryItems = query }
        guard let url = parts.url else { throw APIError.invalidRequest }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 30
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("en", forHTTPHeaderField: "Accept-Language")
        request.setValue("SozoTV/tvOS", forHTTPHeaderField: "User-Agent")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let profileId { request.setValue(profileId, forHTTPHeaderField: "X-Sozo-Profile") }
        if let body {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.http(-1) }
        guard 200..<300 ~= http.statusCode else {
            let payload = try? decoder.decode(ErrorPayload.self, from: data)
            throw APIError.server(status: http.statusCode, code: payload?.code, message: payload?.message)
        }
        return try decoder.decode(T.self, from: data)
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
        ])
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
    case http(Int), server(status: Int, code: String?, message: String?)
    case noPlayableSource, invalidRequest, unauthenticated, staleSession, pinRequired, incompleteDeviceSession
    case keychain(Int32)
    var statusCode: Int? {
        switch self { case .http(let code): code; case .server(let code, _, _): code; default: nil }
    }
    var code: String? { if case .server(_, let code, _) = self { code } else { nil } }
    var errorDescription: String? {
        switch self {
        case .http(let code): "Server error (\(code))"
        case .server(let status, let code, let message):
            switch code {
            case "PIN_INVALID": "Incorrect PIN. Please try again."
            case "PIN_REQUIRED": "Enter the PIN for this profile."
            case "PROFILE_LIMIT": "The maximum number of profiles has been reached."
            case "KIDS_PROFILE": "Switch to an adult profile to continue."
            case "DEFAULT_PROFILE": "The default profile cannot be deleted or made a kids profile."
            case "PROFILE_NOT_FOUND": "This profile could not be found."
            case "PROFILE_NAME_TAKEN": "This profile name is already in use."
            default: message ?? "Server error (\(status))"
            }
        case .noPlayableSource: "This video could not be opened on Apple TV."
        case .invalidRequest: "The request address is invalid."
        case .unauthenticated: "Link this Apple TV again."
        case .staleSession: "The profile or account changed. Please try again."
        case .pinRequired: "Enter the PIN for this profile."
        case .incompleteDeviceSession: "The sign-in response was incomplete. Link this Apple TV again."
        case .keychain: "Your sign-in could not be saved securely."
        }
    }
}
