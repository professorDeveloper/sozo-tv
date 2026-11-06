import Foundation

struct PlaybackHistoryContext {
    let provider: String
    let contentUrl: String
    let title: String
    let thumbnail: String?
    let isSerial: Bool
    let episodeIndex: Int?
    let episodeNumber: Int?
    var positionMs: Int64 = 0

    /// Positions are progress only. No duration watched or achievement counters are invented.
    func payload(positionSeconds: Double, durationSeconds: Double, at date: Date = Date()) -> [String: Any]? {
        guard !provider.isEmpty, !contentUrl.isEmpty,
              positionSeconds.isFinite, durationSeconds.isFinite,
              positionSeconds > 0, durationSeconds > 0,
              positionSeconds < Double(Int64.max) / 1000, durationSeconds < Double(Int64.max) / 1000,
              !isSerial || episodeNumber != nil || episodeIndex != nil else { return nil }
        var body: [String: Any] = [
            "provider": provider, "contentUrl": contentUrl, "contentId": contentUrl,
            "title": title, "isSerial": isSerial,
            "positionMs": Int64(min(positionSeconds, durationSeconds) * 1000),
            "durationMs": Int64(durationSeconds * 1000),
            "watchedAt": ISO8601DateFormatter().string(from: date)
        ]
        if let thumbnail { body["thumbnail"] = thumbnail }
        if isSerial {
            if let episodeIndex { body["episodeIndex"] = episodeIndex }
            if let episodeNumber { body["episodeNumber"] = episodeNumber }
        }
        return body
    }
}
