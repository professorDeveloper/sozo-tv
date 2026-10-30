import Foundation
let data = Data(#"{"items":[{"id":"direct","name":"Direct source","browseOnly":false}]}"#.utf8)
do {
    let providers = try JSONDecoder().decode(ProviderCatalog.self, from: data).items
    assert(providers.count == 1)
    print("PASS: live provider envelope")
} catch { print("FAIL: live provider envelope: \(error)"); exit(1) }

let old = try JSONDecoder().decode(ProviderCatalog.self, from: Data(#"[{"id":"legacy"}]"#.utf8))
assert(old.items.count == 1)
let remote = try JSONDecoder().decode(ProviderCatalog.self, from: Data(#"{"items":[{"id":"phone-only","mode":"client"},{"id":"native","mode":"server"}]}"#.utf8))
assert(remote.items.filter(\.supportsServerBrowsing).map(\.id) == ["native"])
print("PASS: legacy compatibility and phone-only source exclusion")

let hero = try JSONDecoder().decode(MediaCard.self, from: Data(#"{"provider":"vidapi","title":"Featured","thumbnail":"https://image.test/poster.jpg","banner":"https://image.test/backdrop.jpg"}"#.utf8))
assert(hero.banner == "https://image.test/backdrop.jpg")
print("PASS: featured card retains its widescreen banner instead of only its portrait poster")
