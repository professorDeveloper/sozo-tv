import Foundation

/// Plain-text sidecar captions. Times are seconds on the media timeline.
struct SubtitleCue: Equatable, Sendable {
    let start: Double
    let end: Double
    let text: String
}

enum SidecarSubtitles {
    static let maximumBytes = 2 * 1024 * 1024
    private static let maximumCues = 20_000
    private static let maximumDuration: Double = 3600

    enum ParseError: Error, LocalizedError {
        case tooLarge, invalidEncoding, invalidTiming, tooManyCues, invalidFormat
        var errorDescription: String? {
            switch self {
            case .tooLarge: "Subtitle file is too large."
            case .invalidEncoding: "Subtitle text encoding is unsupported."
            case .invalidTiming: "Subtitle file contains invalid timing."
            case .tooManyCues: "Subtitle file contains too many captions."
            case .invalidFormat: "The source did not return a supported subtitle file."
            }
        }
    }

    static func isRemoteURL(_ url: URL) -> Bool {
        ["http", "https"].contains(url.scheme?.lowercased() ?? "")
            && !(url.host ?? "").isEmpty && url.user == nil && url.password == nil
    }

    /// Parses WebVTT or SubRip without evaluating markup or accepting HTML responses.
    static func parse(_ data: Data) throws -> [SubtitleCue] {
        guard data.count <= maximumBytes else { throw ParseError.tooLarge }
        let decoded: String?
        if data.starts(with: [0xFF, 0xFE]) {
            decoded = String(data: data.dropFirst(2), encoding: .utf16LittleEndian)
        } else if data.starts(with: [0xFE, 0xFF]) {
            decoded = String(data: data.dropFirst(2), encoding: .utf16BigEndian)
        } else {
            decoded = String(data: data, encoding: .utf8)
        }
        guard let decoded, !decoded.contains("\0") else { throw ParseError.invalidEncoding }
        let normalized = decoded.replacingOccurrences(of: "\u{FEFF}", with: "")
            .replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        // Providers occasionally return an HTML error document with subtitle-like text.
        // Only detect document markup before cue parsing; angle brackets in real captions are valid.
        let beginning = normalized.trimmingCharacters(in: .whitespacesAndNewlines).prefix(512)
        guard beginning.range(of: #"(?is)^(?:<!--.*?-->\s*)*(?:<!doctype\s+html\b|<html\b|<head\b|<body\b)"#,
                              options: .regularExpression) == nil else { throw ParseError.invalidFormat }
        let lines = normalized.components(separatedBy: "\n")
        var cues: [SubtitleCue] = []
        var index = 0
        while index < lines.count {
            let line = lines[index].trimmingCharacters(in: .whitespaces)
            if line == "NOTE" || line.hasPrefix("NOTE ") || line == "STYLE" || line == "REGION" {
                index += 1
                while index < lines.count && !lines[index].trimmingCharacters(in: .whitespaces).isEmpty { index += 1 }
                continue
            }
            // Header metadata, cue identifiers and numbered SRT identifiers precede timing.
            guard line.contains("-->") else { index += 1; continue }
            let halves = line.components(separatedBy: "-->")
            guard halves.count == 2,
                  let start = timestamp(halves[0].trimmingCharacters(in: .whitespaces)),
                  let endToken = halves[1].split(whereSeparator: { $0.isWhitespace }).first,
                  let end = timestamp(String(endToken)), end > start,
                  end - start <= maximumDuration else { throw ParseError.invalidTiming }
            index += 1
            var captionLines: [String] = []
            while index < lines.count && !lines[index].trimmingCharacters(in: .whitespaces).isEmpty {
                // A missing separator must not consume the next timing line as caption text.
                if lines[index].contains("-->") { break }
                captionLines.append(lines[index])
                index += 1
            }
            let raw = captionLines.joined(separator: "\n")
            guard raw.utf8.count <= 8192 else { throw ParseError.tooLarge }
            let text = plainText(raw)
            if !text.isEmpty { cues.append(SubtitleCue(start: start, end: end, text: text)) }
            guard cues.count <= maximumCues else { throw ParseError.tooManyCues }
        }
        guard !cues.isEmpty else { throw ParseError.invalidFormat }
        return cues.sorted { $0.start == $1.start ? $0.end < $1.end : $0.start < $1.start }
    }

