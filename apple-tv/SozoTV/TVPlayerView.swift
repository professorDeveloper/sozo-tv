import AVKit
import SwiftUI

struct PlaybackItem: Identifiable {
    let id = UUID()
    let url: URL
    let title: String
    let headers: [String: String]
    var history: PlaybackHistoryContext? = nil
    var subtitles: [SubtitleTrack] = []
}

struct TVPlayerView: UIViewControllerRepresentable {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    let item: PlaybackItem
    var changeSource: ((Double?) -> Void)? = nil
    var nextEpisode: (() -> Void)? = nil

    func makeCoordinator() -> Coordinator { Coordinator(session: session, history: item.history, close: { dismiss() }, changeSource: changeSource, nextEpisode: nextEpisode, item: item) }

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let asset = AVURLAsset(url: item.url, options: ["AVURLAssetHTTPHeaderFieldsKey": item.headers])
        let playerItem = AVPlayerItem(asset: asset)
        let title = AVMutableMetadataItem()
        title.identifier = .commonIdentifierTitle
        title.value = item.title as NSString
        title.extendedLanguageTag = "und"
        playerItem.externalMetadata = [title]
        let settings = HouseholdProfileSettings(scope: session.profileScopeIdentity)
        if let height = settings.maximumHeight {
            playerItem.preferredMaximumResolution = CGSize(width: Double(height) * 16 / 9, height: Double(height))
        }
        playerItem.preferredPeakBitRate = settings.preferredPeakBitRate
        let controller = AVPlayerViewController()
        controller.showsPlaybackControls = true
        controller.playbackControlsIncludeTransportBar = true
        controller.playbackControlsIncludeInfoViews = true
        controller.transportBarIncludesTitleView = true
        controller.requiresLinearPlayback = false
        controller.isSkipForwardEnabled = true
        controller.isSkipBackwardEnabled = true
        controller.view.accessibilityIdentifier = "player.surface"
        controller.delegate = context.coordinator
        controller.player = AVPlayer(playerItem: playerItem)
        context.coordinator.attach(controller.player!, controller: controller, autoPlay: settings.autoPlay)
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {}

    static func dismantleUIViewController(_ controller: AVPlayerViewController, coordinator: Coordinator) {
        coordinator.finish()
        controller.player?.pause()
        controller.player = nil
    }

    @MainActor
    final class Coordinator: NSObject, AVPlayerViewControllerDelegate {
        private let session: SessionStore
        private let scope: String
        private let revision: UInt64
        private let history: PlaybackHistoryContext?
        private let close: () -> Void
        private let changeSource: ((Double?) -> Void)?
        private let nextEpisode: (() -> Void)?
        private let item: PlaybackItem
        #if DEBUG
        private var positionProbe: UILabel?
        #endif
        private var subtitleLabel: UILabel?
        private var subtitleBottom: NSLayoutConstraint?
        private var subtitleTask: Task<Void, Never>?
        private var subtitleObserver: Any?
        private var cues: [SubtitleCue] = []
        private var selectedSubtitle: String?
        private var legibleGroup: AVMediaSelectionGroup?
        private var previousLegibleOption: AVMediaSelectionOption?
        private var nextPromptPresented = false
        private var hintTask: Task<Void, Never>?
        private weak var controller: AVPlayerViewController?
        private var lifecycleObservers: [NSObjectProtocol] = []
        private var startupTimeout: Task<Void, Never>?
        private var failurePresented = false
        private var player: AVPlayer?
        private var observer: Any?
        private var statusObservation: NSKeyValueObservation?
        private var lastPosition: Double?
        private var lastTick = ProcessInfo.processInfo.systemUptime
        private var playedSeconds = 0.0
        private var sinceSave = 0.0
        private var didResume = false
        private var startupState = PlaybackStartupState(autoPlay: false)
        private var interruptedByBackground = false
        private var pending: [String: Any]?
        private var saving = false

