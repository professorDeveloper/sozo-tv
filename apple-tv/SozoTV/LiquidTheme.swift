import SwiftUI

/// Static ambient light; focus animates controls, never a screen-sized blur.
struct SozoBackdrop: View {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @AppStorage("sozo.tv.ambientColor") private var ambientColor = true
    var body: some View {
        ZStack {
            SozoTheme.background
            if ambientColor && !reduceTransparency {
                LinearGradient(colors: [.white.opacity(0.065), .clear, .blue.opacity(0.04)], startPoint: .topLeading, endPoint: .bottomTrailing)
            }
        }.ignoresSafeArea().accessibilityHidden(true)
    }
}

/// Nearby controls share a single glass renderer. Reading surfaces use the content material.
struct SozoGlassGroup<Content: View>: View {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @AppStorage("sozo.tv.glassEnabled") private var enabled = true
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    @ViewBuilder var body: some View {
        if #available(tvOS 26.0, *), enabled && !reduceTransparency {
            GlassEffectContainer(spacing: 18) { content }
        } else { content }
    }
}

private struct SozoGlassSurface: ViewModifier {
    let cornerRadius: CGFloat
    var interactive = false
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @AppStorage("sozo.tv.glassEnabled") private var enabled = true
    @ViewBuilder func body(content: Content) -> some View {
        if #available(tvOS 26.0, *), enabled && !reduceTransparency {
            content.glassEffect(.regular.interactive(interactive), in: RoundedRectangle(cornerRadius: cornerRadius))
        } else {
            content.background(Color(white: 0.15), in: RoundedRectangle(cornerRadius: cornerRadius))
                .overlay(RoundedRectangle(cornerRadius: cornerRadius).strokeBorder(.white.opacity(0.14), lineWidth: 1))
        }
    }
}

extension View {
    func sozoGlassSurface(cornerRadius: CGFloat = 28) -> some View {
        modifier(SozoGlassSurface(cornerRadius: cornerRadius))
    }
    func sozoContentSurface(cornerRadius: CGFloat = 24) -> some View {
        background(Color(white: 0.12), in: RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(RoundedRectangle(cornerRadius: cornerRadius).strokeBorder(.white.opacity(0.08), lineWidth: 1))
    }
}

struct SozoProfileButtonStyle: ButtonStyle {
    @Environment(\.isFocused) private var focused
    @Environment(\.isEnabled) private var enabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(Color.white.opacity(enabled ? 1 : 0.45))
            .background(focused ? Color.white.opacity(0.16) : .clear, in: RoundedRectangle(cornerRadius: 24))
            .modifier(SozoGlassSurface(cornerRadius: 24, interactive: true))
            .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(focused ? .white : .clear, lineWidth: 3))
            .shadow(color: .black.opacity(focused ? 0.3 : 0), radius: 16, y: 8)
            .scaleEffect(focused && !reduceMotion ? 1.025 : 1)
            .opacity(configuration.isPressed ? 0.8 : 1)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.15), value: focused)
    }
}

struct SozoActionLabel: View {
    let title: String
    var symbol: String? = nil
    var body: some View {
        HStack(spacing: 12) {
            if let symbol { Image(systemName: symbol) }
            Text(title).lineLimit(2)
        }.font(.system(size: 24, weight: .semibold))
            .frame(minHeight: 36).padding(.horizontal, 24).padding(.vertical, 14)
    }
}

struct SozoEmptyState: View {
    let title: String
    let message: String
    var symbol = "rectangle.stack"
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: symbol).font(.system(size: 40, weight: .light)).foregroundStyle(.secondary)
            Text(title).font(.system(size: 30, weight: .semibold))
            Text(message).font(.system(size: 22)).foregroundStyle(.secondary)
                .multilineTextAlignment(.center).frame(maxWidth: 720)
        }.frame(maxWidth: .infinity).padding(40).sozoContentSurface()
            .accessibilityElement(children: .combine)
    }
}

struct SozoLoadingState: View {
    var title = "Loading…"
    var body: some View {
        HStack(spacing: 18) { ProgressView(); Text(title).font(.system(size: 22)).foregroundStyle(.secondary) }
            .frame(maxWidth: .infinity).padding(32).accessibilityElement(children: .combine)
    }
}
