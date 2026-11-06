import SwiftUI

struct ProfileSourcesView: View {
    @EnvironmentObject private var session: SessionStore
    @StateObject private var request = ProfileRequestState()
    @State private var sources: [Provider] = []
    @State private var selected = ""
    @State private var checking: String?
    @State private var verified = Set<String>()
    @State private var query = ""
    @State private var category = "all"
    @State private var language = "all"
    @State private var selectionTask: Task<Void, Never>?
    private var available: [Provider] { sources.filter(\.supportsServerBrowsing) }
    private var visibleSources: [Provider] { SourceCatalog.filtered(sources, query: query, category: category, language: language) }
    private var selectedName: String { sources.first { $0.id == selected }.map(SourceCatalog.title) ?? "Choose a source" }
    init() {
        #if DEBUG
        if let seed = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--source-query=") }) {
            _query = State(initialValue: String(seed.dropFirst("--source-query=".count)))
        }
        #endif
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                HStack(spacing: 24) {
                    Image(systemName: "rectangle.stack.fill").font(.system(size: 34))
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Home source · \(session.activeProfile?.name ?? "Current profile")").font(.system(size: 21)).foregroundStyle(.secondary)
                        Text(selectedName).font(.system(size: 34, weight: .semibold)).accessibilityIdentifier("sources.selected")
                    }
                    Spacer()
                    Button { selectionTask?.cancel(); selectionTask = Task { await load() } } label: {
                        SozoActionLabel(title: "Refresh", symbol: "arrow.clockwise")
                    }.buttonStyle(SozoProfileButtonStyle()).disabled(request.busy).accessibilityIdentifier("sources.refresh")
                }.padding(26).sozoContentSurface().focusSection()
                Text("Choose where to browse movies and series. We check the catalog before saving; your other profiles keep their own choices.")
                    .font(.system(size: 23)).foregroundStyle(.secondary)
                SozoGlassGroup {
                    HStack(spacing: 24) {
                        TextField("Search by name, language or content", text: $query).font(.system(size: 25))
                            .accessibilityIdentifier("sources.search").frame(maxWidth: .infinity)
                        filterMenu(title: category == "all" ? "All content" : SourceCatalog.categoryName(category), symbol: "square.grid.2x2", id: "sources.category") {
                            Button("All content") { category = "all" }
                            ForEach(Array(Set(available.map(SourceCatalog.category))).sorted(), id: \.self) { value in
                                Button(SourceCatalog.categoryName(value)) { category = value }
                            }
                        }
                        filterMenu(title: language == "all" ? "All languages" : SourceCatalog.languageName(language), symbol: "globe", id: "sources.language") {
                            Button("All languages") { language = "all" }
                            ForEach(Array(Set(available.map(SourceCatalog.language))).sorted(), id: \.self) { value in
                                Button(SourceCatalog.languageName(value)) { language = value }
                            }
                        }
                    }
                }.focusSection()
                if request.busy { SozoLoadingState(title: checking == nil ? "Loading sources…" : "Checking source catalog…") }
                if let error = request.error {
                    Text(error).font(.system(size: 24)).foregroundStyle(.red).accessibilityIdentifier("sources.error")
                    Text(checking == nil && sources.isEmpty ? "Refresh to try loading again." : "Your previous Home source is unchanged. Choose another source or try again.")
                        .font(.system(size: 21)).foregroundStyle(.secondary)
                }
                if let notice = request.notice { Label(notice, systemImage: "checkmark.circle").font(.system(size: 23)).foregroundStyle(.green) }
                HStack {
                    Text("\(visibleSources.count) of \(available.count) sources").font(.system(size: 23, weight: .semibold)).accessibilityIdentifier("sources.count")
                    Spacer()
                    if !query.isEmpty || category != "all" || language != "all" {
                        Button { query = ""; category = "all"; language = "all" } label: { SozoActionLabel(title: "Clear filters", symbol: "xmark") }
                            .buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("sources.clear")
                    }
                }.focusSection()
                if visibleSources.isEmpty && !request.busy && request.error == nil {
                    SozoEmptyState(title: available.isEmpty ? "No Apple TV sources available" : "No matching sources", message: available.isEmpty ? "Refresh the catalog to try again." : "Clear your filters or search for another name.", symbol: "rectangle.stack")
                }
                SozoGlassGroup {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 610, maximum: 760), spacing: 28)], alignment: .leading, spacing: 28) {
                        ForEach(visibleSources) { source in
                            Button { choose(source) } label: { sourceCard(source) }
                                .buttonStyle(SozoProfileButtonStyle()).disabled(request.busy)
                                .accessibilityIdentifier("sources.\(source.id)")
                                .accessibilityAddTraits(selected == source.id ? [.isSelected] : [])
                        }
                    }
                }.focusSection()
                VStack(alignment: .leading, spacing: 12) {
                    Label("About availability", systemImage: "info.circle").font(.system(size: 24, weight: .semibold))
                    Text("A checked catalog does not guarantee every video. Titles, quality and availability depend on the source. Apple TV plays compatible direct HLS and video streams; embedded web pages are not videos.").font(.system(size: 21)).foregroundStyle(.secondary)
                    let phoneCount = sources.count - available.count
                    if phoneCount > 0 { Text("\(phoneCount) phone-only or browsing-only sources are omitted. Android extensions cannot run on Apple TV.").font(.system(size: 21)).foregroundStyle(.secondary) }
                }.padding(26).sozoContentSurface()
            }.frame(maxWidth: 1540).padding(.horizontal, 70).padding(.vertical, 30).frame(maxWidth: .infinity)
        }.scrollClipDisabled().scrollIndicators(.hidden).background { SozoBackdrop() }.navigationTitle("Sources")
            .task(id: session.profileScopeIdentity) { await load() }
            .onDisappear { selectionTask?.cancel(); request.cancel() }
    }
    private func filterMenu<Content: View>(title: String, symbol: String, id: String, @ViewBuilder content: () -> Content) -> some View {
        Menu(content: content) { SozoActionLabel(title: title, symbol: symbol) }
            .buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier(id)
    }
    private func sourceCard(_ source: Provider) -> some View {
        HStack(alignment: .top, spacing: 22) {
            Text(String(SourceCatalog.title(source).prefix(1)).uppercased()).font(.system(size: 32, weight: .bold))
                .frame(width: 64, height: 64).background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
            VStack(alignment: .leading, spacing: 13) {
                HStack {
                    Text(SourceCatalog.title(source)).font(.system(size: 28, weight: .semibold)).lineLimit(1)
                    Spacer()
                    if selected == source.id { Image(systemName: "checkmark.circle.fill").foregroundStyle(.white).accessibilityLabel("Selected") }
                }
                Text("\(SourceCatalog.categoryName(SourceCatalog.category(source))) · \(SourceCatalog.languageName(SourceCatalog.language(source)))")
                    .font(.system(size: 20)).foregroundStyle(.secondary).lineLimit(2)
                Text(checking == source.id ? "Checking…" : verified.contains(source.id) ? "Catalog checked · Select to check again" : selected == source.id ? "Current Home source · Select to check" : "Select to check and use on Home")
                    .font(.system(size: 20)).foregroundStyle(.secondary).lineLimit(2)
            }
        }.padding(24).frame(maxWidth: .infinity, minHeight: 160, alignment: .topLeading)
    }
    private func load() async {
        checking = nil; request.notice = nil; verified = []
        let scope = session.profileScopeIdentity
        await request.run {
            let catalog: ProviderCatalog = try await session.request("contents/providers")
            return SourceCatalog.unique(catalog.items)
        } apply: { values in
            guard scope == session.profileScopeIdentity else { return }
            sources = values
            selected = SourceCatalog.selectedID(values, preferred: HouseholdProfileSettings(scope: scope).selectedProvider) ?? ""
        }
    }
    private func choose(_ source: Provider) {
        guard !request.busy, source.supportsServerBrowsing else { return }
        let scope = session.profileScopeIdentity
        checking = source.id; request.notice = nil
        selectionTask?.cancel()
        selectionTask = Task {
            await request.run {
                let home: HomePayload = try await session.request("contents/home", query: [.init(name: "provider", value: source.id)])
                return home
            } apply: { _ in
                guard scope == session.profileScopeIdentity else { return }
                HouseholdProfileSettings(scope: scope).selectedProvider = source.id
                selected = source.id; verified.insert(source.id)
                request.notice = "Home source updated"
            }
            checking = nil
        }
    }
}
