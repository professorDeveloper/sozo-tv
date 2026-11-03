import Foundation

enum PlaybackSourcePolicy {
    static func playableURL(_ value: String, type: String?) -> URL? {
        guard let url = URL(string: value), ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
              url.host != nil, url.user == nil, url.password == nil else { return nil }
        let kind = (type ?? "").lowercased()
        if ["iframe", "embed", "html", "dash", "mpd", "torrent"].contains(kind) { return nil }
        let path = url.path.lowercased()
        if path.hasSuffix(".mpd") || path.hasSuffix(".html") || path.hasSuffix(".htm") || path.contains("/embed/") || path.hasPrefix("/e/") { return nil }
        // Unknown extension-less URLs are not assumed to be media documents.
        guard ["hls", "m3u8", "mp4", "progressive", "video"].contains(kind)
                || ["m3u8", "mp4", "m4v", "mov"].contains(url.pathExtension.lowercased()) else { return nil }
        return url
    }
    static func headers(base: [String: String], source: [String: String]) -> [String: String] {
        var values = base
        for (key, value) in source {
            for old in values.keys.filter({ $0.caseInsensitiveCompare(key) == .orderedSame }) { values.removeValue(forKey: old) }
            values[key] = value
        }
        return values.filter { !$0.key.contains("\r") && !$0.key.contains("\n") && !$0.value.contains("\r") && !$0.value.contains("\n") }
    }
    static func height(_ source: VideoSource) -> Int? {
        let label = (source.quality ?? "").lowercased()
        if label.contains("4k") { return 2160 }
        guard let match = label.range(of: #"(?<![0-9])(2160|1440|1080|720|576|480|360|240)(?:p|\b)"#, options: .regularExpression) else { return nil }
        return Int(label[match].filter(\.isNumber))
    }
    static func constrained(_ sources: [VideoSource], maximumHeight: Int?, dataSaver: Bool) -> [VideoSource] {
        let eligible = unique(sources).filter { source in
            let adaptive = ["hls", "m3u8"].contains((source.type ?? "").lowercased()) || URL(string: source.videoUrl)?.pathExtension.lowercased() == "m3u8"
            guard !adaptive, let cap = maximumHeight, let actual = height(source) else { return true }
            return actual <= cap
        }
        return eligible.sorted { left, right in
            let a = height(left), b = height(right)
            if dataSaver { return (a ?? Int.max) < (b ?? Int.max) }
            if a != b { return (a ?? 0) > (b ?? 0) }
            return left.isDefault == true && right.isDefault != true
        }
    }
    static func preferred(_ sources: [VideoSource], quality: String?) -> [VideoSource] {
        guard let quality, !quality.isEmpty else { return sources }
        return sources.filter { $0.quality == quality } + sources.filter { $0.quality != quality }
    }
    static func unique(_ sources: [VideoSource]) -> [VideoSource] {
        var seen = Set<String>()
        return sources.filter { playableURL($0.videoUrl, type: $0.type) != nil && seen.insert($0.videoUrl).inserted }
    }
}

struct EpisodePage: Decodable {
    let episodes: [Episode]?
    let videoSources: [VideoSource]?
    let subtitles: [SubtitleTrack]?
    let hasMore: Bool?
    let totalPages: Int?
    let playerSrc: String?
    let type: String?
    let headers: [String: String]?
    let isSerial: Bool?

    var movieEnvelope: MediaEnvelope {
        var sources = videoSources ?? []
        if let playerSrc, !playerSrc.isEmpty, !sources.contains(where: { $0.videoUrl == playerSrc }) {
            sources.append(VideoSource(quality: "Auto", videoUrl: playerSrc, type: type, isDefault: sources.isEmpty, headers: headers))
        }
        // Individual source headers override the inherited response headers at playback time.
        sources = sources.map { source in
            VideoSource(quality: source.quality, videoUrl: source.videoUrl, type: source.type ?? type,
                        isDefault: source.isDefault, headers: source.headers)
        }
        return MediaEnvelope(videoUrl: nil, type: type, headers: headers, videoSources: sources, subtitles: subtitles)
    }
    enum CodingKeys: String, CodingKey { case episodes, videoSources, subtitles, hasMore, totalPages, playerSrc, type, headers, isSerial }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        episodes = try c.decodeIfPresent([EpisodeWire].self, forKey: .episodes)?.map(\.value)
        videoSources = try c.decodeIfPresent([VideoSource].self, forKey: .videoSources)
        subtitles = try c.decodeIfPresent([SubtitleTrack].self, forKey: .subtitles)
        hasMore = try c.decodeIfPresent(Bool.self, forKey: .hasMore)
        totalPages = try c.decodeIfPresent(Int.self, forKey: .totalPages)
        playerSrc = try c.decodeIfPresent(String.self, forKey: .playerSrc)
        type = try c.decodeIfPresent(String.self, forKey: .type)
        headers = try c.decodeIfPresent([String: String].self, forKey: .headers)
        isSerial = try c.decodeIfPresent(Bool.self, forKey: .isSerial)
    }
}

private struct EpisodeWire: Decodable {
    let value: Episode
    enum CodingKeys: String, CodingKey { case episode, label, mediaRef, image, season, seasonNumber }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let numeric = (try? c.decode(Double.self, forKey: .episode))
            ?? (try? c.decode(String.self, forKey: .episode)).flatMap(Double.init)
        // Do not truncate a special episode (12.5) into episode12's history identity.
        let integral = numeric.flatMap { Int(exactly: $0) }
        let label = try c.decodeIfPresent(String.self, forKey: .label)
        value = Episode(episode: integral,
                        label: label ?? numeric.map { "Episode \($0)" },
                        mediaRef: try c.decodeIfPresent(String.self, forKey: .mediaRef),
                        image: try c.decodeIfPresent(String.self, forKey: .image),
                        season: (try? c.decode(Int.self, forKey: .season)) ?? (try? c.decode(Int.self, forKey: .seasonNumber))
                            ?? (try? c.decode(String.self, forKey: .season)).flatMap(Int.init)
                            ?? (try? c.decode(String.self, forKey: .seasonNumber)).flatMap(Int.init))
    }
}

