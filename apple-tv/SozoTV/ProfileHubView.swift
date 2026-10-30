import SwiftUI

enum ProfileDestination: String, CaseIterable, Identifiable {
    case library, history, activity, connections, sources, security, support, about, profiles, account, notifications, friends
    var id: String { rawValue }
    var title: String {
        switch self {
        case .account: return "Account"
        case .notifications: return "Notifications"
        case .friends: return "Friends"
        case .library: return "My library"
        case .history: return "Watch history"
        case .activity: return "Activity"
        case .connections: return "Connections"
        case .sources: return "Sources"
        case .security: return "Profile security"
        case .support: return "Help & support"
        case .about: return "About Sozo"
        case .profiles: return "Manage profiles"
        }
    }
    var symbol: String {
        switch self {
        case .account: return "person.crop.circle"
        case .notifications: return "bell"
        case .friends: return "person.2.fill"
        case .library: return "books.vertical"
        case .history: return "clock.arrow.circlepath"
        case .activity: return "chart.bar"
        case .connections: return "link"
        case .sources: return "play.rectangle.on.rectangle"
        case .security: return "lock.shield"
        case .support: return "questionmark.circle"
        case .about: return "info.circle"
        case .profiles: return "person.2"
        }
    }
}

struct ProfileHubView: View {
    @EnvironmentObject private var session: SessionStore
    let destination: (ProfileDestination) -> AnyView
    @State private var confirmSignOut = false
    private let columns = [GridItem(.flexible(), spacing: 28), GridItem(.flexible(), spacing: 28), GridItem(.flexible(), spacing: 28)]
    var body: some View {
        ZStack {
            SozoBackdrop()
            ScrollView {
                VStack(alignment: .leading, spacing: 36) {
                    HStack(spacing: 28) {
                        if let profile = session.activeProfile { ProfileAvatar(profile: profile, size: 110) }
                        VStack(alignment: .leading, spacing: 8) {
                            Text(session.activeProfile?.name ?? "Your profile").font(.system(size: 46, weight: .bold)).lineLimit(2)
                            Text(session.activeProfile?.isKids == true ? "Kids profile" : "Your space on the big screen").font(.system(size: 24)).foregroundStyle(.secondary)
                        }
                        Spacer()
                        NavigationLink { destination(.profiles) } label: { Label("Profiles", systemImage: "person.2").padding(24) }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("profile.manage")
                    }.padding(28).sozoContentSurface(cornerRadius: 28).focusSection().padding(.bottom, 8)
                    hubSection("Your viewing", routes: [.library, .history, .activity])
                    hubSection("Your account", routes: session.activeProfile?.isKids == true ? [.account, .notifications] : [.account, .notifications, .friends])
                    hubSection("Connected to you", routes: [.connections, .sources, .security])
                    Text("Make it yours").font(.system(size: 30, weight: .bold))
                    SozoGlassGroup {
                    LazyVGrid(columns: columns, spacing: 28) {
                        NavigationLink { TVSettingsView() } label: { tile("Appearance & playback", symbol: "slider.horizontal.3") }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("profile.settings")
                        routeLink(.support)
                        routeLink(.about)
                    }
                    }
                    Button(role: .destructive) { confirmSignOut = true } label: { Label("Sign out of this Apple TV", systemImage: "rectangle.portrait.and.arrow.right").padding(24) }.buttonStyle(SozoProfileButtonStyle()).padding(.top, 16)
                }.padding(70)
            }
        }.navigationTitle("Profile")
            .confirmationDialog("Sign out of this Apple TV?", isPresented: $confirmSignOut, titleVisibility: .visible) {
                Button("Sign out", role: .destructive) { session.signOut() }
                Button("Cancel", role: .cancel) {}
            }
    }
    private func hubSection(_ title: String, routes: [ProfileDestination]) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(title).font(.system(size: 30, weight: .bold))
            SozoGlassGroup {
                LazyVGrid(columns: columns, spacing: 28) { ForEach(routes) { routeLink($0) } }.focusSection()
            }
        }
    }
    private func routeLink(_ route: ProfileDestination) -> some View {
        NavigationLink { destination(route) } label: { tile(route.title, symbol: route.symbol) }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("profile.route.\(route.rawValue)")
    }
    private func tile(_ title: String, symbol: String) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            Image(systemName: symbol).font(.system(size: 34, weight: .medium)).foregroundStyle(Color.white.opacity(0.9))
            Text(title).font(.system(size: 30, weight: .semibold)).lineLimit(2).frame(height: 76, alignment: .topLeading)
        }.frame(maxWidth: .infinity, minHeight: 142, maxHeight: 142, alignment: .leading).padding(28)
    }
}
