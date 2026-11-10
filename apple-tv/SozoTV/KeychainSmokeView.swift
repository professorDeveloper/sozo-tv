#if DEBUG
import SwiftUI

/// Exercises the actual installed app's Keychain entitlement in an isolated test service.
struct KeychainSmokeView: View {
    @State private var result = "Checking secure storage"
    var body: some View {
        Text(result).accessibilityIdentifier("keychain.result").task {
            let storage = KeychainCredentialStorage(service: "com.saikou.sozotv.keychain-smoke")
            let first = DeviceCredentials(accessToken: "test-access", refreshToken: "test-refresh", sessionId: "test-session")
            let rotated = DeviceCredentials(accessToken: "test-access-2", refreshToken: "test-refresh-2", sessionId: "test-session")
            do {
                try storage.clear()
                try storage.save(first)
                guard try storage.load() == first else { result = "FAIL: initial read"; return }
                try storage.save(rotated)
                guard try storage.load() == rotated else { result = "FAIL: rotated read"; return }
                try storage.clear()
                guard try storage.load() == nil else { result = "FAIL: deletion"; return }
                result = "PASS: secure storage save, load, update, delete"
            } catch APIError.keychain(let status) { result = "FAIL: Keychain status \(status)" }
            catch { result = "FAIL: secure storage" }
        }
    }
}
#endif
