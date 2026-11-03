import Foundation

/// Device-link polling rides out brief outages and rate limits until the code itself expires.
enum DeviceLinkPolling {
    static let maximumDelay: TimeInterval = 60

    static func isTransient(_ error: Error) -> Bool {
        if let error = error as? APIError {
            if case .invalidResponse = error { return true }
            guard let status = error.statusCode else { return false }
            return status < 0 || status == 408 || status == 429 || status >= 500
        }
        guard let error = error as? URLError else { return false }
        return [.timedOut, .networkConnectionLost, .notConnectedToInternet, .cannotConnectToHost, .cannotFindHost,
                .dnsLookupFailed, .badServerResponse, .resourceUnavailable, .dataNotAllowed, .internationalRoamingOff,
                .secureConnectionFailed].contains(error.code)
    }

    /// The server's interval while healthy, doubling per consecutive failure; Retry-After is a floor.
    static func delay(interval: Int, failures: Int, retryAfter: TimeInterval? = nil) -> TimeInterval {
        let base = TimeInterval(max(interval, 1))
        let backoff = base * pow(2, Double(min(max(failures, 0), 6)))
        return min(maximumDelay, max(backoff, retryAfter ?? 0))
    }
}
