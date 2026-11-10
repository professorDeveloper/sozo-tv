import SwiftUI

struct ProfilePickerView: View {
    @EnvironmentObject private var session: SessionStore
    let onPick: () -> Void
    @State private var lockedProfile: HouseholdProfile?
    @State private var choosing = false
    @State private var returningProfileID: String?
    @State private var failure: String?
    @State private var confirmSignOut = false
    @FocusState private var focusedProfileID: String?

    var body: some View {
        VStack(spacing: 32) {
            Text("Who’s watching?").font(.system(size: 48, weight: .bold))
            SozoGlassGroup {
            ScrollView(.horizontal) {
                HStack(spacing: 28) {
                ForEach(session.profiles) { profile in
                    Button { choose(profile) } label: {
                        VStack(spacing: 14) {
                            ProfileAvatar(profile: profile, size: 150)
                            Label(profile.name, systemImage: profile.hasPin ? "lock.fill" : "person.fill")
                                .font(.system(size: 27, weight: .semibold)).lineLimit(2)
                                .multilineTextAlignment(.center).frame(height: 68)
                            Text(profile.isKids ? "Kids" : (profile.hasPin ? "PIN protected" : " "))
                                .font(.system(size: 20, weight: .medium)).foregroundStyle(.secondary)
                                .frame(height: 28)
                        }.frame(width: 230, height: 290).padding(22)
                    }.buttonStyle(SozoProfileButtonStyle()).disabled(choosing).accessibilityIdentifier("profile.pick.\(profile.id)")
                        .focused($focusedProfileID, equals: profile.id)
                }
                }.padding(.horizontal, 26).padding(.vertical, 24).focusSection()
            }.scrollIndicators(.hidden)
                .frame(width: min(CGFloat(max(session.profiles.count, 1)) * 302 + 24, 1680), height: 380)
            }
            if session.profiles.isEmpty && !session.busy { Text("No profiles available. Refresh to try again.").foregroundStyle(.secondary) }
            if session.busy || choosing { ProgressView() }
            if let text = failure ?? session.error { Text(text).foregroundStyle(.red).multilineTextAlignment(.center) }
            SozoGlassGroup {
            HStack(spacing: 24) {
                Button { Task { await session.loadProfiles() } } label: { SozoActionLabel(title: "Refresh", symbol: "arrow.clockwise") }.buttonStyle(SozoProfileButtonStyle()).disabled(session.busy || choosing)
                Button { confirmSignOut = true } label: { SozoActionLabel(title: "Sign out", symbol: "rectangle.portrait.and.arrow.right") }.buttonStyle(SozoProfileButtonStyle()).disabled(choosing)
            }
            }
        }.padding(50).frame(maxWidth: .infinity, maxHeight: .infinity)
        .background { SozoBackdrop() }
        .confirmationDialog("Sign out of this Apple TV?", isPresented: $confirmSignOut, titleVisibility: .visible) {
            Button("Sign out", role: .destructive) { session.signOut() }
            Button("Cancel", role: .cancel) {}
        }
        .onAppear { focusFirstProfileIfNeeded() }
        .onChange(of: session.profiles.map(\.id)) { _, _ in focusFirstProfileIfNeeded() }
        .sheet(item: $lockedProfile, onDismiss: {
            if let id = returningProfileID { focusedProfileID = id }
        }) { profile in
            ProfilePinView(title: profile.name, subtitle: "Enter your PIN to unlock this profile") { pin in
                try await session.select(profile, pin: pin)
                lockedProfile = nil
                onPick()
            }
        }
    }

    private func focusFirstProfileIfNeeded() {
        guard focusedProfileID == nil || !session.profiles.contains(where: { $0.id == focusedProfileID }) else { return }
        focusedProfileID = session.profiles.first?.id
    }

    private func choose(_ profile: HouseholdProfile) {
        guard !choosing else { return }
        failure = nil
        if profile.hasPin { returningProfileID = profile.id; lockedProfile = profile; return }
        choosing = true
        Task {
            defer { choosing = false }
            do { try await session.select(profile); onPick() }
            catch { failure = error.localizedDescription }
        }
    }
}

struct ProfileAvatar: View {
    let profile: HouseholdProfile
    var size: CGFloat = 120
    var body: some View {
        Group {
            if let avatar = profile.avatar, let image = ProfileArtwork.image(named: avatar) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ZStack {
                    Color(hex: profile.color) ?? SozoTheme.accent
                    Text(String(profile.name.prefix(1)).uppercased()).font(.system(size: size * 0.4, weight: .bold))
                        .foregroundStyle(ProfileColorContrast.usesDarkForeground(profile.color) ? Color.black : Color.white)
                }
            }
        }.frame(width: size, height: size).clipShape(.rect(cornerRadius: size * 0.2))
    }
}

extension Color {
    init?(hex: String?) {
        guard let hex else { return nil }
        let clean = hex.replacingOccurrences(of: "#", with: "")
        guard let value = UInt64(clean, radix: 16), clean.count == 6 else { return nil }
        self.init(red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255)
    }
}

/// Avatars are bundled PNG files, not symbol/template images or remote URLs.
enum ProfileArtwork {
    private static let cache = NSCache<NSString, UIImage>()
    static func image(named name: String) -> UIImage? {
        guard !name.contains("/"), !name.contains("..") else { return nil }
        if let image = cache.object(forKey: name as NSString) { return image }
        guard let url = Bundle.main.url(forResource: name, withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        let original = image.withRenderingMode(.alwaysOriginal)
        cache.countLimit = 48
        cache.setObject(original, forKey: name as NSString)
        return original
    }
}
