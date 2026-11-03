import SwiftUI

struct DetailView: View {
    @EnvironmentObject private var session: SessionStore
    let card: MediaCard
    let provider: String
    var resume: PlaybackHistoryContext? = nil
    @State private var detail: MediaDetail?
    @State private var episodeList: [Episode] = []
    @State private var selectedSeason: Int?
    private var seasons: [Int] { EpisodeCatalog.seasons(in: episodeList) }
    private var visibleEpisodes: [Episode] { seasons.isEmpty ? episodeList : EpisodeCatalog.episodes(in: episodeList, season: selectedSeason) }
    @State private var movieSources: [VideoSource] = []
    @State private var movieSubtitles: [SubtitleTrack] = []
    @State private var movieHeaders: [String: String] = [:]
    @State private var episodeSerial: Bool?
    @State private var playback: PlaybackItem?
    @State private var reopenSourcePicker = false
    @State private var sourceSwitchPositionMs: Int64?
    @State private var pendingNextEpisode = false
    @State private var error: String?
    @State private var notice: String?
    @State private var favorite: Bool?
    @State private var pendingRetry: (episode: Episode, source: VideoSource?, positionMs: Int64?)?
    @State private var playingSource: VideoSource?
    @State private var primaryFocused = false
    @FocusState private var focus: DetailFocus?
    private enum DetailFocus: Hashable { case primary, episode(Int) }
    @State private var saving = false
    @State private var resolving = false
    @State private var loading = false
    @State private var loadingPage = false
    @State private var page = 0
    @State private var hasMore = false
    @State private var loadComplete = false
    @State private var actionTask: Task<Void, Never>?
    @State private var candidates: [VideoSource] = []
    @State private var candidateSubtitles: [SubtitleTrack] = []
    @State private var candidateHeaders: [String: String] = [:]
    @State private var selectedEpisode: Episode?
    @State private var showingSources = false
    @State private var visible = true
    @State private var hasExternalSubtitles = false
    private var busy: Bool { loading || loadingPage || saving || resolving }
    private var url: String { card.contentUrl ?? card.slug ?? "" }
    private var title: String { detail?.title ?? card.title ?? "Sozo" }
    private var isSerial: Bool { detail?.isSerial ?? episodeSerial ?? (card.type == "series" || card.type == "anime") }
    private var choiceKey: String { HouseholdProfileSettings(scope: session.profileScopeIdentity).key("source.\(provider)") }

