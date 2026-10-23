import Foundation

enum CatalogPagination {
    static func hasMore(page: Int, newCount: Int, incomingCount: Int, explicit: Bool?, totalPages: Int?, pageSize: Int) -> Bool {
        explicit ?? totalPages.map { page < $0 } ?? (newCount > 0 && incomingCount >= pageSize)
    }
}
