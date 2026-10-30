import Foundation
let payload = Data(#"{"items":[{"provider":"source","contentUrl":"title","isSerial":true,"episodeNumber":12.5,"episodeIndex":12,"positionMs":10000,"durationMs":60000},{"provider":"source","contentUrl":"other","isSerial":false,"positionMs":10000,"durationMs":60000}]}"#.utf8)
do {
    let history = try JSONDecoder().decode(ContinueWatchingEnvelope.self, from: payload)
    precondition(history.items.count == 2 && history.items[0].episodeNumber == nil)
    precondition(history.items[0].context.episodeIndex == 12 && history.items[0].canResume)
    precondition(history.items[1].canResume)
    print("PASS: fractional mobile history preserves its episode index and other resume entries")
} catch { print("FAIL: fractional history decoding: \(error)"); exit(1) }
