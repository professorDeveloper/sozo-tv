import Foundation
precondition(CatalogPagination.hasMore(page: 2, newCount: 0, incomingCount: 2, explicit: true, totalPages: nil, pageSize: 100))
precondition(CatalogPagination.hasMore(page: 2, newCount: 0, incomingCount: 2, explicit: nil, totalPages: 3, pageSize: 100))
precondition(!CatalogPagination.hasMore(page: 3, newCount: 1, incomingCount: 1, explicit: false, totalPages: 3, pageSize: 100))
precondition(!CatalogPagination.hasMore(page: 1, newCount: 0, incomingCount: 100, explicit: nil, totalPages: nil, pageSize: 100))
print("PASS: duplicate pages preserve explicit pagination; unknown repeated pages stop")
