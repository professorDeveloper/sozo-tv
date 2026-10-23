import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        // Keep a concrete root container while sign-in replaces the focused TV screen.
        ZStack {
            if session.token == nil {
                DeviceLinkView()
            } else if session.activeProfile == nil || session.profiles.count > 1 && !chosenThisRun {
                ProfilePickerView { chosenThisRun = true }
            } else {
                HomeView().id(session.activeProfile?.id)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background { SozoBackdrop() }
        .task(id: session.token != nil) {
            if session.token != nil && session.profiles.isEmpty { await session.loadProfiles() }
            if session.token == nil { chosenThisRun = false }
        }
    }

    @State private var chosenThisRun = false
}

struct DeviceLinkView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var code: DeviceCode?
    @State private var message = "Getting a sign-in code…"
    @State private var expiresAt: Date?
    @State private var loading = true
    @State private var attempt = 0
    var body: some View {
        HStack(spacing: 100) {
            VStack(alignment: .leading, spacing: 28) {
                Text("SOZO").font(.system(size: 50, weight: .black)).foregroundStyle(SozoTheme.accent)
                Text("Sign in with your phone").font(.system(size: 52, weight: .bold)).lineLimit(3)
                Text("Your space, on the big screen.").font(.system(size: 27)).foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 22) {
                    signInStep("1", "Scan the QR code with your phone")
                    signInStep("2", "Approve this Apple TV in Sozo")
                    signInStep("3", "Choose a profile and start watching")
                }.padding(.top, 10)
                Text("You can also open sozo.azamov.me/link and enter the code.")
                    .font(.system(size: 22)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            }.frame(width: 570, alignment: .leading)
            VStack(spacing: 26) {
                if let code {
                    QRImage(text: "https://sozo.azamov.me/link/\(code.userCode)")
                        .frame(width: 280, height: 280).padding(14).background(.white, in: RoundedRectangle(cornerRadius: 22))
                    Text(code.userCode).font(.system(size: 38, weight: .bold, design: .monospaced))
                        .accessibilityLabel("Sign-in code \(code.userCode)")
                    Label("Waiting for approval", systemImage: "iphone.and.arrow.forward").font(.system(size: 22)).foregroundStyle(.secondary)
                    if let expiresAt {
                        TimelineView(.periodic(from: .now, by: 1)) { clock in
                            let remaining = max(0, Int(expiresAt.timeIntervalSince(clock.date)))
                            Text("Code expires in \(remaining / 60):\(String(format: "%02d", remaining % 60))")
                                .font(.system(size: 19, design: .monospaced)).foregroundStyle(.secondary)
                        }
                    }
                } else {
                    Image(systemName: loading ? "iphone.and.arrow.forward" : "exclamationmark.circle")
                        .font(.system(size: 48, weight: .light)).foregroundStyle(.secondary)
                    if loading { ProgressView() }
                    Text(message).font(.system(size: 24)).multilineTextAlignment(.center)
                        .accessibilityIdentifier("signin.status")
                }
                Button { attempt += 1 } label: {
                    SozoActionLabel(title: code == nil ? "Try again" : "Get a new code", symbol: "arrow.clockwise")
                }.buttonStyle(SozoProfileButtonStyle()).disabled(loading).accessibilityIdentifier("signin.retry")
            }.frame(width: 380).frame(minHeight: 510).padding(40).sozoContentSurface(cornerRadius: 36)
        }.padding(70).task(id: attempt) { await start() }
    }
    private func signInStep(_ number: String, _ text: String) -> some View {
        HStack(spacing: 18) {
            Text(number).font(.system(size: 21, weight: .semibold)).frame(width: 38, height: 38)
                .background(.white.opacity(0.1), in: Circle())
            Text(text).font(.system(size: 24)).fixedSize(horizontal: false, vertical: true)
        }
    }
    private func start() async {
        code = nil; expiresAt = nil; loading = true; message = "Getting a sign-in code…"
        defer { loading = false }
        do {
            let value = try await session.createDeviceCode()
            try Task.checkCancellation()
            code = value; expiresAt = Date().addingTimeInterval(TimeInterval(max(1, value.expiresIn))); loading = false
            for _ in 0..<max(1, value.expiresIn / max(value.interval, 1)) {
                try await Task.sleep(for: .seconds(max(value.interval, 1)))
                let result = try await session.pollDeviceToken(value.deviceCode)
                try Task.checkCancellation()
                if result.accessToken != nil { try await session.accept(result); return }
                if result.status == "denied" { code = nil; message = "Sign-in was declined. Get a new code to try again."; return }
                if result.status == "expired" { break }
            }
            code = nil; message = "This code has expired. Get a new code to continue."
        } catch is CancellationError {} catch { if !Task.isCancelled { code = nil; message = error.localizedDescription } }
    }
}

private struct QRImage: View {
    let image: CGImage?
    init(text: String) {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        if let output = filter.outputImage {
            let scaled = output.transformed(by: .init(scaleX: 10, y: 10))
            image = context.createCGImage(scaled, from: scaled.extent)
        } else { image = nil }
    }
    var body: some View {
        if let image { Image(decorative: image, scale: 1).resizable().interpolation(.none) }
        else { Color.white }
    }
}

import CoreImage
import CoreImage.CIFilterBuiltins