        init(session: SessionStore, history: PlaybackHistoryContext?, close: @escaping () -> Void, changeSource: ((Double?) -> Void)?, nextEpisode: (() -> Void)?, item: PlaybackItem) {
            self.close = close; self.changeSource = changeSource
            self.nextEpisode = nextEpisode; self.item = item
            self.session = session; self.history = history
            scope = session.profileScopeIdentity; revision = session.revision
            super.init()
        }
        private var sameScope: Bool { session.profileScopeIdentity == scope && session.revision == revision }
        func attach(_ player: AVPlayer, controller: AVPlayerViewController, autoPlay: Bool) {
            self.player = player; self.controller = controller
            startupState = PlaybackStartupState(autoPlay: autoPlay)
            if UIApplication.shared.applicationState == .background { startupState.enteredBackground() }
            var actions: [UIMenuElement] = [
                UIAction(title: "Back 10 seconds", image: UIImage(systemName: "gobackward.10")) { [weak self] _ in self?.seek(by: -10) },
                UIAction(title: "Forward 10 seconds", image: UIImage(systemName: "goforward.10")) { [weak self] _ in self?.seek(by: 10) }
            ]
            if changeSource != nil {
                actions.append(UIAction(title: "Source & quality", image: UIImage(systemName: "rectangle.stack")) { [weak self] _ in
                    guard let self, self.sameScope else { return }
                    let position = self.sourceSwitchPosition
                    self.enqueueSnapshot(); self.player?.pause(); self.changeSource?(position)
                })
            }
            if nextEpisode != nil {
                actions.append(UIAction(title: "Next episode", image: UIImage(systemName: "forward.end")) { [weak self] _ in
                    guard let self, self.sameScope else { return }
                    self.enqueueSnapshot(); self.player?.pause(); self.nextEpisode?()
                })
            }
            var menus: [UIMenuElement] = [UIMenu(title: "Playback options", image: UIImage(systemName: "slider.horizontal.3"), children: actions)]
            let subtitleTracks = item.subtitles.filter { track in
                guard !track.file.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                      let url = URL(string: track.file, relativeTo: item.url)?.absoluteURL else { return false }
                #if DEBUG
                if url.isFileURL && ProcessInfo.processInfo.arguments.contains("--player-smoke") { return true }
                #endif
                return SidecarSubtitles.isRemoteURL(url)
            }
            if !subtitleTracks.isEmpty {
                var tracks: [UIMenuElement] = [UIAction(title: "External subtitles off") { [weak self] _ in self?.selectSubtitle(nil) }]
                var files = Set<String>()
                for track in subtitleTracks where files.insert(track.file).inserted {
                    tracks.append(UIAction(title: track.label ?? "Subtitle", image: UIImage(systemName: "captions.bubble")) { [weak self] _ in self?.selectSubtitle(track) })
                }
                menus.append(UIMenu(title: "External subtitles", image: UIImage(systemName: "captions.bubble"), children: tracks))
            }
            controller.transportBarCustomMenuItems = menus
            if let overlay = controller.contentOverlayView {
                let captions = UILabel()
                captions.numberOfLines = 4; captions.textAlignment = .center
                captions.font = .systemFont(ofSize: 32, weight: .semibold)
                captions.textColor = .white; captions.backgroundColor = UIColor.black.withAlphaComponent(0.72)
                captions.layer.cornerRadius = 12; captions.clipsToBounds = true
                captions.isHidden = true; captions.accessibilityIdentifier = "player.sidecar.text"
                captions.translatesAutoresizingMaskIntoConstraints = false
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("--native-detail-playback") {
                    let probe = UILabel()
                    probe.accessibilityIdentifier = "player.position"
                    probe.text = "0"
                    probe.textColor = .white
                    probe.font = .systemFont(ofSize: 18)
                    probe.translatesAutoresizingMaskIntoConstraints = false
                    overlay.addSubview(probe)
                    NSLayoutConstraint.activate([probe.trailingAnchor.constraint(equalTo: overlay.trailingAnchor, constant: -84), probe.topAnchor.constraint(equalTo: overlay.topAnchor, constant: 50)])
                    positionProbe = probe
                }
                #endif
                overlay.addSubview(captions)
                let bottom = captions.bottomAnchor.constraint(equalTo: overlay.bottomAnchor, constant: -160)
                subtitleBottom = bottom
                NSLayoutConstraint.activate([captions.centerXAnchor.constraint(equalTo: overlay.centerXAnchor), bottom, captions.widthAnchor.constraint(lessThanOrEqualTo: overlay.widthAnchor, multiplier: 0.8)])
                subtitleLabel = captions
                let hint = UILabel()
                hint.text = "Select: playback controls  ·  Play/Pause: pause or resume"
                hint.font = .systemFont(ofSize: 22, weight: .medium)
                hint.textColor = .white
                hint.backgroundColor = UIColor.black.withAlphaComponent(0.65)
                hint.layer.cornerRadius = 12; hint.clipsToBounds = true
                hint.translatesAutoresizingMaskIntoConstraints = false
                overlay.addSubview(hint)
                NSLayoutConstraint.activate([hint.leadingAnchor.constraint(equalTo: overlay.leadingAnchor, constant: 84), hint.topAnchor.constraint(equalTo: overlay.topAnchor, constant: 50), hint.heightAnchor.constraint(equalToConstant: 48)])
                hintTask = Task { [weak hint] in
                    try? await Task.sleep(for: .seconds(7))
                    guard !Task.isCancelled else { return }
                    hint?.removeFromSuperview()
                }
            }
            startupTimeout = Task { [weak self] in
                try? await Task.sleep(for: .seconds(35))
                guard !Task.isCancelled, let self, !self.didResume else { return }
                self.showFailure("The stream did not become ready. Return and choose another source.")
            }
            lifecycleObservers.append(NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    self?.interruptedByBackground = true
                    self?.startupState.enteredBackground()
                    self?.enqueueSnapshot(); self?.player?.pause()
                }
            })
            lifecycleObservers.append(NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.sameScope else { return }
                    // AVKit can restore its pre-background rate before this notification.
                    if self.interruptedByBackground {
                        self.interruptedByBackground = false
                        self.player?.pause()
                    }
                    self.applyStartupAction(self.startupState.becameActive())
                }
            })
            lifecycleObservers.append(NotificationCenter.default.addObserver(forName: AVPlayerItem.mediaSelectionDidChangeNotification, object: player.currentItem, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self, self.selectedSubtitle != nil, let group = self.legibleGroup,
                          self.player?.currentItem?.currentMediaSelection.selectedMediaOption(in: group) != nil else { return }
                    // A native subtitle choice takes precedence over the external caption overlay.
                    self.subtitleTask?.cancel(); self.cues = []; self.selectedSubtitle = nil
                    self.legibleGroup = nil; self.previousLegibleOption = nil
                    self.subtitleLabel?.text = nil; self.subtitleLabel?.isHidden = true
                }
            })
            lifecycleObservers.append(NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: player.currentItem, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    self.enqueueSnapshot()
                    self.offerNextEpisode()
                }
            })
            statusObservation = player.currentItem?.observe(\.status, options: [.initial, .new]) { [weak self] _, _ in
                Task { @MainActor [weak self] in self?.ready() }
            }
            observer = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 1, preferredTimescale: 600), queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in self?.tick() }
            }
        }
        func playerViewController(_ playerViewController: AVPlayerViewController, willTransitionToVisibilityOfTransportBar visible: Bool, with coordinator: AVPlayerViewControllerAnimationCoordinator) {
            subtitleBottom?.constant = visible ? -310 : -160
            coordinator.addCoordinatedAnimations({ [weak self] in
                self?.subtitleLabel?.superview?.layoutIfNeeded()
            }, completion: nil)
        }
        private func selectSubtitle(_ track: SubtitleTrack?) {
            subtitleTask?.cancel(); cues = []; selectedSubtitle = track?.file
            if let subtitleObserver { player?.removeTimeObserver(subtitleObserver) }
            subtitleObserver = nil
            subtitleLabel?.text = nil; subtitleLabel?.isHidden = true
            guard let track else {
                if let legibleGroup { player?.currentItem?.select(previousLegibleOption, in: legibleGroup) }
                legibleGroup = nil; previousLegibleOption = nil
                return
            }
            subtitleLabel?.text = "Loading subtitles…"; subtitleLabel?.isHidden = false
            subtitleTask = Task { [weak self] in
                guard let self else { return }
                do {
                    guard let url = URL(string: track.file, relativeTo: item.url)?.absoluteURL else { throw URLError(.badURL) }
                    let data: Data
                    #if DEBUG
                    if url.isFileURL && ProcessInfo.processInfo.arguments.contains("--player-smoke") {
                        data = try Data(contentsOf: url)
                    } else {
                        data = try await subtitleData(url)
                    }
                    #else
                    data = try await subtitleData(url)
                    #endif
                    let parsed = try await Task.detached(priority: .userInitiated) { try SidecarSubtitles.parse(data) }.value
                    guard !Task.isCancelled, sameScope, selectedSubtitle == track.file else { return }
                    let group = try? await player?.currentItem?.asset.loadMediaSelectionGroup(for: .legible)
                    guard !Task.isCancelled, sameScope, selectedSubtitle == track.file else { return }
                    cues = parsed
                    if let group {
                        if legibleGroup == nil { previousLegibleOption = player?.currentItem?.currentMediaSelection.selectedMediaOption(in: group) }
                        legibleGroup = group
                        player?.currentItem?.select(nil, in: group)
                    }
                    updateSubtitles()
                    if subtitleObserver == nil, let player {
                        subtitleObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.1, preferredTimescale: 600), queue: .main) { [weak self] _ in
                            Task { @MainActor [weak self] in self?.updateSubtitles() }
                        }
                    }
                } catch {
                    guard !Task.isCancelled, sameScope, selectedSubtitle == track.file else { return }
                    // A failed replacement must not leave the previously selected native track off.
                    selectSubtitle(nil)
                    let alert = UIAlertController(title: "Subtitles unavailable", message: "This subtitle file could not be loaded. Try another subtitle track.", preferredStyle: .alert)
                    alert.addAction(UIAlertAction(title: "OK", style: .default))
                    controller?.present(alert, animated: true)
                }
            }
        }
        private func subtitleData(_ url: URL) async throws -> Data {
            guard SidecarSubtitles.isRemoteURL(url) else { throw URLError(.badURL) }
            var request = URLRequest(url: url, timeoutInterval: 15)
            for (name, value) in item.headers {
                if ["referer", "origin", "user-agent", "accept-language"].contains(name.lowercased()) {
                    request.setValue(value, forHTTPHeaderField: name)
                }
            }
            let config = URLSessionConfiguration.ephemeral
            config.httpShouldSetCookies = false
            let transport = URLSession(configuration: config)
            defer { transport.invalidateAndCancel() }
            let (bytes, response) = try await transport.bytes(for: request)
            guard let response = response as? HTTPURLResponse, (200..<300).contains(response.statusCode), response.expectedContentLength <= SidecarSubtitles.maximumBytes else { throw URLError(.badServerResponse) }
            var data = Data()
            for try await byte in bytes {
                try Task.checkCancellation()
                guard data.count < SidecarSubtitles.maximumBytes else { throw URLError(.dataLengthExceedsMaximum) }
                data.append(byte)
            }
            return data
        }
        private func updateSubtitles() {
            guard sameScope, let player else { subtitleLabel?.isHidden = true; return }
            let text = SidecarSubtitles.text(at: player.currentTime().seconds, cues: cues)
            if subtitleLabel?.text != text { subtitleLabel?.text = text }
            subtitleLabel?.isHidden = text == nil
        }
        private func offerNextEpisode() {
            guard sameScope, !nextPromptPresented, let nextEpisode, let controller else { return }
            nextPromptPresented = true
            let alert = UIAlertController(title: "Episode finished", message: "Continue with the next episode?", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Next episode", style: .default) { [weak self] _ in
                guard let self, self.sameScope else { return }
                nextEpisode()
            })
            alert.addAction(UIAlertAction(title: "Back to episodes", style: .cancel) { [weak self] _ in self?.close() })
            controller.present(alert, animated: true)
        }
        private var sourceSwitchPosition: Double? {
            guard let player, didResume || player.currentItem?.status == .readyToPlay else { return nil }
            let position = player.currentTime().seconds
            return position.isFinite && position >= 0 ? position : nil
        }
        private func seek(by seconds: Double) {
            guard let player, let item = player.currentItem, item.status == .readyToPlay,
                  item.duration.seconds.isFinite, item.duration.seconds > 0 else { return }
            let position = min(max(0, player.currentTime().seconds + seconds), max(0, item.duration.seconds - 0.1))
            player.seek(to: CMTime(seconds: position, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        }
        private func ready() {
            #if DEBUG
            defer { reportProbe() }
            #endif
            guard sameScope else { player?.pause(); close(); return }
            if player?.currentItem?.status == .failed {
                showFailure(player?.currentItem?.error?.localizedDescription ?? "This stream cannot be played. Choose another source.")
                return
            }
            guard !didResume, let player, player.currentItem?.status == .readyToPlay else { return }
            startupTimeout?.cancel()
            didResume = true
            if let history, history.positionMs > 0 {
                let duration = player.currentItem?.duration.seconds ?? 0
                let position = Double(history.positionMs) / 1000
                if duration.isFinite && position < duration - 1 {
                    guard startupState.becameReady(needsResume: true) == .seek else { return }
                    player.seek(to: CMTime(seconds: position, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] succeeded in
                        Task { @MainActor [weak self] in
                            guard let self, self.sameScope, let player = self.player else { return }
                            self.lastPosition = player.currentTime().seconds
                            self.applyStartupAction(self.startupState.resumeCompleted(success: succeeded))
                        }
                    }
                    return
                }
            }
            applyStartupAction(startupState.becameReady(needsResume: false))
        }
        private func applyStartupAction(_ action: PlaybackStartupState.Action) {
            // Defer initial Play during transient inactivity; a background event cancels it.
            // Checking live state also covers notification/seek-completion delivery races.
            let activity: PlaybackStartupState.Activity
            switch UIApplication.shared.applicationState {
            case .active: activity = .active
            case .background: activity = .background
            default: activity = .inactive
            }
            if startupState.apply(action, activity: activity) == .play { player?.play() }
        }
        private func tick() {
            #if DEBUG
            defer { reportProbe() }
            #endif
            guard sameScope else { player?.pause(); close(); return }
            guard let player, player.currentItem?.status == .readyToPlay else { return }
            let now = ProcessInfo.processInfo.systemUptime
            let position = player.currentTime().seconds
            defer { lastPosition = position; lastTick = now }
            guard position.isFinite, let previous = lastPosition else { return }
            let delta = position - previous
            // Seeking, buffering and paused time never count as playback.
            if player.rate > 0 && delta > 0 && delta < 3 {
                let elapsed = min(delta, max(0, now - lastTick))
                playedSeconds += elapsed; sinceSave += elapsed
            }
            if sinceSave >= 30 { enqueueSnapshot(); sinceSave = 0 }
        }
        private func enqueueSnapshot() {
            guard sameScope, playedSeconds >= 1, let player,
                  player.currentItem?.status == .readyToPlay,
                  let body = history?.payload(positionSeconds: player.currentTime().seconds,
                                              durationSeconds: player.currentItem?.duration.seconds ?? 0) else { return }
            pending = body
            guard !saving else { return }
            saving = true
            Task { [self] in
                defer { saving = false }
                while let body = pending, sameScope {
                    pending = nil
                    do {
                        let _: MessageResponse = try await session.request("auth/history", method: "POST", body: body)
                    } catch {
                        // Retain newest progress for the next periodic save; never retry into another scope.
                        if pending == nil, sameScope { pending = body }
                        break
                    }
                }
            }
        }
        #if DEBUG
        private func reportProbe() {
            if let position = player?.currentTime().seconds, position.isFinite {
                positionProbe?.text = String(Int(position))
            }
            guard ProcessInfo.processInfo.arguments.contains("--player-smoke"),
                  let player, player.currentItem?.status == .readyToPlay else { return }
            let position = player.currentTime().seconds
            guard position.isFinite else { return }
            NotificationCenter.default.post(name: .sozoPlaybackProbe, object: nil,
                userInfo: ["status": "\(player.rate > 0 ? "Playing" : "Paused") • \(Int(position))s"])
        }
        #endif
        private func showFailure(_ message: String) {
            guard !failurePresented, sameScope, let controller else { return }
            failurePresented = true; player?.pause(); startupTimeout?.cancel()
            let alert = UIAlertController(title: "Playback unavailable", message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Choose another source", style: .default) { [weak self] _ in if let self { if let changeSource = self.changeSource { changeSource(self.sourceSwitchPosition) } else { self.close() } } })
            controller.present(alert, animated: true)
        }
        func finish() {
            startupState.finish()
            tick(); enqueueSnapshot()
            if let observer { player?.removeTimeObserver(observer) }
            if let subtitleObserver { player?.removeTimeObserver(subtitleObserver) }
            #if DEBUG
            positionProbe?.removeFromSuperview(); positionProbe = nil
            #endif
            subtitleObserver = nil; subtitleTask?.cancel(); subtitleTask = nil
            subtitleLabel?.removeFromSuperview(); subtitleLabel = nil; subtitleBottom = nil; cues = []
            observer = nil; statusObservation = nil; player = nil
            startupTimeout?.cancel(); startupTimeout = nil
            hintTask?.cancel(); hintTask = nil
            lifecycleObservers.forEach { NotificationCenter.default.removeObserver($0) }
            lifecycleObservers = []; controller = nil
        }
    }
}
