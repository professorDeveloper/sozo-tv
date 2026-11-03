import Foundation
let payload = Data(#"{"items":[{"provider":"source","contentUrl":"title","isSerial":true,"episodeNumber":12.5,"episodeIndex":12,"positionMs":10000,"durationMs":60000},{"provider":"source","contentUrl":"other","isSerial":false,"positionMs":10000,"durationMs":60000}]}"#.utf8)
do {
    let history = try JSONDecoder().decode(ContinueWatchingEnvelope.self, from: payload)
    precondition(history.items.count == 2 && history.items[0].episodeNumber == nil)
    precondition(history.items[0].context.episodeIndex == 12 && history.items[0].canResume)
    precondition(history.items[1].canResume)
    let mixed = Data(#"{"items":[{"provider":"source","contentUrl":"a","positionMs":1000,"durationMs":60000},{"provider":"source","contentUrl":"b","positionMs":"soon"},{"provider":"source","contentUrl":"e","isSerial":true,"episodeIndex":"7","positionMs":1000,"durationMs":60000},{"contentUrl":"c"},42,{"provider":"source","contentUrl":"d","positionMs":2000,"durationMs":60000}]}"#.utf8)
    let rows = try JSONDecoder().decode(ContinueWatchingEnvelope.self, from: mixed).items
    precondition(rows.map(\.contentUrl) == ["a", "e", "d"], "bad rows are skipped, good rows kept")
    precondition(rows[1].episodeIndex == 7 && rows[1].canResume, "a string episode index is read like a string episode number")
    print("PASS: fractional mobile history preserves its episode index and other resume entries; bad rows do not hide the rail")
} catch { print("FAIL: fractional history decoding: \(error)"); exit(1) }