    /// Cue ends are exclusive. Supports overlapping captions in timestamp order.
    /// `cues` should be the sorted array returned by `parse`.
    static func text(at time: Double, cues: [SubtitleCue]) -> String? {
        guard time.isFinite, time >= 0, !cues.isEmpty else { return nil }
        var lower = 0, upper = cues.count
        while lower < upper {
            let middle = lower + (upper - lower) / 2
            if cues[middle].start <= time { lower = middle + 1 } else { upper = middle }
        }
        var active: [String] = []
        var index = lower - 1
        while index >= 0 {
            let cue = cues[index]
            if cue.start < time - maximumDuration { break }
            if cue.end > time { active.append(cue.text) }
            index -= 1
        }
        return active.isEmpty ? nil : active.reversed().joined(separator: "\n")
    }

    private static func timestamp(_ value: String) -> Double? {
        let fields = value.replacingOccurrences(of: ",", with: ".").split(separator: ":", omittingEmptySubsequences: false)
        guard fields.count == 2 || fields.count == 3 else { return nil }
        let secondsFields = fields.last!.split(separator: ".", omittingEmptySubsequences: false)
        guard secondsFields.count == 2, secondsFields[0].count == 2, secondsFields[1].count == 3,
              secondsFields.allSatisfy({ !$0.isEmpty && $0.allSatisfy({ $0.isASCII && $0.isNumber }) }),
              let seconds = Double(fields.last!), seconds < 60 else { return nil }
        let minuteField = fields[fields.count - 2]
        guard minuteField.count == 2, minuteField.allSatisfy({ $0.isASCII && $0.isNumber }),
              let minutes = Double(minuteField), minutes < 60 else { return nil }
        var hours: Double = 0
        if fields.count == 3 {
            guard fields[0].count >= 2, fields[0].count <= 3,
                  fields[0].allSatisfy({ $0.isASCII && $0.isNumber }), let parsed = Double(fields[0]), parsed <= 168 else { return nil }
            hours = parsed
        }
        let result = hours * 3600 + minutes * 60 + seconds
        return result.isFinite ? result : nil
    }

    private static func plainText(_ raw: String) -> String {
        var value = raw.replacingOccurrences(of: #"(?is)<(script|style)\b[^>]*>.*?</\1\s*>"#, with: "", options: .regularExpression)
        value = value.replacingOccurrences(of: #"(?i)<br\s*/?>"#, with: "\n", options: .regularExpression)
        value = value.replacingOccurrences(of: #"<[^>]*>"#, with: "", options: .regularExpression)
        // Decode only after removing tags, so encoded angle brackets remain literal text.
        let entityPattern = try! NSRegularExpression(pattern: #"&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z]{2,8});"#)
        let ns = value as NSString
        for match in entityPattern.matches(in: value, range: NSRange(location: 0, length: ns.length)).reversed() {
            let entity = ns.substring(with: match.range(at: 1))
            let named = ["amp": "&", "lt": "<", "gt": ">", "quot": "\"", "apos": "'", "nbsp": " ", "lrm": "", "rlm": ""]
            var replacement = named[entity]
            let scalarValue: UInt32?
            if entity.hasPrefix("#x") { scalarValue = UInt32(entity.dropFirst(2), radix: 16) }
            else if entity.hasPrefix("#") { scalarValue = UInt32(entity.dropFirst()) }
            else { scalarValue = nil }
            if let scalarValue, let scalar = UnicodeScalar(scalarValue), scalarValue >= 32, !(127...159).contains(scalarValue) {
                replacement = String(scalar)
            }
            if let replacement, let range = Range(match.range, in: value) { value.replaceSubrange(range, with: replacement) }
        }
        return String(value.unicodeScalars.filter { !CharacterSet.controlCharacters.contains($0) || $0 == "\n" || $0 == "\t" })
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
