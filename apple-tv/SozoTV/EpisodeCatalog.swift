import Foundation

/// Season grouping never changes the provider's episode/history number or media reference.
enum EpisodeCatalog {
    static func season(of episode: Episode) -> Int? {
        if let season = episode.season, season >= 0 { return season }
        if let ref = episode.mediaRef, ref.hasPrefix("vidapi:tv:") {
            let pieces = ref.split(separator: ":", omittingEmptySubsequences: false)
            if pieces.count == 6, let season = Int(pieces[4]), season >= 0 { return season }
        }
        guard let label = episode.label,
              let range = label.range(of: #"(?i)^\s*S(\d+)\s*E\d+"#, options: .regularExpression) else { return nil }
        let prefix = String(label[range]).uppercased()
        return Int(prefix.drop(while: { $0 != "S" }).dropFirst().prefix(while: { $0.isNumber }))
    }
    static func seasons(in episodes: [Episode]) -> [Int] {
        Array(Set(episodes.compactMap { season(of: $0) })).sorted()
    }
    static func episodes(in episodes: [Episode], season: Int?) -> [Episode] {
        episodes.filter { self.season(of: $0) == season }
    }
    static func resumedEpisode(in episodes: [Episode], number: Int?, index: Int?) -> Episode? {
        if let index, episodes.indices.contains(index), number == nil || episodes[index].episode == number { return episodes[index] }
        guard let number else { return nil }
        let matches = episodes.filter { $0.episode == number }
        return matches.count == 1 ? matches[0] : nil
    }

}
