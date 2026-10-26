import SwiftUI

@MainActor
final class HomeModel: ObservableObject {
    @Published var providers: [Provider] = []
    @Published var selected: Provider?
    @Published var payload: HomePayload?
    @Published var continuing: [ContinueWatchingItem] = []
    @Published var loading = false
    @Published var error: String?
    private var generation: UInt64 = 0

    func load(session: SessionStore, refreshSources: Bool = false) async {
        generation &+= 1
        let current = generation
        loading = true; error = nil
        defer { if current == generation { loading = false } }
        do {
            if providers.isEmpty || refreshSources {
                let available: ProviderCatalog = try await session.request("contents/providers")
                try Task.checkCancellation()
                guard current == generation else { return }
                providers = available.items.filter(\.supportsServerBrowsing)
            }
            let preferred = HouseholdProfileSettings(scope: session.profileScopeIdentity).selectedProvider
            let provider = providers.first { $0.id == preferred } ?? providers.first { $0.id == selected?.id } ?? providers.first { $0.id == SourceCatalog.defaultID(providers) }
            guard let provider else { throw APIError.noPlayableSource }
            if selected?.id != provider.id { payload = nil }
            selected = provider
            async let history: ContinueWatchingEnvelope? = try? session.request("auth/history")
            let next: HomePayload = try await session.request("contents/home", query: [.init(name: "provider", value: provider.id)])
            try Task.checkCancellation()
            guard current == generation else { return }
            payload = next
            let progress = await history
            try Task.checkCancellation()
            guard current == generation else { return }
            continuing = (progress?.items ?? []).filter(\.canResume).prefix(16).map { $0 }
        } catch is CancellationError {} catch {
            if current == generation && !Task.isCancelled { self.error = error.localizedDescription }
        }
    }
    func choose(_ provider: Provider, session: SessionStore) async {
        HouseholdProfileSettings(scope: session.profileScopeIdentity).selectedProvider = provider.id
        selected = provider; payload = nil
        await load(session: session)
    }
}


