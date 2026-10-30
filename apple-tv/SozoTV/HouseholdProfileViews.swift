import SwiftUI

/// Numeric controls work with the Siri Remote without exposing PINs in text fields.
struct ProfilePinView: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    let subtitle: String
    var requiredLength: Int? = nil
    var actionTitle = "Unlock"
    let submit: (String) async throws -> Void
    @State private var pin = ""
    @State private var busy = false
    @State private var failure: String?
    @FocusState private var focused: Int?
    // 0...8: digits 1...9; 9: erase, 10: zero, 11: cancel, 12: submit.
    private let labels = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "Delete", "0", "Cancel"]
    var body: some View {
        VStack(spacing: 18) {
            Text(title).font(.system(size: 38, weight: .bold)).lineLimit(1)
            Text(subtitle).font(.system(size: 24)).foregroundStyle(.secondary)
                .multilineTextAlignment(.center).frame(maxWidth: 640)
            Text(pin.isEmpty ? "Enter PIN" : String(repeating: "●", count: pin.count))
                .font(.system(size: 30, weight: .medium, design: .monospaced))
                .frame(height: 40).accessibilityLabel("\(pin.count) digits entered")
                .accessibilityIdentifier("profile.pin.count")
            Text("\(requiredLength.map(String.init) ?? "4–8") digits · \(actionTitle) or press Play/Pause")
                .font(.system(size: 20)).foregroundStyle(.secondary)
            SozoGlassGroup {
            VStack(spacing: 16) {
                ForEach(0..<4, id: \.self) { row in
                    HStack(spacing: 16) {
                        ForEach(0..<3, id: \.self) { column in
                            let index = row * 3 + column
                            Button { activate(index) } label: {
                                Text(labels[index]).font(.system(size: index < 9 || index == 10 ? 32 : 22, weight: .semibold))
                                    .frame(width: 160, height: 72)
                            }.buttonStyle(SozoProfileButtonStyle())
                                .focused($focused, equals: index)
                                .accessibilityIdentifier(identifier(index))
                        }
                    }
                }
                Button { verify() } label: {
                    Label(actionTitle, systemImage: "checkmark")
                        .font(.system(size: 26, weight: .semibold)).frame(width: 160, height: 68)
                        .opacity(canSubmit ? 1 : 0.45)
                }.buttonStyle(SozoProfileButtonStyle()).focused($focused, equals: 12)
                    .accessibilityIdentifier("profile.pin.submit")
                    .accessibilityHint(canSubmit ? actionTitle : "\(requiredLength ?? 4) digits required")
            }
            }
            ZStack {
                if busy { ProgressView("Verifying…") }
                else if let failure { Text(failure).foregroundStyle(.red).lineLimit(2) }
            }.font(.system(size: 21)).frame(height: 52)
        }.padding(36).sozoContentSurface(cornerRadius: 32)
            .interactiveDismissDisabled(busy)
            .onAppear { focused = 0 }
            .onPlayPauseCommand { verify() }
            .onExitCommand { if !busy { dismiss() } }
    }
    private var canSubmit: Bool { requiredLength.map { pin.count == $0 } ?? (pin.count >= 4) }
    private func identifier(_ index: Int) -> String {
        if index == 9 { return "profile.pin.delete" }
        if index == 11 { return "profile.pin.cancel" }
        return "profile.pin.digit.\(index == 10 ? 0 : index + 1)"
    }
    private func activate(_ index: Int) {
        guard !busy else { return }
        if index == 11 { dismiss(); return }
        failure = nil
        if index == 9 { if !pin.isEmpty { pin.removeLast() }; return }
        if pin.count < (requiredLength ?? 8) { pin += String(index == 10 ? 0 : index + 1) }
    }
    private func verify() {
        guard !busy, canSubmit else { return }
        busy = true; failure = nil
        Task {
            do { try await submit(pin); busy = false }
            catch { busy = false; failure = error.localizedDescription; pin = ""; focused = 0 }
        }
    }
}

