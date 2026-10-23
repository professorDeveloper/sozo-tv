import Foundation

struct DeviceCode: Decodable {
    let deviceCode: String
    let userCode: String
    let expiresIn: Int
    let interval: Int
    enum CodingKeys: String, CodingKey {
        case deviceCode = "device_code", userCode = "user_code"
        case expiresIn = "expires_in", interval
    }
}

struct DeviceToken: Decodable {
    let status: String?
    let accessToken: String?
    let refreshToken: String?
    let sessionId: String?
}

struct HouseholdProfile: Codable, Identifiable, Hashable {
    let id: String
    var name: String
    var avatar: String?
    var color: String?
    var isKids: Bool
    var isDefault: Bool
    var hasPin: Bool
    var createdAt: String?

    init(id: String, name: String, avatar: String? = nil, color: String? = nil,
         isKids: Bool = false, isDefault: Bool = false, hasPin: Bool = false, createdAt: String? = nil) {
        self.id = id; self.name = name; self.avatar = avatar; self.color = color
        self.isKids = isKids; self.isDefault = isDefault; self.hasPin = hasPin; self.createdAt = createdAt
    }
    enum CodingKeys: String, CodingKey { case id, name, avatar, color, isKids, isDefault, hasPin, createdAt }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        avatar = try c.decodeIfPresent(String.self, forKey: .avatar)
        color = try c.decodeIfPresent(String.self, forKey: .color)
        isKids = try c.decodeIfPresent(Bool.self, forKey: .isKids) ?? false
        isDefault = try c.decodeIfPresent(Bool.self, forKey: .isDefault) ?? false
        hasPin = try c.decodeIfPresent(Bool.self, forKey: .hasPin) ?? false
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
    }
}

struct ProfilesEnvelope: Decodable {
    let profiles: [HouseholdProfile]
    let activeProfileId: String?
    let max: Int
}

struct Provider: Decodable, Identifiable, Hashable {
    let id: String
    let name: String?
    let image: String?
    let browseOnly: Bool?
    var mode: String? = nil
    var category: String? = nil
    var lang: String? = nil
    var description: String? = nil
    var url: String? = nil
    var supportsServerBrowsing: Bool { browseOnly != true && mode != "client" }
}

struct ProviderCatalog: Decodable {
    let items: [Provider]
    enum CodingKeys: String, CodingKey { case items }
    init(from decoder: Decoder) throws {
        if let array = try? decoder.singleValueContainer().decode([Provider].self) { items = array }
        else { items = try decoder.container(keyedBy: CodingKeys.self).decode([Provider].self, forKey: .items) }
    }
}

struct MediaCard: Decodable, Identifiable, Hashable {
    var id: String { "\(provider ?? "")|\(contentUrl ?? slug ?? title ?? "")" }
    let provider: String?
    let title: String?
    let contentUrl: String?
    let slug: String?
    let thumbnail: String?
    let type: String?
    var banner: String? = nil
}

struct HomeSection: Decodable, Identifiable {
    var id: String { key ?? label ?? items.map(\.id).joined(separator: "|") }
    let key: String?
    let label: String?
    let items: [MediaCard]
}

struct HomePayload: Decodable {
    let banner: [MediaCard]?
    let sections: [HomeSection]
}

struct Episode: Decodable, Identifiable {
    var id: String {
        if let mediaRef, !mediaRef.isEmpty { return mediaRef }
        return "season-\(season ?? -1)-episode-\(episode ?? 0)-\(label ?? "")"
    }
    let episode: Int?
    let label: String?
    let mediaRef: String?
    let image: String?
    var season: Int? = nil
}

struct EpisodesEnvelope: Decodable {
    let episodes: [Episode]?
    let videoSources: [VideoSource]?
    let subtitles: [SubtitleTrack]?
}

struct MediaDetail: Decodable {
    let title: String?
    let description: String?
    let thumbnail: String?
    let banner: String?
    let genres: [String]?
    let year: FlexibleString?
    let isSerial: Bool?
}

enum FlexibleString: Decodable {
    case value(String)
    init(from decoder: Decoder) throws {
        let box = try decoder.singleValueContainer()
        if let s = try? box.decode(String.self) { self = .value(s) }
        else if let n = try? box.decode(Int.self) { self = .value(String(n)) }
        else { self = .value("") }
    }
    var text: String { if case .value(let value) = self { value } else { "" } }
}

struct VideoSource: Decodable, Identifiable, Hashable {
    var id: String { videoUrl }
    let quality: String?
    let videoUrl: String
    let type: String?
    let isDefault: Bool?
    let headers: [String: String]?
}

struct SubtitleTrack: Decodable, Identifiable, Hashable {
    var id: String { file }
    let label: String?
    let file: String
}

struct MediaEnvelope: Decodable {
    /// Inherit response format for signed URLs without file extensions.
    var playbackSources: [VideoSource] {
        var sources = (videoSources ?? []).map { source in
            VideoSource(quality: source.quality, videoUrl: source.videoUrl, type: source.type ?? type,
                        isDefault: source.isDefault, headers: source.headers)
        }
        if let videoUrl, !videoUrl.isEmpty {
            sources.append(VideoSource(quality: "Auto", videoUrl: videoUrl, type: type, isDefault: true, headers: headers))
        }
        return sources
    }
    let videoUrl: String?
    let type: String?
    let headers: [String: String]?
    let videoSources: [VideoSource]?
    let subtitles: [SubtitleTrack]?
}
