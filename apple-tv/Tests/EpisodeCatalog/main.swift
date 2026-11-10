import Foundation
let page = try JSONDecoder().decode(EpisodePage.self, from: Data(#"{"episodes":[{"episode":1,"label":"S1E1","mediaRef":"vidapi:tv:tt:42:1:1"},{"episode":13,"label":"S2E1","mediaRef":"vidapi:tv:tt:42:2:1"},{"episode":1,"label":"Special","season":0},{"episode":2,"label":"Unlabelled"}]}"#.utf8))
let episodes = page.episodes!
precondition(EpisodeCatalog.seasons(in: episodes) == [0,1,2])
precondition(EpisodeCatalog.episodes(in: episodes, season: 2).map(\.episode) == [13])
precondition(EpisodeCatalog.episodes(in: episodes, season: nil).count == 1)
precondition(EpisodeCatalog.season(of: Episode(episode: 12, label: "s12e2 · Chapter", mediaRef: nil, image: nil)) == 12)
precondition(episodes[0].id != episodes[2].id)
print("PASS: seasons, specials, unknown episodes and provider history identity")

let repeats = [Episode(episode: 1, label: "S1E1", mediaRef: "first", image: nil), Episode(episode: 1, label: "S2E1", mediaRef: "second", image: nil)]
precondition(EpisodeCatalog.resumedEpisode(in: repeats, number: 1, index: 1)?.id == "second")
precondition(EpisodeCatalog.resumedEpisode(in: repeats, number: 1, index: nil) == nil)
print("PASS: repeated episode numbers resume only their matching season/index")
let stringSeason = try JSONDecoder().decode(EpisodePage.self, from: Data(#"{"episodes":[{"episode":"1","seasonNumber":"3","mediaRef":"other-provider:third-season"}]}"#.utf8))
precondition(EpisodeCatalog.seasons(in: stringSeason.episodes!) == [3])
print("PASS: string seasonNumber retains season grouping")
let missingReferences = [Episode(episode: 1, label: "S1E1", mediaRef: "", image: nil, season: 1), Episode(episode: 2, label: "S1E2", mediaRef: "", image: nil, season: 1)]
precondition(Set(missingReferences.map(\.id)).count == 2)
print("PASS: empty media references do not collapse distinct episodes")