struct ManageProfilesView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var editing: HouseholdProfile?
    @State private var creating = false
    @State private var switching = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                Text("Manage profiles").font(.largeTitle.bold())
                Button { switching = true } label: { SozoActionLabel(title: "Switch profile", symbol: "person.2") }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("profile.switch")
                Text("A personal profile, kids mode and PIN for everyone.").font(.system(size: 22)).foregroundStyle(.secondary)
                if session.activeProfile?.isKids == true {
                    Text("Switch to an adult profile to manage profiles.")
                } else {
                    ForEach(session.profiles) { profile in
                        Button { editing = profile } label: {
                            HStack(spacing: 24) {
                                ProfileAvatar(profile: profile, size: 74)
                                VStack(alignment: .leading) {
                                    Text(profile.name).font(.system(size: 30, weight: .semibold)).lineLimit(2)
                                    Text(profile.isDefault ? "Default profile" : profile.isKids ? "Kids profile" : "Personal profile")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if profile.hasPin { Image(systemName: "lock.fill") }
                                Image(systemName: "pencil")
                            }.padding(16)
                        }.buttonStyle(SozoProfileButtonStyle()).disabled(session.busy)
                            .accessibilityIdentifier("profile.edit.\(profile.id)")
                    }
                    Button { creating = true } label: { SozoActionLabel(title: "New profile", symbol: "person.badge.plus") }.buttonStyle(SozoProfileButtonStyle()).disabled(!session.canAdd || session.busy)
                        .accessibilityIdentifier("profile.create")
                    if !session.canAdd && session.profiles.count >= session.maxProfiles {
                        Text("Your household has reached its profile limit. Edit or remove an existing profile to make room.").font(.system(size: 22)).foregroundStyle(.secondary)
                    }
                    Text("\(session.profiles.count) / \(session.maxProfiles) profiles").font(.caption).foregroundStyle(.secondary)
                }
                if session.busy { ProgressView() }
                if let error = session.error { Text(error).foregroundStyle(.red) }
                Button { Task { await session.loadProfiles() } } label: { SozoActionLabel(title: "Refresh", symbol: "arrow.clockwise") }.buttonStyle(SozoProfileButtonStyle()).disabled(session.busy)
            }.frame(maxWidth: 1280, alignment: .leading).padding(64)
        }
        .background { SozoBackdrop() }
        .sheet(isPresented: $switching) { ProfilePickerView { switching = false } }
        .sheet(item: $editing) { HouseholdProfileEditor(profile: $0) }
        .sheet(isPresented: $creating) { HouseholdProfileEditor(profile: nil) }
    }
}

struct ProfileSecurityView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var editing = false
    var body: some View {
        VStack(alignment: .leading, spacing: 28) {
            Text("Profile security").font(.largeTitle.bold())
            if let profile = session.activeProfile {
                Label(profile.hasPin ? "This profile is PIN protected" : "No PIN is set for this profile", systemImage: profile.hasPin ? "lock.fill" : "lock.open")
                Text("A PIN is checked when opening a profile. The default profile can reset other members’ PINs.").font(.system(size: 22)).foregroundStyle(.secondary)
                if !profile.isKids {
                    Button { editing = true } label: { SozoActionLabel(title: "Edit profile and PIN", symbol: "lock") }.buttonStyle(SozoProfileButtonStyle())
                } else { Text("Switch to an adult profile to change security settings.") }
            }
        }.padding(70).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background { SozoBackdrop() }
        .sheet(isPresented: $editing) { if let profile = session.activeProfile { HouseholdProfileEditor(profile: profile) } }
    }
}

