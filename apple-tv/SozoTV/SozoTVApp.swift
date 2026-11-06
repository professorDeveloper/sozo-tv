import SwiftUI

@main
struct SozoTVApp: App {
    @StateObject private var session: SessionStore

    init() {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        let store: SessionStore
        if args.contains("--device-link-fixture") { store = ProfileUITestFixture.makeDeviceLinkSession(reset: args.contains("--reset-device-link-fixture")) }
        else if args.contains("--profile-ui-fixture") || args.contains("--browse-ui-fixture") || args.contains("--player-smoke") { store = ProfileUITestFixture.makeSession() }
        else { store = SessionStore() }
        _session = StateObject(wrappedValue: store)
        #else
        _session = StateObject(wrappedValue: SessionStore())
        #endif
    }

    @ViewBuilder private var appContent: some View {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--keychain-smoke") { KeychainSmokeView() }
        else if ProcessInfo.processInfo.arguments.contains("--player-smoke") { PlaybackSmokeView() }
        else { RootView() }
        #else
        RootView()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            appContent
                .background { SozoBackdrop() }
                .environmentObject(session)
                .preferredColorScheme(.dark)
        }
    }
}

enum SozoTheme {
    static let background = Color(red: 20 / 255, green: 20 / 255, blue: 20 / 255)
    static let surface = Color.white.opacity(0.10)
    static let accent = Color(red: 254 / 255, green: 69 / 255, blue: 69 / 255)
}
