import CommonCrypto
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
        case tooLarge, invalidEncoding, invalidTiming, tooManyCues, invalidFormat, undecryptable
        var errorDescription: String? {
            switch self {
            case .tooLarge: "Subtitle file is too large."
            case .invalidEncoding: "Subtitle text encoding is unsupported."
            case .invalidTiming: "Subtitle file contains invalid timing."
            case .tooManyCues: "Subtitle file contains too many captions."
            case .invalidFormat: "The source did not return a supported subtitle file."
            case .undecryptable: "Subtitle file could not be decrypted."
            }
        }
    }

    static func isRemoteURL(_ url: URL) -> Bool {
        ["http", "https"].contains(url.scheme?.lowercased() ?? "")
            && !(url.host ?? "").isEmpty && url.user == nil && url.password == nil
    }

    /// Parses WebVTT or SubRip without evaluating markup or accepting HTML responses.
    static func parse(_ data: Data) throws -> [SubtitleCue] {
        let normalized = try decodeText(data)
        // Providers occasionally return an HTML error document with subtitle-like text.
        // Only detect document markup before cue parsing; angle brackets in real captions are valid.
        let beginning = normalized.trimmingCharacters(in: .whitespacesAndNewlines).prefix(512)
        guard beginning.range(of: #"(?is)^(?:<!--.*?-->\s*)*(?:<!doctype\s+html\b|<html\b|<head\b|<body\b)"#,
                              options: .regularExpression) == nil else { throw ParseError.invalidFormat }
        let lines = normalized.components(separatedBy: "\n")
        return try cues(lines)
    }

    /// Opens subtitles whose cue text lines are each AES-128-CBC encrypted and base64 encoded.
    /// Indices and timing lines are left alone; a key that opens no line is an error.
    static func decryptCueText(_ data: Data, alg: String, key: String, iv: String,
                               scope: String? = nil, encoding: String? = nil) throws -> Data {
        guard alg.lowercased() == "aes-128-cbc", (scope ?? "cue-text").lowercased() == "cue-text",
              (encoding ?? "base64").lowercased() == "base64" else { throw ParseError.undecryptable }
        let keyBytes = Data(key.utf8), ivBytes = Data(iv.utf8)
        guard keyBytes.count == kCCKeySizeAES128, ivBytes.count == kCCBlockSizeAES128 else { throw ParseError.undecryptable }
        var encrypted = 0, opened = 0
        let lines = try decodeText(data).components(separatedBy: "\n").map { line -> String in
            let text = line.trimmingCharacters(in: .whitespaces)
            guard !text.isEmpty, !text.contains("-->"), text != "WEBVTT",
                  !text.allSatisfy({ $0.isASCII && $0.isNumber }) else { return line }
            encrypted += 1
            guard let cipherText = Data(base64Encoded: text),
                  let plain = aes128CBCDecrypt(cipherText, key: keyBytes, iv: ivBytes) else { return line }
            opened += 1
            return plain
        }
        if encrypted > 0 && opened == 0 { throw ParseError.undecryptable }
        return Data(lines.joined(separator: "\n").utf8)
    }

    private static func aes128CBCDecrypt(_ data: Data, key: Data, iv: Data) -> String? {
        guard !data.isEmpty, data.count % kCCBlockSizeAES128 == 0 else { return nil }
        var output = Data(count: data.count + kCCBlockSizeAES128)
        let capacity = output.count
        var written = 0
        let status = output.withUnsafeMutableBytes { out in
            data.withUnsafeBytes { input in
                key.withUnsafeBytes { k in
                    iv.withUnsafeBytes { v in
                        CCCrypt(CCOperation(kCCDecrypt), CCAlgorithm(kCCAlgorithmAES), CCOptions(kCCOptionPKCS7Padding),
                                k.baseAddress, key.count, v.baseAddress, input.baseAddress, data.count,
                                out.baseAddress, capacity, &written)
                    }
                }
            }
        }
        guard status == kCCSuccess else { return nil }
        return String(data: output.prefix(written), encoding: .utf8)
    }

    private static func decodeText(_ data: Data) throws -> String {
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
        return decoded.replacingOccurrences(of: "\u{FEFF}", with: "")
            .replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
    }

    private static func cues(_ lines: [String]) throws -> [SubtitleCue] {
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
