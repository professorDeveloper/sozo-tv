import Foundation

/// Catalog metadata is descriptive. Only a successful request can verify availability.
enum SourceCatalog {
    static func unique(_ sources: [Provider]) -> [Provider] {
        var seen = Set<String>()
        return sources.filter { !$0.id.isEmpty && seen.insert($0.id).inserted }
    }
    static func title(_ source: Provider) -> String {
        let name = source.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return name.isEmpty ? source.id : name
    }
    static func category(_ source: Provider) -> String { source.category?.lowercased() ?? "other" }
    static func categoryName(_ value: String) -> String {
        switch value {
        case "anime": return "Anime"
        case "movies", "movie": return "Movies & series"
        case "tmdb": return "Movies & series (TMDB)"
        case "tv", "live": return "Live TV"
        default: return value == "other" ? "Other" : value.capitalized
        }
    }
    static func language(_ source: Provider) -> String { source.lang?.lowercased() ?? "unknown" }
    static func languageName(_ value: String) -> String {
        switch value {
        case "en": return "English"
        case "uz": return "Uzbek"
        case "ru": return "Russian"
        case "ja", "jp": return "Japanese"
        case "multi", "all": return "Multiple languages"
        case "unknown": return "Language not specified"
        default: return Locale(identifier: "en").localizedString(forLanguageCode: value) ?? value.uppercased()
        }
    }
    static func defaultID(_ sources: [Provider]) -> String? {
        let available = unique(sources).filter(\.supportsServerBrowsing)
        return (available.first { $0.id == "vidapi" } ?? available.first)?.id
    }
    static func selectedID(_ sources: [Provider], preferred: String?) -> String? {
        sources.first { $0.id == preferred && $0.supportsServerBrowsing }?.id ?? defaultID(sources)
    }
    static func filtered(_ sources: [Provider], query: String, category: String = "all", language: String = "all") -> [Provider] {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return unique(sources).filter { source in
            source.supportsServerBrowsing && (category == "all" || self.category(source) == category)
                && (language == "all" || self.language(source) == language)
                && (text.isEmpty || [title(source), source.id, source.description ?? "", languageName(self.language(source)), categoryName(self.category(source))].contains { $0.localizedCaseInsensitiveContains(text) })
        }.sorted { title($0).localizedStandardCompare(title($1)) == .orderedAscending }
    }
}