struct HomeView: View {
    @EnvironmentObject private var session: SessionStore
    @StateObject private var model = HomeModel()
    @State private var actionTask: Task<Void, Never>?
    @FocusState private var focusedShelfCard: String?
    @State private var shelfPositions: [String: Int] = [:]
    private var shelves: [HomeSection] { (model.payload?.sections ?? []).filter { !$0.items.isEmpty } }
    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                ScrollViewReader { scroller in
                ScrollView {
                    VStack(alignment: .leading, spacing: 34) {
                        header
                        if let error = model.error { ProfileErrorView(message: error) { refresh() } }
                        if model.loading && model.payload == nil { SozoLoadingState(title: "Loading your home…") }
                        if let banners = model.payload?.banner, !banners.isEmpty {
                            FeaturedCarousel(cards: uniqueCards(banners), provider: model.selected?.id ?? "", width: max(400, geometry.size.width - 140))
                        }
                        if !model.continuing.isEmpty { continueWatching }
                        ForEach(Array(shelves.enumerated()), id: \.element.id) { row, section in
                            if !section.items.isEmpty {
                                VStack(alignment: .leading, spacing: 16) {
                                    Text(section.label ?? "Discover").font(.system(size: 30, weight: .bold))
                                    ScrollView(.horizontal) {
                                        HStack(spacing: 26) {
                                            ForEach(Array(uniqueCards(section.items).enumerated()), id: \.element.id) { column, card in
                                                MediaTile(card: card, provider: model.selected?.id ?? "")
                                                    .id(shelfFocusID(section, card))
                                                    .focused($focusedShelfCard, equals: shelfFocusID(section, card))
                                                    .onChange(of: focusedShelfCard) { value in
                                                        if value == shelfFocusID(section, card) { shelfPositions[section.id] = column }
                                                    }
                                            }
                                        }.padding(.horizontal, 12).padding(.vertical, 18)
                                    }.scrollIndicators(.hidden).focusSection()
                                        .onMoveCommand { direction in
                                            if direction == .down { moveShelf(from: row, to: row + 1, scroller: scroller) }
                                            if direction == .up { moveShelf(from: row, to: row - 1, scroller: scroller) }
                                        }
                                }.id(section.id)
                            }
                        }
                        if !model.loading && model.error == nil && model.payload != nil
                            && model.payload?.banner?.isEmpty != false && model.payload?.sections.allSatisfy({ $0.items.isEmpty }) == true {
                            SozoEmptyState(title: "Nothing here yet", message: "Try another source or refresh to discover available titles.", symbol: "film.stack")
                            Button { refresh() } label: { SozoActionLabel(title: "Refresh", symbol: "arrow.clockwise") }
                                .buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("home.refresh")
                        }
                    }.padding(.horizontal, 70).padding(.top, 30).padding(.bottom, 70)
                }.scrollIndicators(.hidden)
                }
            }.background { SozoBackdrop() }
                .task(id: session.profileScopeIdentity) { shelfPositions = [:]; await model.load(session: session, refreshSources: true) }
                .onReceive(NotificationCenter.default.publisher(for: UserDefaults.didChangeNotification)) { _ in
                    let preferred = HouseholdProfileSettings(scope: session.profileScopeIdentity).selectedProvider
                    if let preferred, preferred != model.selected?.id, !model.loading,
                       let provider = model.providers.first(where: { $0.id == preferred }) { choose(provider) }
                }
                .onAppear {
                    let preferred = HouseholdProfileSettings(scope: session.profileScopeIdentity).selectedProvider
                    if let preferred, preferred != model.selected?.id, !model.loading { refresh() }
                }
                .onChange(of: model.selected?.id) { _ in shelfPositions = [:]; focusedShelfCard = nil }
                .onDisappear { actionTask?.cancel() }
        }
    }
    private func shelfFocusID(_ section: HomeSection, _ card: MediaCard) -> String { section.id + "|" + card.id }
    private func moveShelf(from current: Int, to next: Int, scroller: ScrollViewProxy) {
        guard shelves.indices.contains(next) else { return }
        let section = shelves[next], cards = uniqueCards(section.items)
        guard !cards.isEmpty else { return }
        let column = min(shelfPositions[section.id] ?? shelfPositions[shelves[current].id] ?? 0, cards.count - 1)
        let target = shelfFocusID(section, cards[column])
        focusedShelfCard = target
        // Focus set from code does not scroll anything: the remembered card could be focused
        // while its shelf was below the screen, or scrolled out of its own row.
        withAnimation(.easeOut(duration: 0.25)) {
            scroller.scrollTo(section.id)
            scroller.scrollTo(target)
        }
    }
    private func refresh() { actionTask?.cancel(); actionTask = Task { await model.load(session: session, refreshSources: true) } }
    private func choose(_ provider: Provider) { actionTask?.cancel(); actionTask = Task { await model.choose(provider, session: session) } }
    private func uniqueCards(_ cards: [MediaCard]) -> [MediaCard] { var ids = Set<String>(); return cards.filter { ids.insert($0.id).inserted } }
    private var header: some View {
        HStack(spacing: 28) {
            Text("SOZO").font(.system(size: 36, weight: .black)).foregroundStyle(SozoTheme.accent)
            Spacer()
            SozoGlassGroup {
                HStack(spacing: 22) {
                    NavigationLink { ProfileSourcesView() } label: {
                        SozoActionLabel(title: model.selected?.name ?? "Sources", symbol: "rectangle.stack")
                    }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("home.source")
                    NavigationLink { SearchView(provider: model.selected?.id ?? "") } label: { SozoActionLabel(title: "Search", symbol: "magnifyingglass") }
                        .buttonStyle(SozoProfileButtonStyle()).disabled(model.selected == nil).accessibilityIdentifier("home.search")
                    NavigationLink { ProfileHubView { AnyView(ProfileDestinationView(destination: $0)) } } label: {
                        HStack(spacing: 14) {
                            if let profile = session.activeProfile { ProfileAvatar(profile: profile, size: 42); Text(profile.name).lineLimit(1) }
                            else { Label("Profile", systemImage: "person.crop.circle") }
                        }.font(.system(size: 24, weight: .semibold)).padding(.horizontal, 22).padding(.vertical, 12)
                    }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("home.profile")
                }
            }
        }.focusSection()
    }
    private var continueWatching: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Continue watching").font(.system(size: 30, weight: .bold))
            ScrollView(.horizontal) {
                HStack(spacing: 26) {
                    ForEach(model.continuing) { progress in
                        NavigationLink { DetailView(card: progress.card, provider: progress.provider, resume: progress.context) } label: {
                            VStack(alignment: .leading, spacing: 12) {
                                TVArtwork(url: progress.thumbnail, width: 310, height: 175)
                                Text(progress.title ?? "Untitled").font(.system(size: 23, weight: .semibold)).lineLimit(1)
                                ProgressView(value: Double(progress.positionMs ?? 0) / Double(progress.durationMs ?? 1)).tint(.white)
                                Text(progress.episodeNumber.map { "Episode \($0) · Resume" } ?? "Resume").font(.system(size: 19)).foregroundStyle(.secondary)
                            }.frame(width: 310).padding(12)
                        }.buttonStyle(.card).accessibilityIdentifier("home.resume.\(progress.id)")
                    }
                }.padding(.horizontal, 12).padding(.vertical, 18)
            }.scrollIndicators(.hidden).focusSection()
        }
    }
}

