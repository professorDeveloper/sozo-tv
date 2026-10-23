import Foundation

@main struct ProfileRequestChecks {
    @MainActor static func main() async {
        let state = ProfileRequestState()
        var value = 0
        let slow = Task {
            await state.run { try await Task.sleep(nanoseconds: 100_000_000); return 1 } apply: { value = $0 }
        }
        try? await Task.sleep(nanoseconds: 10_000_000)
        await state.run { 2 } apply: { value = $0 }
        await slow.value
        precondition(value == 2 && !state.busy && state.error == nil, "Newer request must win without cancellation error")
        let departing = Task {
            await state.run { try await Task.sleep(nanoseconds: 100_000_000); return 3 } apply: { value = $0 }
        }
        try? await Task.sleep(nanoseconds: 10_000_000)
        state.cancel()
        await departing.value
        precondition(value == 2 && !state.busy && state.error == nil, "Navigation cancellation must not publish stale content")
        await state.run { throw URLError(.notConnectedToInternet) } apply: { (_: Int) in }
        precondition(state.error != nil && !state.busy, "Network failure must be visible and release busy state")
        await state.run { 4 } apply: { value = $0 }
        precondition(value == 4 && state.error == nil, "Retry must clear prior error")
        print("PASS: latest request wins; screen cancellation; network error; successful retry")
    }
}
