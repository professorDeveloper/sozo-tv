import Foundation

/// Image-wrapped TS segments go through the backend's media-segment relay, while
/// the playlists stay on the device: their tokens are bound to its address.
enum HLSPlaylistRelay {
    static let scheme = "sozo-hls"
    static let maximumPlaylistBytes = 4 * 1024 * 1024

    /// The embed rotates CDN hosts; its stream route is the stable part.
    static func needsManifestProxy(_ url: URL) -> Bool {
        url.scheme?.lowercased() == "https" && url.path.hasPrefix("/stream/") && url.path.hasSuffix(".m3u8")
    }

    static func loaderURL(_ url: URL) -> URL? {
        guard url.scheme?.lowercased() == "https", var parts = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        parts.scheme = scheme
        return parts.url
    }

    static func upstreamURL(_ url: URL) -> URL? {
        guard url.scheme?.lowercased() == scheme, var parts = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        parts.scheme = "https"
        return parts.url
    }

    /// Mirrors the backend allowlist in mediaSegmentRelay.js; anything else is left alone.
    static func segmentRelay(_ url: URL, endpoint: URL) -> URL? {
        let raw = url.absoluteString
        guard raw.count <= 8192, url.scheme?.lowercased() == "https", url.user == nil, url.password == nil,
              url.port == nil || url.port == 443, let host = url.host?.lowercased(),
              host.range(of: #"^p(?:16|19)-ad-site-sign-sg\.tiktokcdn\.com$"#, options: .regularExpression) != nil,
              url.path.hasPrefix("/ad-site-i18n-sg/"), url.path.hasSuffix(".image") else { return nil }
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~")
        guard let encoded = raw.addingPercentEncoding(withAllowedCharacters: allowed) else { return nil }
        return URL(string: endpoint.absoluteString + "?url=" + encoded)
    }

    /// Every URI is made absolute because the playlist is served from a custom scheme.
    /// Variant playlists come back through the loader; media playlist segments are
    /// relayed when the backend allows it and otherwise fetched directly.
    static func rewrite(_ playlist: String, base: URL, endpoint: URL) -> String? {
        guard playlist.drop(while: { $0 == "\u{FEFF}" || $0.isWhitespace }).hasPrefix("#EXTM3U") else { return nil }
        let master = playlist.contains("#EXT-X-STREAM-INF")
        func target(_ raw: String, playlist: Bool) -> String? {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let absolute = URL(string: trimmed, relativeTo: base)?.absoluteURL else { return nil }
            if playlist { return (loaderURL(absolute) ?? absolute).absoluteString }
            return (segmentRelay(absolute, endpoint: endpoint) ?? absolute).absoluteString
        }
        let attribute = try! NSRegularExpression(pattern: #"URI="([^"]+)""#)
        return playlist.components(separatedBy: "\n").map { line -> String in
            let stripped = line.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines.union(.init(charactersIn: "\u{FEFF}")))
            if stripped.isEmpty { return line }
            if stripped.hasPrefix("#") {
                let renditions = master && (stripped.hasPrefix("#EXT-X-MEDIA:") || stripped.hasPrefix("#EXT-X-I-FRAME-STREAM-INF:"))
                let text = line as NSString
                var out = line
                for match in attribute.matches(in: line, range: NSRange(location: 0, length: text.length)).reversed() {
                    let value = text.substring(with: match.range(at: 1))
                    guard let replaced = target(value, playlist: renditions) else { continue }
                    out = (out as NSString).replacingCharacters(in: match.range(at: 1), with: replaced)
                }
                return out
            }
            return target(stripped, playlist: master) ?? line
        }.joined(separator: "\n")
    }
}