struct MediaTile: View {
    let card: MediaCard
    let provider: String
    var width: CGFloat = 250
    var body: some View {
        NavigationLink { DetailView(card: card, provider: card.provider ?? provider) } label: {
            VStack(alignment: .leading, spacing: 12) {
                TVArtwork(url: card.thumbnail, width: width, height: width * 1.44)
                Text(card.title ?? "Untitled").font(.system(size: 23, weight: .semibold)).lineLimit(2)
                    .frame(width: width, height: 60, alignment: .topLeading)
            }.padding(10)
        }.buttonStyle(.card).accessibilityIdentifier("media.\(card.id)")
    }
}

private struct FeaturedCarousel: View {
    let cards: [MediaCard]
    let provider: String
    let width: CGFloat
    @State private var index = 0
    @FocusState private var focused: Bool
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var identity: String { provider + "|" + cards.map(\.id).joined(separator: "|") }
    var body: some View {
        if !cards.isEmpty {
            VStack(alignment: .trailing, spacing: 12) {
                HeroCard(card: cards[min(index, cards.count - 1)], provider: provider, width: width, focus: $focused)
                if cards.count > 1 {
                    Text("\(min(index, cards.count - 1) + 1) / \(cards.count) · Featured")
                        .font(.system(size: 18)).foregroundStyle(.secondary).accessibilityIdentifier("home.banner.position")
                }
            }
            .task(id: identity) {
                index = 0
                while !Task.isCancelled && cards.count > 1 {
                    do { try await Task.sleep(for: .seconds(8)) } catch { return }
                    guard !Task.isCancelled else { return }
                    if !focused && scenePhase == .active && !reduceMotion { index = (index + 1) % cards.count }
                }
            }
        }
    }
}

private struct HeroCard: View {
    let card: MediaCard
    let provider: String
    let width: CGFloat
    var focus: FocusState<Bool>.Binding
    var body: some View {
        NavigationLink { DetailView(card: card, provider: card.provider ?? provider) } label: {
            ZStack(alignment: .bottomLeading) {
                TVArtwork(url: card.banner ?? card.thumbnail, width: width, height: 420, cornerRadius: 28)
                LinearGradient(colors: [.clear, .black.opacity(0.9)], startPoint: .top, endPoint: .bottom)
                VStack(alignment: .leading, spacing: 12) {
                    Text("FEATURED").font(.system(size: 18, weight: .semibold)).tracking(3).foregroundStyle(.white.opacity(0.75))
                    Text(card.title ?? "Discover").font(.system(size: 44, weight: .bold)).lineLimit(2)
                    Label("View title", systemImage: "play.circle").font(.system(size: 22, weight: .medium))
                }.padding(36)
            }.frame(width: width, height: 420).clipShape(RoundedRectangle(cornerRadius: 28))
        }.buttonStyle(.card).focused(focus).accessibilityIdentifier("home.featured")
    }
}
