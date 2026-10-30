import Foundation

/// Endpoint and household scope together identify visible profile-owned content.
struct ProfileContentScope: Equatable {
    let profile: String
    let path: String
}

/// Retain the failed page rather than deriving Retry from the last successful page.
struct InboxPagination {
    private(set) var page = 0
    private(set) var totalPages = 1
    private(set) var pendingPage = 1
    var hasMore: Bool { page > 0 && page < totalPages }
    var retryResets: Bool { pendingPage == 1 }

    mutating func reset() { page = 0; totalPages = 1; pendingPage = 1 }
    mutating func begin(reset: Bool) -> Int {
        pendingPage = reset ? 1 : page + 1
        return pendingPage
    }
    mutating func succeeded(page: Int, totalPages: Int) {
        self.page = page
        self.totalPages = max(1, totalPages)
        // A non-pagination operation, such as marking a notification read, retries by refreshing.
        pendingPage = 1
    }
    mutating func prepareRefreshRetry() { pendingPage = 1 }
}

/// A failed read retries its own operation; failed social mutations refresh server truth.
enum FriendsRetryOperation {
    case refresh, search, more
}
