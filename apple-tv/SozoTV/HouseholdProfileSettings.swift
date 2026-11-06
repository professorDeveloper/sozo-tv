import Foundation

/// Scope includes the signed-in device session and profile; no settings leak between accounts.
struct HouseholdProfileSettings {
    let scope: String
    var defaults: UserDefaults = .standard
    func key(_ name: String) -> String {
        "sozo.tv.profile." + Data(scope.utf8).base64EncodedString() + "." + name
    }
    var selectedProvider: String? {
        get { defaults.string(forKey: key("provider")) }
        nonmutating set { defaults.set(newValue, forKey: key("provider")) }
    }
    var maximumHeight: Int? {
        let value = defaults.integer(forKey: key("maximumHeight"))
        return [480, 720, 1080, 2160].contains(value) ? value : nil
    }
    var preferredPeakBitRate: Double { defaults.bool(forKey: key("dataSaver")) ? 800_000 : 0 }
    var autoPlay: Bool { defaults.object(forKey: key("autoPlay")) as? Bool ?? true }
    func remove() {
        for name in defaults.dictionaryRepresentation().keys where name.hasPrefix(key("")) { defaults.removeObject(forKey: name) }
    }
}