    var body: some View {
        ZStack {
            GeometryReader { geometry in
                TVArtwork(url: detail?.banner ?? detail?.thumbnail ?? card.thumbnail, width: geometry.size.width, height: geometry.size.height, cornerRadius: 0)
                    .overlay(.black.opacity(0.76))
            }.ignoresSafeArea().accessibilityHidden(true)
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    Text(title).font(.system(size: 48, weight: .bold)).lineLimit(3)
                    Text(([detail?.year?.text].compactMap { $0 } + (detail?.genres ?? [])).joined(separator: " · ")).font(.system(size: 22)).foregroundStyle(.secondary)
                    Text(detail?.description ?? "").font(.system(size: 24)).foregroundStyle(.secondary).frame(maxWidth: 1100, alignment: .leading)
                    if loading || resolving || saving { SozoLoadingState(title: loading ? "Loading title…" : resolving ? "Finding playable sources…" : "Saving…") }
                    if let notice { Label(notice, systemImage: "checkmark.circle").foregroundStyle(.green) }
                    if let error {
                        Text(error).foregroundStyle(.red).accessibilityIdentifier("detail.error")
                        Button { run { await load() } } label: { SozoActionLabel(title: "Reload title", symbol: "arrow.clockwise") }.buttonStyle(SozoProfileButtonStyle()).disabled(busy)
                    }
                    SozoGlassGroup {
                        HStack(spacing: 24) {
                            Button { run { await toggleFavorite() } } label: {
                                SozoActionLabel(title: favorite == true ? "Remove from favorites" : "Add to favorites", symbol: favorite == true ? "heart.fill" : "heart")
                            }.accessibilityIdentifier("detail.favorite")
                            Button { run { await saveTo("auth/lists/watch-later", notice: "Added to Watch later") } } label: { SozoActionLabel(title: "Watch later", symbol: "bookmark") }
                        }.buttonStyle(SozoProfileButtonStyle()).disabled(busy || url.isEmpty)
                    }
                    if loadComplete {
                        if isSerial {
                            Text("Episodes").font(.system(size: 30, weight: .bold))
                            if !seasons.isEmpty {
                                ScrollView(.horizontal) {
                                    HStack(spacing: 18) {
                                        ForEach(seasons, id: \.self) { season in
                                            Button { selectedSeason = season } label: {
                                                HStack { Text(season == 0 ? "Specials" : "Season \(season)"); if selectedSeason == season { Image(systemName: "checkmark") } }.padding(18)
                                            }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("detail.season.\(season)")
                                        }
                                        if episodeList.contains(where: { EpisodeCatalog.season(of: $0) == nil }) {
                                            Button { selectedSeason = nil } label: { Text("Other episodes").padding(18) }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("detail.season.other")
                                        }
                                    }.padding(.horizontal, 12).padding(.vertical, 18)
                                }.scrollIndicators(.hidden).focusSection()
                                Text("\(visibleEpisodes.count) episodes").font(.system(size: 22)).foregroundStyle(.secondary)
                            }
                            if episodeList.isEmpty { SozoEmptyState(title: "No episodes", message: "No episodes are available from this source.", symbol: "film.stack") }
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 22)], spacing: 24) {
                                ForEach(visibleEpisodes) { episode in
                                    episodeButton(episode, index: episodeList.firstIndex(where: { $0.id == episode.id }) ?? 0, primary: episode.id == primaryEpisodeID)
                                }
                            }
                            if hasMore { Button { run { await loadPage() } } label: { SozoActionLabel(title: loadingPage ? "Loading episodes…" : "Load more episodes", symbol: "plus") }.buttonStyle(SozoProfileButtonStyle()).disabled(busy).accessibilityIdentifier("detail.episodes.more") }
                        } else {
                            episodeButton(Episode(episode: nil, label: resumePosition(for: nil) > 0 ? "Resume film" : "Play film", mediaRef: episodeList.first?.mediaRef ?? url, image: card.thumbnail), index: 0, primary: true)
                        }
                    }
                }.padding(70)
            }
        }
        .task(id: session.profileScopeIdentity) { visible = true; await load() }
        .task(id: session.profileScopeIdentity) { await loadFavorite() }
        .defaultFocus($focus, .primary)
        .onChange(of: loadComplete && !busy) { _, ready in
            // Land on Play or the episodes, not the save row above them.
            guard ready, !primaryFocused else { return }
            primaryFocused = true; focus = .primary
        }
        .onAppear { visible = true }
        .onDisappear { visible = false; actionTask?.cancel() }
        .fullScreenCover(item: $playback, onDismiss: {
            if let retry = pendingRetry {
                pendingRetry = nil
                run { await resolve(retry.episode, retrying: retry.source, positionMs: retry.positionMs) }
            }
            else if reopenSourcePicker { reopenSourcePicker = false; showingSources = true }
            else if pendingNextEpisode {
                pendingNextEpisode = false
                run { await playNextEpisode() }
            }
        }) { item in
            TVPlayerView(item: item, changeSource: { position in
                if let position, position.isFinite, position >= 0, position < Double(Int64.max) / 1000 {
                    sourceSwitchPositionMs = Int64(position * 1000)
                }
                reopenSourcePicker = true; playback = nil
            },
                         nextEpisode: canPlayNextEpisode ? { pendingNextEpisode = true; playback = nil } : nil,
                         retry: retryAction(for: item)).ignoresSafeArea()
        }
        .sheet(isPresented: $showingSources) {
            VStack(alignment: .leading, spacing: 24) {
                Text("Choose source & quality").font(.system(size: 38, weight: .bold))
                Text("Sources are ordered by your quality settings. Known progressive streams above your maximum quality are hidden. Unlabelled streams cannot guarantee a resolution limit.").font(.system(size: 22)).foregroundStyle(.secondary)
                if hasExternalSubtitles { Text("Subtitle tracks are available in the player.").font(.system(size: 22)).foregroundStyle(.secondary) }
                ScrollView {
                    VStack(spacing: 20) {
                        ForEach(Array(candidates.enumerated()), id: \.element.id) { index, source in
                            Button {
                                UserDefaults.standard.set(source.quality, forKey: choiceKey)
                                showingSources = false
                                start(source)
                            } label: {
                                HStack {
                                    Text(source.quality ?? "Source \(index + 1)")
                                    Spacer()
                                    if source.quality == UserDefaults.standard.string(forKey: choiceKey) { Image(systemName: "checkmark.circle.fill") }
                                    Text(source.type?.uppercased() ?? "VIDEO").font(.caption)
                                }.padding(20)
                            }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("detail.source.\(index)")
                        }
                    }.padding(20)
                }
                Button { showingSources = false } label: { SozoActionLabel(title: "Cancel", symbol: "xmark") }.buttonStyle(SozoProfileButtonStyle())
            }.padding(60)
        }
    }
    /// Resolves again rather than replaying the URL, because signed links expire.
    private func retryAction(for item: PlaybackItem) -> ((Double?) -> Void)? {
        guard let episode = selectedEpisode else { return nil }
        let source = playingSource
        return { position in
            var positionMs = item.history?.positionMs
            if let position, position.isFinite, position >= 0, position < Double(Int64.max) / 1000 { positionMs = Int64(position * 1000) }
            pendingRetry = (episode, source, positionMs)
            playback = nil
        }
    }
    private var primaryEpisodeID: Episode.ID? {
        let resumed = EpisodeCatalog.resumedEpisode(in: episodeList, number: resume?.episodeNumber, index: resume?.episodeIndex)
        if let resumed, visibleEpisodes.contains(where: { $0.id == resumed.id }) { return resumed.id }
        return visibleEpisodes.first?.id
    }
    private func episodeButton(_ episode: Episode, index: Int, primary: Bool = false) -> some View {
        Button { run { await resolve(episode) } } label: {
            VStack(alignment: .leading, spacing: 10) {
                Text(episode.label ?? "Episode \(episode.episode ?? index + 1)").font(.system(size: 24, weight: .semibold)).lineLimit(2)
                if isSerial && resumePosition(for: episode) > 0 { Label("Resume", systemImage: "play.circle").font(.caption) }
            }.frame(maxWidth: .infinity, minHeight: 70, alignment: .leading).padding(20)
        }.buttonStyle(SozoProfileButtonStyle()).disabled(busy).focused($focus, equals: primary ? .primary : .episode(index)).accessibilityIdentifier("detail.episode.\(index)")
    }
    private func run(_ work: @escaping () async -> Void) { guard !busy else { return }; actionTask = Task { await work() } }
    private func current(_ scope: String) -> Bool { visible && !Task.isCancelled && session.profileScopeIdentity == scope }
    private func resumePosition(for episode: Episode?) -> Int64 {
        guard let resume else { return 0 }
        if !resume.isSerial { return resume.positionMs }
        guard let episode else { return 0 }
        return EpisodeCatalog.resumedEpisode(in: episodeList, number: resume.episodeNumber, index: resume.episodeIndex)?.id == episode.id ? resume.positionMs : 0
    }
    private func saveTo(_ path: String, notice message: String) async {
        let scope = session.profileScopeIdentity
        saving = true; notice = nil; error = nil
        defer { saving = false }
        do {
            let _: ProfileJSON = try await session.request(path, method: "POST", body: ["provider": provider, "contentUrl": url, "title": title, "thumbnail": detail?.thumbnail ?? card.thumbnail ?? ""])
            if current(scope) { notice = message }
        } catch { if current(scope) { self.error = error.localizedDescription } }
    }
    private func toggleFavorite() async {
        guard favorite == true else {
            await saveTo("auth/favorites", notice: "Added to favorites")
            if notice != nil { favorite = true }
            return
        }
        let scope = session.profileScopeIdentity
        saving = true; notice = nil; error = nil
        defer { saving = false }
        do {
            let _: ProfileJSON = try await session.request("auth/favorites", method: "DELETE", body: ["contentUrl": url])
            if current(scope) { favorite = false; notice = "Removed from favorites" }
        } catch { if current(scope) { self.error = error.localizedDescription } }
    }
    /// Unknown stays nil so a failed lookup still offers Add.
    private func loadFavorite() async {
        let scope = session.profileScopeIdentity
        favorite = nil
        guard !url.isEmpty, let value: ProfileJSON = try? await session.request("auth/favorites") else { return }
        guard current(scope), favorite == nil else { return }
        favorite = value["items"].array.contains { $0["contentUrl"].string == url }
    }
    private func load() async {
        let scope = session.profileScopeIdentity
        loading = true; error = nil; notice = nil; primaryFocused = false; loadComplete = false; episodeList = []; selectedSeason = nil; page = 0; hasMore = false; detail = nil; movieSources = []; movieSubtitles = []; movieHeaders = [:]; episodeSerial = nil; hasExternalSubtitles = false
        defer { loading = false }
        do {
            let value: MediaDetail = try await session.request("contents/detail", query: [.init(name: "provider", value: provider), .init(name: "url", value: url)])
            guard current(scope) else { return }
            detail = value
            await loadPage()
            // A history index is across all pages, not just the first page's seasons.
            // Fetch only until the saved episode is present; leave ordinary browsing paginated.
            while current(scope), hasMore, error == nil,
                  let target = resume?.episodeIndex, target >= episodeList.count, page < 50 {
                await loadPage()
            }
            if current(scope) { loadComplete = page > 0 }
        } catch { if current(scope) { self.error = error.localizedDescription } }
    }
    private func loadPage() async {
        guard !loadingPage else { return }
        let scope = session.profileScopeIdentity, next = page + 1
        loadingPage = true
        defer { loadingPage = false }
        do {
            let value: EpisodePage = try await session.request("contents/episodes", query: [.init(name: "provider", value: provider), .init(name: "url", value: url), .init(name: "page", value: String(next)), .init(name: "size", value: "100")])
            guard current(scope) else { return }
            let incoming = value.episodes ?? []
            var ids = Set(episodeList.map(\.id))
            let fresh = incoming.filter { ids.insert($0.id).inserted }
            episodeList += fresh; page = next
            let resumed = EpisodeCatalog.resumedEpisode(in: episodeList, number: resume?.episodeNumber, index: resume?.episodeIndex)
            if next == 1 { selectedSeason = resumed.flatMap { EpisodeCatalog.season(of: $0) } ?? seasons.first }
            else if let resumed, fresh.contains(where: { $0.id == resumed.id }) {
                selectedSeason = EpisodeCatalog.season(of: resumed)
            }
            hasMore = CatalogPagination.hasMore(page: next, newCount: fresh.count, incomingCount: incoming.count, explicit: value.hasMore, totalPages: value.totalPages, pageSize: 100)
            if next == 1 {
                let movie = value.movieEnvelope
                movieSources = movie.videoSources ?? []; movieSubtitles = movie.subtitles ?? []
                movieHeaders = movie.headers ?? [:]; episodeSerial = value.isSerial
            }
        } catch { if current(scope) { self.error = error.localizedDescription } }
    }
    private func resolve(_ episode: Episode, retrying failed: VideoSource? = nil, positionMs: Int64? = nil) async {
        let scope = session.profileScopeIdentity
        resolving = true; error = nil; sourceSwitchPositionMs = positionMs
        defer { resolving = false }
        do {
            let envelope: MediaEnvelope
            if !isSerial && !movieSources.isEmpty {
                if failed != nil { await refreshMovieSources() }
                envelope = MediaEnvelope(videoUrl: nil, type: nil, headers: movieHeaders, videoSources: movieSources, subtitles: movieSubtitles)
            } else {
                guard let ref = episode.mediaRef, !ref.isEmpty else { throw APIError.noPlayableSource }
                envelope = try await session.request("contents/media", query: [.init(name: "provider", value: provider), .init(name: "ref", value: ref), .init(name: "lang", value: "sub")])
            }
            guard current(scope) else { return }
            let options = envelope.playbackSources
            hasExternalSubtitles = !(envelope.subtitles ?? []).isEmpty
            let preferences = HouseholdProfileSettings(scope: scope)
            let eligible = PlaybackSourcePolicy.constrained(options, maximumHeight: preferences.maximumHeight, dataSaver: preferences.preferredPeakBitRate > 0)
            let playable = PlaybackSourcePolicy.preferred(eligible, quality: UserDefaults.standard.string(forKey: choiceKey))
            guard !playable.isEmpty else { error = "This source did not provide an Apple TV compatible stream. Embedded web pages, DASH and torrents cannot play here. Try another source or adjust your maximum quality setting."; return }
            candidates = playable; candidateHeaders = envelope.headers ?? [:]; candidateSubtitles = envelope.subtitles ?? []; selectedEpisode = episode
            let same = failed.flatMap { failed in
                playable.first { $0.videoUrl == failed.videoUrl } ?? playable.first { $0.quality == failed.quality && $0.type == failed.type }
            }
            if let source = same ?? (playable.count == 1 ? playable[0] : nil) { start(source) } else { showingSources = true }
        } catch { if current(scope) { self.error = error.localizedDescription } }
    }
    /// A film's links come with the first episode page; fetch it again so a retry gets fresh ones.
    private func refreshMovieSources() async {
        let scope = session.profileScopeIdentity
        guard let value: EpisodePage = try? await session.request("contents/episodes", query: [.init(name: "provider", value: provider), .init(name: "url", value: url), .init(name: "page", value: "1"), .init(name: "size", value: "100")]),
              current(scope), let sources = value.movieEnvelope.videoSources, !sources.isEmpty else { return }
        let movie = value.movieEnvelope
        movieSources = sources; movieSubtitles = movie.subtitles ?? []; movieHeaders = movie.headers ?? [:]
    }
    private var canPlayNextEpisode: Bool {
        guard isSerial, let selectedEpisode,
              let index = episodeList.firstIndex(where: { $0.id == selectedEpisode.id }) else { return false }
        return index + 1 < episodeList.count || hasMore
    }
    private func playNextEpisode() async {
        guard let selectedEpisode,
              let index = episodeList.firstIndex(where: { $0.id == selectedEpisode.id }) else { return }
        var attempts = 0
        while index + 1 == episodeList.count && hasMore && error == nil && attempts < 50 {
            attempts += 1
            await loadPage()
            guard !Task.isCancelled else { return }
        }
        guard episodeList.indices.contains(index + 1) else { return }
        let next = episodeList[index + 1]
        selectedSeason = EpisodeCatalog.season(of: next)
        await resolve(next)
    }
    private func start(_ source: VideoSource) {
        guard let stream = PlaybackSourcePolicy.playableURL(source.videoUrl, type: source.type) else { return }
        playingSource = source
        let playbackTitle = isSerial ? title + " · " + (selectedEpisode?.label ?? "Episode") : title
        playback = PlaybackItem(url: stream, title: playbackTitle, headers: PlaybackSourcePolicy.headers(base: candidateHeaders, source: source.headers ?? [:]), history: PlaybackHistoryContext(provider: provider, contentUrl: url, title: title, thumbnail: detail?.thumbnail ?? card.thumbnail, isSerial: isSerial, episodeIndex: selectedEpisode.flatMap { selected in episodeList.firstIndex(where: { $0.id == selected.id }) }, episodeNumber: selectedEpisode?.episode, positionMs: sourceSwitchPositionMs ?? resumePosition(for: selectedEpisode)), subtitles: candidateSubtitles)
    }
}
