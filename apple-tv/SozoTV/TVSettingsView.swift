import SwiftUI

struct TVSettingsView: View {
    @FocusState private var appearanceFocus: String?
    @AppStorage("sozo.tv.glassEnabled") private var glassEnabled = true
    @AppStorage("sozo.tv.ambientColor") private var ambientColor = true
    @EnvironmentObject private var session: SessionStore
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var supportsGlass: Bool {
        if #available(tvOS 26.0, *) { return true }
        return false
    }
    var body: some View {
        ZStack {
            SozoBackdrop()
            Form {
                Section("Appearance — this Apple TV") {
                    Toggle("Liquid Glass", isOn: $glassEnabled)
                        .disabled(!supportsGlass || reduceTransparency)
                        .focused($appearanceFocus, equals: "glass")
                        .foregroundStyle(appearanceFocus == "glass" ? Color.black : Color.white)
                        .accessibilityIdentifier("settings.liquidGlass")
                    Toggle("Ambient color", isOn: $ambientColor)
                        .disabled(reduceTransparency)
                        .focused($appearanceFocus, equals: "ambient")
                        .foregroundStyle(appearanceFocus == "ambient" ? Color.black : Color.white)
                        .accessibilityIdentifier("settings.ambientColor")
                    if #unavailable(tvOS 26.0) { Text("Liquid Glass requires tvOS 26. This Apple TV uses solid surfaces.").foregroundStyle(.secondary).font(.system(size: 22)) }
                    if reduceTransparency { Text("Reduce Transparency is enabled in Apple TV Accessibility settings. Surfaces stay solid.").foregroundStyle(.secondary).font(.system(size: 22)) }
                    if reduceMotion { Text("Reduce Motion is enabled. Focus remains visible without scaling.").foregroundStyle(.secondary).font(.system(size: 22)) }
                }
                ScopedPlaybackSettings(scope: session.profileScopeIdentity)
                    .id(session.profileScopeIdentity)
            }.padding(.horizontal, 40)
        }.navigationTitle("Appearance & playback")
    }
}

private struct ScopedPlaybackSettings: View {
    @FocusState private var playbackFocus: String?
    @AppStorage private var maximumHeight: Int
    @AppStorage private var dataSaver: Bool
    @AppStorage private var autoPlay: Bool
    init(scope: String) {
        let settings = HouseholdProfileSettings(scope: scope)
        _maximumHeight = AppStorage(wrappedValue: 0, settings.key("maximumHeight"))
        _dataSaver = AppStorage(wrappedValue: false, settings.key("dataSaver"))
        _autoPlay = AppStorage(wrappedValue: true, settings.key("autoPlay"))
    }
    var body: some View {
        Section("Playback — current profile") {
            Picker("Maximum quality", selection: $maximumHeight) {
                Text("Auto").tag(0)
                ForEach([2160, 1080, 720, 480], id: \.self) { Text("\($0)p").tag($0) }
            }.focused($playbackFocus, equals: "quality")
                .foregroundStyle(playbackFocus == "quality" ? Color.black : Color.white)
                .accessibilityIdentifier("settings.maximumQuality")
            Toggle("Data saver", isOn: $dataSaver)
                .focused($playbackFocus, equals: "saver")
                .foregroundStyle(playbackFocus == "saver" ? Color.black : Color.white)
                .accessibilityIdentifier("settings.dataSaver")
            if dataSaver { Text("Requests a lower bitrate to use less data. Available source tracks determine the result.").font(.system(size: 22)).foregroundStyle(.secondary) }
            Toggle("Start playback automatically", isOn: $autoPlay)
                .focused($playbackFocus, equals: "autoplay")
                .foregroundStyle(playbackFocus == "autoplay" ? Color.black : Color.white)
                .accessibilityIdentifier("settings.autoPlay")
            Text("Saved automatically for this profile. Applies when opening a video. Adaptive sources choose an available quality within your limit; a low-resolution source cannot be upgraded.").font(.system(size: 22)).foregroundStyle(.secondary)
        }
    }
}