/// One-shot startup decisions; AVKit owns subsequent explicit remote Play/Pause.
struct PlaybackStartupState {
    enum Action: Equatable { case none, seek, play }
    enum Activity { case active, inactive, background }
    private enum Phase { case waiting, seeking, ready, finished }
    private var phase = Phase.waiting
    private var autoPlay: Bool
    private var pendingPlay = false
    init(autoPlay: Bool) { self.autoPlay = autoPlay }
    mutating func becameReady(needsResume: Bool) -> Action {
        guard phase == .waiting else { return .none }
        phase = needsResume ? .seeking : .ready
        return needsResume ? .seek : (autoPlay ? .play : .none)
    }
    mutating func resumeCompleted(success: Bool) -> Action {
        guard phase == .seeking else { return .none }
        phase = .ready
        return success && autoPlay ? .play : .none
    }
    mutating func apply(_ action: Action, activity: Activity) -> Action {
        if activity == .background { enteredBackground(); return .none }
        guard action == .play, autoPlay, phase == .ready else { return .none }
        pendingPlay = activity == .inactive
        return pendingPlay ? .none : .play
    }
    mutating func becameActive() -> Action {
        guard pendingPlay, autoPlay, phase == .ready else { return .none }
        pendingPlay = false
        return .play
    }
    mutating func enteredBackground() { autoPlay = false; pendingPlay = false }
    mutating func finish() { phase = .finished; pendingPlay = false; autoPlay = false }
}

/// Turns AVFoundation errors and access-log details into a reason the viewer can act on.
enum PlaybackFailure: Equatable {
    case offline, expired, missing, blocked, unsupported, stalled, timedOut, generic

    /// The error chain wins; an error-log entry only explains an error that maps to nothing.
    init(error: Error?, logStatus: Int? = nil, logComment: String? = nil) {
        var next = error.map { $0 as NSError }
        var depth = 0
        while let current = next, depth < 6 {
            if let mapped = Self.forError(current) { self = mapped; return }
            next = current.userInfo[NSUnderlyingErrorKey] as? NSError
            depth += 1
        }
        self = Self.httpStatus(code: logStatus, comment: logComment).flatMap(Self.forHTTP) ?? .generic
    }

    var isRetryable: Bool { self != .unsupported && self != .missing && self != .blocked }

    var message: String {
        switch self {
        case .offline: return "Apple TV lost its internet connection. Check the network, then retry."
        case .expired: return "The source refused this stream. Its link may have expired: retry to fetch a new one, or choose another source."
        case .missing: return "This stream is no longer available from the source. Choose another source."
        case .blocked: return "Couldn't open the video source. The server may have blocked it. Choose another source."
        case .unsupported: return "This stream uses a format Apple TV cannot play. Choose another source."
        case .stalled: return "The stream stopped loading. Retry, or choose another source if it keeps happening."
        case .timedOut: return "The stream did not start in time. Retry or choose another source."
        case .generic: return "This stream could not be played. Retry or choose another source."
        }
    }

    private static func httpStatus(code: Int?, comment: String?) -> Int? {
        if let code, (400..<600).contains(code) { return code }
        // CoreMedia reports some HTTP failures with its own codes.
        switch code {
        case -12660: return 403
        case -12938: return 404
        default: break
        }
        guard let comment, let range = comment.range(of: #"HTTP (\d{3})"#, options: .regularExpression) else { return nil }
        return Int(comment[range].dropFirst(5))
    }

    private static func forHTTP(_ status: Int) -> PlaybackFailure? {
        switch status {
        case 401, 403: return .expired
        case 404, 410: return .missing
        default: return nil
        }
    }

    private static func forError(_ error: NSError) -> PlaybackFailure? {
        switch (error.domain, error.code) {
        case (NSURLErrorDomain, NSURLErrorNotConnectedToInternet), (NSURLErrorDomain, NSURLErrorNetworkConnectionLost),
             (NSURLErrorDomain, NSURLErrorDataNotAllowed), (NSURLErrorDomain, NSURLErrorInternationalRoamingOff):
            return .offline
        case (NSURLErrorDomain, NSURLErrorTimedOut): return .timedOut
        case (NSURLErrorDomain, NSURLErrorFileDoesNotExist), (NSURLErrorDomain, NSURLErrorResourceUnavailable): return .missing
        case (NSURLErrorDomain, NSURLErrorUserAuthenticationRequired), (NSURLErrorDomain, NSURLErrorNoPermissionsToReadFile): return .expired
        // fileFormatNotRecognized: usually a block or challenge page served instead of media.
        case ("AVFoundationErrorDomain", -11828): return .blocked
        // AVError fileFailedToParse, failedToParse, decodeFailed, decoderNotFound, formatUnsupported.
        case ("AVFoundationErrorDomain", -11829), ("AVFoundationErrorDomain", -11853),
             ("AVFoundationErrorDomain", -11821), ("AVFoundationErrorDomain", -11833), ("AVFoundationErrorDomain", -11864):
            return .unsupported
        case ("CoreMediaErrorDomain", let code):
            return httpStatus(code: code, comment: nil).flatMap(forHTTP)
        default: return nil
        }
    }
}