struct HouseholdProfileEditor: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    let profile: HouseholdProfile?
    @State private var name: String
    @State private var avatar: String?
    @State private var color: String
    @State private var kids: Bool
    @State private var pinMode = 0
    @FocusState private var nativeControlFocus: String?
    private enum PINEntry: String, Identifiable { case new, confirmation; var id: String { rawValue } }
    @State private var pinEntry: PINEntry?
    @State private var newPin = ""
    @State private var confirmPin = ""
    @State private var failure: String?
    @State private var busy = false
    @State private var confirmDelete = false
    @State private var askingPin = false
    @State private var deleting = false
    private let colorNames = ["Red", "Amber", "Green", "Blue", "Purple", "Pink", "Teal", "Orange"]
    private let colors = ["#e50914", "#f5a623", "#2ecc71", "#1e88e5", "#8e44ad", "#e91e63", "#00acc1", "#ff7043"]
    private let avatars = (1...16).map { "lorelei-\($0)" } + (1...16).map { "open-peeps-\($0)" } + (1...8).map { "notionists-\($0)" } + (1...4).map { "thumbs-\($0)" }
    init(profile: HouseholdProfile?) {
        self.profile = profile
        _name = State(initialValue: profile?.name ?? "")
        _avatar = State(initialValue: profile?.avatar ?? "lorelei-1")
        _color = State(initialValue: profile?.color ?? "#e50914")
        _kids = State(initialValue: profile?.isKids ?? false)
    }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 25) {
                Text(profile == nil ? "New profile" : "Edit profile").font(.largeTitle.bold())
                HStack(spacing: 28) {
                    editorAvatar
                    VStack(alignment: .leading, spacing: 10) {
                        Text(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Your profile" : name)
                            .font(.system(size: 34, weight: .semibold)).lineLimit(2)
                        Text(kids ? "Kids profile" : "Personal profile").foregroundStyle(.secondary)
                    }
                }.padding(.bottom, 10)
                TextField("Profile name", text: $name)
                    .accessibilityIdentifier("profile.editor.name")
                Text("Avatar").font(.system(size: 28, weight: .semibold))
                ScrollView(.horizontal) {
                    HStack(spacing: 18) {
                        Button { avatar = nil } label: {
                            VStack(spacing: 10) { Text(String(name.prefix(1)).uppercased()).font(.system(size: 38, weight: .bold)).frame(width: 76, height: 76); Text("Initial").font(.system(size: 18)) }
                                .padding(12)
                                .overlay(RoundedRectangle(cornerRadius: 18).stroke(avatar == nil ? .white : .clear, lineWidth: 3))
                        }.buttonStyle(SozoProfileButtonStyle()).accessibilityLabel("Use name initial")
                        ForEach(avatars, id: \.self) { id in
                            Button { avatar = id } label: {
                                Group {
                                    if let image = ProfileArtwork.image(named: id) {
                                        Image(uiImage: image).resizable().scaledToFit()
                                    } else { Image(systemName: "person.crop.square").resizable().scaledToFit() }
                                }.frame(width: 96, height: 96).padding(12)
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(avatar == id ? Color.white.opacity(0.9) : .clear, lineWidth: 4))
                            }.buttonStyle(SozoProfileButtonStyle())
                                .accessibilityLabel("Avatar \(id.replacingOccurrences(of: "-", with: " "))")
                                .accessibilityAddTraits(avatar == id ? [.isSelected] : [])
                        }
                    }.padding(.horizontal, 20).padding(.vertical, 24)
                        .focusSection()
                }.scrollIndicators(.hidden)
                HStack {
                    Text("Color")
                    ForEach(colors, id: \.self) { value in
                        Button { color = value } label: {
                            Circle().fill(Color(hex: value) ?? .red).frame(width: 40, height: 40)
                                .overlay(Image(systemName: color == value ? "checkmark" : "").foregroundStyle(.white))
                        }.accessibilityLabel(colorNames[colors.firstIndex(of: value) ?? 0])
                            .accessibilityAddTraits(color == value ? [.isSelected] : [])
                    }
                }
                Toggle("Kids profile", isOn: $kids).disabled(profile?.isDefault == true)
                    .focused($nativeControlFocus, equals: "kids")
                    .foregroundStyle(nativeControlFocus == "kids" ? Color.black : Color.white)
                    .accessibilityIdentifier("profile.editor.kids")
                if profile?.isDefault == true { Text("The default profile cannot be deleted or changed into a kids profile.").font(.system(size: 22)).foregroundStyle(.secondary) }
                Picker("PIN protection", selection: $pinMode) {
                    Text(profile?.hasPin == true ? "Keep current PIN" : "No PIN set").tag(0)
                    Text("Set a new PIN").tag(1)
                    if profile?.hasPin == true { Text("Remove PIN").tag(2) }
                }.focused($nativeControlFocus, equals: "pin")
                    .foregroundStyle(nativeControlFocus == "pin" ? Color.black : Color.white)
                    .accessibilityIdentifier("profile.editor.pinMode")
                if pinMode == 1 {
                    Button(newPin.isEmpty ? "New PIN — 4 digits" : "New PIN: ●●●●") { pinEntry = .new }
                        .accessibilityIdentifier("profile.editor.newPin")
                    Button(confirmPin.isEmpty ? "Confirm PIN" : "Confirm PIN: ●●●●") { pinEntry = .confirmation }
                        .accessibilityIdentifier("profile.editor.confirmPin")
                }
                if let failure { Text(failure).foregroundStyle(.red) }
                if busy { ProgressView() }
                SozoGlassGroup {
                HStack(spacing: 24) {
                    Button { begin(delete: false) } label: { SozoActionLabel(title: "Save", symbol: "checkmark") }.buttonStyle(SozoProfileButtonStyle()).disabled(session.activeProfile?.isKids == true)
                        .accessibilityIdentifier("profile.editor.save")
                    Button { dismiss() } label: { SozoActionLabel(title: "Cancel", symbol: "xmark") }.buttonStyle(SozoProfileButtonStyle()).accessibilityIdentifier("profile.editor.cancel")
                    if let profile, !profile.isDefault { Button(role: .destructive) { confirmDelete = true } label: { SozoActionLabel(title: "Delete profile", symbol: "trash") }.buttonStyle(SozoProfileButtonStyle()).disabled(session.activeProfile?.isKids == true).accessibilityIdentifier("profile.editor.delete") }
                }
                }
            }.disabled(busy).frame(maxWidth: 1380, alignment: .leading).padding(60)
        }
        .background { SozoBackdrop() }
        .interactiveDismissDisabled(busy)
        .confirmationDialog("Delete this profile and its saved data?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { begin(delete: true) }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(item: $pinEntry) { field in
            ProfilePinView(title: field == .new ? "New PIN" : "Confirm PIN", subtitle: "Enter 4 digits with your remote", requiredLength: 4, actionTitle: "Save") { value in
                if field == .new { newPin = value; confirmPin = "" } else { confirmPin = value }
                pinEntry = nil
            }
        }
        .sheet(isPresented: $askingPin) {
            ProfilePinView(title: "Current PIN", subtitle: "Enter this profile’s PIN to confirm the change") { pin in
                try await persist(currentPin: pin)
                askingPin = false
                dismiss()
            }
        }
    }
    private var editorAvatar: some View {
        Group {
            if let avatar, let image = ProfileArtwork.image(named: avatar) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ZStack {
                    Color(hex: color) ?? .gray
                    Text(String(name.prefix(1)).uppercased()).font(.system(size: 52, weight: .bold))
                        .foregroundStyle(ProfileColorContrast.usesDarkForeground(color) ? Color.black : Color.white)
                }
            }
        }.frame(width: 120, height: 120).clipShape(.rect(cornerRadius: 28)).accessibilityHidden(true)
    }
    private func begin(delete: Bool) {
        guard !busy, session.canManage else { return }
        failure = nil
        if !delete {
            let trimmed = name.split(whereSeparator: \.isWhitespace).joined(separator: " ")
            guard !trimmed.isEmpty, trimmed.count <= 30 else { failure = "Enter a profile name between 1 and 30 characters."; return }
            name = trimmed
            if pinMode == 1 && (newPin.count != 4 || !newPin.allSatisfy({ $0.isASCII && $0.isNumber }) || newPin != confirmPin) {
                failure = "Enter the same 4-digit PIN twice."; return
            }
        }
        deleting = delete
        let sensitive = delete || pinMode != 0 || kids != profile?.isKids
        if let profile, profile.hasPin, sensitive, !(session.activeProfile?.isDefault == true && !profile.isDefault) {
            askingPin = true; return
        }
        busy = true
        Task {
            defer { busy = false }
            do { try await persist(currentPin: nil); dismiss() }
            catch { failure = error.localizedDescription }
        }
    }
    private func persist(currentPin: String?) async throws {
        if deleting, let profile { try await session.deleteProfile(id: profile.id, currentPin: currentPin); return }
        let draft = ProfileDraft(name: name, avatar: avatar, color: color, isKids: kids, pin: pinMode == 1 ? newPin : pinMode == 2 ? "" : nil)
        if let profile { _ = try await session.updateProfile(id: profile.id, draft: draft, currentPin: currentPin) }
        else { _ = try await session.createProfile(draft) }
    }
}
