import Foundation

let suite = "SozoProfileSettingsTest-" + UUID().uuidString
let defaults = UserDefaults(suiteName: suite)!
defer { defaults.removePersistentDomain(forName: suite) }
let a = HouseholdProfileSettings(scope: "accountA:profileA", defaults: defaults)
let b = HouseholdProfileSettings(scope: "accountA:profileB", defaults: defaults)
let other = HouseholdProfileSettings(scope: "accountB:profileA", defaults: defaults)
assert(a.maximumHeight == nil && a.autoPlay && a.preferredPeakBitRate == 0)
defaults.set(1080, forKey: a.key("maximumHeight"))
defaults.set(true, forKey: a.key("dataSaver"))
defaults.set(false, forKey: a.key("autoPlay"))
assert(a.maximumHeight == 1080 && a.preferredPeakBitRate == 800_000 && !a.autoPlay)
assert(b.maximumHeight == nil && b.autoPlay && b.preferredPeakBitRate == 0)
assert(other.maximumHeight == nil && other.autoPlay)
a.selectedProvider = "provider-one"
assert(a.selectedProvider == "provider-one")
assert(b.selectedProvider == nil && other.selectedProvider == nil)
let restored = HouseholdProfileSettings(scope: "accountA:profileA", defaults: defaults)
assert(restored.maximumHeight == 1080 && !restored.autoPlay)
defaults.set(9999, forKey: a.key("maximumHeight"))
assert(a.maximumHeight == nil)
defaults.set("1080p", forKey: a.key("source.fixture"))
defaults.set("720p", forKey: b.key("source.fixture"))
a.remove()
assert(defaults.object(forKey: a.key("source.fixture")) == nil)
assert(defaults.string(forKey: b.key("source.fixture")) == "720p")
assert(a.maximumHeight == nil && a.autoPlay && a.selectedProvider == nil)
print("Profile settings: defaults, profile/account isolation, restoration, invalid limit and removal passed")
