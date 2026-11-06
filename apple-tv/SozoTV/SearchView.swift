import SwiftUI

struct CatalogPage: Decodable {
    let items: [MediaCard]
    let hasMore: Bool?
    let totalPages: Int?
}

@MainActor final class SearchModel: ObservableObject {
    @Published var items: [MediaCard] = []
    @Published var loading = false
    @Published var error: String?
    @Published var page = 0
    @Published var hasMore = false
    @Published var completed = false
    private var generation: UInt64 = 0
    private var term = ""

    func reset() { generation &+= 1; items = []; page = 0; hasMore = false; completed = false; error = nil; loading = false }
    func search(session: SessionStore, provider: String, query: String, more: Bool = false) async {
        let requested = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if !more { reset(); term = requested }
        guard requested.count >= 2, !provider.isEmpty, !loading, !more || (requested == term && hasMore) else { return }
        generation &+= 1
        let current = generation, next = more ? page + 1 : 1
        loading = true; error = nil
        defer { if current == generation { loading = false } }
        do {
            let result: CatalogPage = try await session.request("contents/search", query: [
                .init(name: "provider", value: provider), .init(name: "q", value: requested), .init(name: "page", value: String(next))
            ])
            try Task.checkCancellation()
            guard current == generation, requested == term else { return }
            var ids = Set(items.map(\.id))
            let fresh = result.items.filter { ids.insert($0.id).inserted }
            items += fresh; page = next; completed = true
            hasMore = CatalogPagination.hasMore(page: next, newCount: fresh.count, incomingCount: result.items.count, explicit: result.hasMore, totalPages: result.totalPages, pageSize: 1)
        } catch is CancellationError {} catch {
            if current == generation && !Task.isCancelled { self.error = error.localizedDescription }
        }
    }
}

struct SearchView: View {
    @EnvironmentObject private var session: SessionStore
    let provider: String
    @State private var query = ""
    @State private var retry = 0
    @StateObject private var model = SearchModel()
    @State private var moreTask: Task<Void, Never>?
    private var term: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }
    init(provider: String) {
        self.provider = provider
        #if DEBUG
        if let seed = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--search-query=") }) {
            _query = State(initialValue: String(seed.dropFirst("--search-query=".count)))
        }
        #endif
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 30) {
                TextField("Movie or series title", text: $query).font(.system(size: 28))
                    .accessibilityIdentifier("search.query").onSubmit { retry += 1 }
                Text("Search this source by title. Enter at least 2 characters.").font(.system(size: 21)).foregroundStyle(.secondary)
                if let error = model.error {
                    ProfileErrorView(message: error) {
                        if model.page > 0 { moreTask?.cancel(); moreTask = Task { await model.search(session: session, provider: provider, query: term, more: true) } }
                        else { retry += 1 }
                    }
                }
                if model.loading && model.items.isEmpty { SozoLoadingState(title: "Searching…") }
                if term.count < 2 {
                    SozoEmptyState(title: "Find your next watch", message: "Use the remote keyboard or your iPhone to enter a movie or series title.", symbol: "magnifyingglass")
                } else if model.completed && model.items.isEmpty && model.error == nil {
                    SozoEmptyState(title: "No matching titles", message: "Try a different title or switch your source from Home.", symbol: "magnifyingglass")
                        .accessibilityIdentifier("search.empty")
                }
                if !model.items.isEmpty {
                    Text("\(model.items.count) titles").font(.system(size: 24, weight: .semibold)).accessibilityIdentifier("search.count")
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 260, maximum: 300), spacing: 30)], alignment: .leading, spacing: 32) {
                        ForEach(model.items) { card in MediaTile(card: card, provider: provider) }
                    }.focusSection()
                    if model.hasMore {
                        HStack {
                            Spacer()
                            Button {
                            moreTask?.cancel()
                            moreTask = Task { await model.search(session: session, provider: provider, query: term, more: true) }
                        } label: { SozoActionLabel(title: model.loading ? "Loading more…" : "Load more titles", symbol: "plus") }
                            .buttonStyle(SozoProfileButtonStyle()).disabled(model.loading).accessibilityIdentifier("search.more")
                            Spacer()
                        }.focusSection()
                    } else if !model.loading { Text("You’ve reached the end").font(.system(size: 21)).foregroundStyle(.secondary) }
                }
            }.padding(.horizontal, 70).padding(.vertical, 36)
        }.scrollClipDisabled().scrollIndicators(.hidden)
            .background { SozoBackdrop() }.navigationTitle("Search")
            .task(id: "\(session.profileScopeIdentity)|\(term)|\(retry)") {
                moreTask?.cancel(); model.reset()
                guard term.count >= 2 else { return }
                do { try await Task.sleep(for: .milliseconds(400)); await model.search(session: session, provider: provider, query: term) }
                catch is CancellationError {} catch {}
            }
            .onDisappear { moreTask?.cancel(); model.reset() }
    }
}
