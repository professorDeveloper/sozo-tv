import Foundation

precondition(DeviceLinkPolling.delay(interval: 5, failures: 0) == 5, "healthy polling keeps the server interval")
precondition(DeviceLinkPolling.delay(interval: 5, failures: 2) == 20, "failures back off exponentially")
precondition(DeviceLinkPolling.delay(interval: 5, failures: 40) == DeviceLinkPolling.maximumDelay, "backoff is capped")
precondition(DeviceLinkPolling.delay(interval: 5, failures: 1, retryAfter: 30) == 30, "Retry-After is honoured")
precondition(DeviceLinkPolling.delay(interval: 0, failures: 0) == 1, "zero interval never spins")
for status in [429, 500, 502, 503, 504] {
    precondition(DeviceLinkPolling.isTransient(APIError.server(status: status, code: nil, message: nil)), "\(status) retries")
}
for status in [400, 403, 404, 410] {
    precondition(!DeviceLinkPolling.isTransient(APIError.server(status: status, code: nil, message: nil)), "\(status) stops")
}
precondition(DeviceLinkPolling.isTransient(URLError(.timedOut)) && DeviceLinkPolling.isTransient(URLError(.notConnectedToInternet)), "network blips retry")
precondition(!DeviceLinkPolling.isTransient(URLError(.cancelled)) && !DeviceLinkPolling.isTransient(CancellationError()), "cancellation stops")
precondition(DeviceLinkPolling.isTransient(APIError.invalidResponse), "a garbled reply retries")
print("PASS: device-link polling backs off, honours Retry-After and stops only on permanent errors")
