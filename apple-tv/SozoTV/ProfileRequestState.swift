import Foundation
import Combine

/// One visible request per screen. Navigating away invalidates and cancels its pending response.
@MainActor final class ProfileRequestState: ObservableObject {
    @Published private(set) var busy = false
    @Published var error: String?
    @Published var notice: String?
    private var generation: UInt64 = 0
    private var cancelPending: (() -> Void)?

    func cancel() {
        generation &+= 1
        cancelPending?(); cancelPending = nil; busy = false
    }

    func run<T>(_ operation: @escaping @MainActor () async throws -> T,
                apply: @escaping @MainActor (T) -> Void) async {
        cancel()
        let current = generation
        busy = true; error = nil
        let task = Task { try await operation() }
        cancelPending = { task.cancel() }
        defer {
            if current == generation { busy = false; cancelPending = nil }
        }
        do {
            let value = try await withTaskCancellationHandler { try await task.value } onCancel: { task.cancel() }
            guard current == generation, !Task.isCancelled else { return }
            apply(value)
        } catch {
            guard current == generation, !Task.isCancelled,
                  !(error is CancellationError), (error as? URLError)?.code != .cancelled else { return }
            self.error = error.localizedDescription
        }
    }
}
