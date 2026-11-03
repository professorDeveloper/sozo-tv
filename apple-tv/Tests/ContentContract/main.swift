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

let media = try JSONDecoder().decode(MediaEnvelope.self, from: Data(#"{"videoUrl":"https://v.test/a.m3u8","subtitles":[{"label":"English","file":"https://s.test/a.srt.txt1","cipher":{"alg":"aes-128-cbc","key":"AmSmZVcH93UQUezi","iv":"ReBKWW8cqdjPEnF6","scope":"cue-text","encoding":"base64"}},{"file":"https://s.test/b.srt","cipher":{"alg":1}}]}"#.utf8))
precondition(media.subtitles?[0].cipher?.key == "AmSmZVcH93UQUezi" && media.subtitles?[0].cipher?.iv == "ReBKWW8cqdjPEnF6")
precondition(media.subtitles?.count == 2 && media.subtitles?[1].cipher == nil)
print("PASS: subtitle cipher survives decoding and a malformed one does not drop the track")
