import Foundation

struct ContinueWatchingEnvelope: Decodable { let items: [ContinueWatchingItem] }
struct ContinueWatchingItem: Decodable, Identifiable {
    let provider: String
    let contentUrl: String
    let title: String?
    let thumbnail: String?
    let isSerial: Bool?
    let episodeNumber: Int?
    let episodeIndex: Int?
    let positionMs: Int64?
    let durationMs: Int64?
    let deletedAt: String?
    enum CodingKeys: String, CodingKey { case provider, contentUrl, title, thumbnail, isSerial, episodeNumber, episodeIndex, positionMs, durationMs, deletedAt }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        provider = try c.decode(String.self, forKey: .provider)
        contentUrl = try c.decode(String.self, forKey: .contentUrl)
        title = try c.decodeIfPresent(String.self, forKey: .title)
        thumbnail = try c.decodeIfPresent(String.self, forKey: .thumbnail)
        isSerial = try c.decodeIfPresent(Bool.self, forKey: .isSerial)
        let number = (try? c.decode(Double.self, forKey: .episodeNumber))
            ?? (try? c.decode(String.self, forKey: .episodeNumber)).flatMap(Double.init)
        episodeNumber = number.flatMap { Int(exactly: $0) }
        episodeIndex = try c.decodeIfPresent(Int.self, forKey: .episodeIndex)
        positionMs = try c.decodeIfPresent(Int64.self, forKey: .positionMs)
        durationMs = try c.decodeIfPresent(Int64.self, forKey: .durationMs)
        deletedAt = try c.decodeIfPresent(String.self, forKey: .deletedAt)
    }
    var id: String { "\(provider)|\(contentUrl)|\(episodeNumber ?? episodeIndex ?? -1)" }
    var canResume: Bool {
        !provider.isEmpty && !provider.contains(":") && !contentUrl.isEmpty && !contentUrl.contains("\u{1}")
            && (deletedAt ?? "").isEmpty && (positionMs ?? 0) > 0
            && (isSerial != true || episodeNumber != nil || episodeIndex != nil)
            && (durationMs ?? 0) > 0 && Double(positionMs ?? 0) / Double(durationMs ?? 1) < 0.95
    }
    var card: MediaCard { MediaCard(provider: provider, title: title, contentUrl: contentUrl, slug: nil, thumbnail: thumbnail, type: isSerial == true ? "series" : "movie") }
    var context: PlaybackHistoryContext { PlaybackHistoryContext(provider: provider, contentUrl: contentUrl, title: title ?? "", thumbnail: thumbnail, isSerial: isSerial ?? false, episodeIndex: episodeIndex, episodeNumber: episodeNumber, positionMs: max(0, positionMs ?? 0)) }
}

