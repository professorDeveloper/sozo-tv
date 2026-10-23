import Foundation

/// Initial avatars must stay readable on both light and dark user-selected colors.
enum ProfileColorContrast {
    static func usesDarkForeground(_ hex: String?) -> Bool {
        guard let hex else { return false }
        let clean = hex.replacingOccurrences(of: "#", with: "")
        guard clean.count == 6, let rgb = UInt64(clean, radix: 16) else { return false }
        func linear(_ channel: UInt64) -> Double {
            let value = Double(channel) / 255
            return value <= 0.04045 ? value / 12.92 : pow((value + 0.055) / 1.055, 2.4)
        }
        let luminance = 0.2126 * linear((rgb >> 16) & 255)
            + 0.7152 * linear((rgb >> 8) & 255)
            + 0.0722 * linear(rgb & 255)
        let darkContrast = (luminance + 0.05) / 0.05
        let lightContrast = 1.05 / (luminance + 0.05)
        return darkContrast > lightContrast
    }
}
