#if DEBUG
import SwiftUI

extension Notification.Name {
    static let sozoPlaybackProbe = Notification.Name("SozoPlaybackProbe")
}

/// The production AVKit wrapper playing a local test clip with an isolated fixture account.
/// This view and its launch arguments do not exist in Release builds.
struct PlaybackSmokeView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var item: PlaybackItem?
    @State private var status = "Preparing playback"
    @State private var route = ""
    var body: some View {
        ZStack(alignment: .bottom) {
            if let item { TVPlayerView(item: item, changeSource: { _ in route = "Source picker requested"; self.item = nil }, nextEpisode: { route = "Next episode requested"; self.item = nil }).ignoresSafeArea() }
            if !route.isEmpty { Text(route).accessibilityIdentifier("playback.route") }
            Text(status).font(.system(size: 24)).padding(20).background(.black.opacity(0.7))
                .accessibilityIdentifier("playback.probe").padding(.bottom, 70)
        }
        .onReceive(NotificationCenter.default.publisher(for: .sozoPlaybackProbe)) { event in
            if let value = event.userInfo?["status"] as? String { status = value }
        }
        .task {
            await session.loadProfiles()
            guard let profile = session.profiles.first else { status = "Fixture profile missing"; return }
            do {
                try await session.select(profile)
                let preferences = HouseholdProfileSettings(scope: session.profileScopeIdentity)
                UserDefaults.standard.set(!ProcessInfo.processInfo.arguments.contains("--autoplay-off"), forKey: preferences.key("autoPlay"))
                let path = ProcessInfo.processInfo.environment["SOZO_PLAYBACK_FILE"] ?? ""
                guard FileManager.default.fileExists(atPath: path) else { status = "Fixture clip missing"; return }
                let history = PlaybackHistoryContext(provider: "fixture", contentUrl: "fixture-playback", title: "Playback test", thumbnail: nil, isSerial: false, episodeIndex: nil, episodeNumber: nil, positionMs: 10_000)
                let subtitles = ProcessInfo.processInfo.environment["SOZO_SUBTITLE_FILE"].map { [SubtitleTrack(label: "Fixture English", file: URL(fileURLWithPath: $0).absoluteString)] } ?? []
                item = PlaybackItem(url: URL(fileURLWithPath: path), title: "Playback test", headers: [:], history: history, subtitles: subtitles)
            } catch { status = error.localizedDescription }
        }
    }
}
#endif
