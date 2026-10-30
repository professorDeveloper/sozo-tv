import SwiftUI

struct ProfileSupportView: View {
    @EnvironmentObject private var session: SessionStore
    @StateObject private var request = ProfileRequestState()
    @State private var tickets: [ProfileJSON] = []
    @State private var category = "playback"
    @State private var message = ""
    @State private var pagination = InboxPagination()
    @State private var confirm = false
    private var draft: String { message.trimmingCharacters(in: .whitespacesAndNewlines) }
    var body: some View {
        Form {
            Section("New request") {
                Picker("Category", selection: $category) { ForEach(["playback", "account", "content", "bug", "idea", "other"], id: \.self) { Text($0.capitalized).tag($0) } }.disabled(request.busy)
                TextField("Describe the problem", text: $message).disabled(request.busy).accessibilityIdentifier("support.message")
                Text("\(draft.count) / 4,000 characters").font(.caption).foregroundStyle(draft.count > 4000 ? .red : .secondary)
                Text("Only your message and category are sent to Sozo support.").foregroundStyle(.secondary)
                Button("Send request") { confirm = true }.disabled(request.busy || draft.isEmpty || draft.count > 4000).accessibilityIdentifier("support.send")
            }
            if let notice = request.notice { Label(notice, systemImage: "checkmark.circle").foregroundStyle(.green) }
            if let error = request.error { ProfileErrorView(message: error) { Task { await load(more: !pagination.retryResets) } }.disabled(request.busy) }
            if request.busy { ProgressView("Updating support requests") }
            Section("My requests") {
                ForEach(tickets, id: \.identity) { ticket in
                    NavigationLink { ProfileSupportThreadView(id: ticket.identity) } label: {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(ticket["preview"].string.isEmpty ? "Support request" : ticket["preview"].string).lineLimit(3)
                            Text("\(ticket["category"].string.capitalized) · \(ticket["status"].string.capitalized)").foregroundStyle(.secondary)
                            if ticket["userUnread"].bool { Label("New reply", systemImage: "circle.fill").font(.caption).foregroundStyle(SozoTheme.accent) }
                        }.padding(.vertical, 12)
                    }.accessibilityIdentifier("support.ticket.\(ticket.identity)")
                }
                if tickets.isEmpty && !request.busy && request.error == nil { Label("No support requests yet", systemImage: "bubble.left.and.bubble.right") }
                if pagination.hasMore { Button("Load more") { Task { await load(more: true) } }.disabled(request.busy).accessibilityIdentifier("support.loadMore") }
                Button("Refresh requests") { Task { await load() } }.disabled(request.busy).accessibilityIdentifier("support.refresh")
            }
        }.navigationTitle("Help & support").task { await load() }.onDisappear { request.cancel() }
        .onChange(of: message) { _, value in if !value.isEmpty { request.notice = nil } }
        .confirmationDialog("Send this request to Sozo support?", isPresented: $confirm) {
            Button("Send") { Task { await send() } }; Button("Cancel", role: .cancel) {}
        }
    }
    private func load(more: Bool = false) async {
        let requested = pagination.begin(reset: !more)
        await request.run {
            let result: ProfileJSON = try await session.request("support/tickets", query: [.init(name: "page", value: String(requested))], profileScoped: false)
            return result
        } apply: { data in
            var seen = Set<String>()
            tickets = ((more ? tickets : []) + data["items"].array).filter { !$0.identity.isEmpty && seen.insert($0.identity).inserted }
            pagination.succeeded(page: requested, totalPages: Int(exactly: data["totalPages"].number.rounded(.towardZero)) ?? 1)
        }
    }
    private func send() async {
        guard !request.busy, !draft.isEmpty, draft.count <= 4000 else { return }
        let text = draft, kind = category
        pagination.prepareRefreshRetry()
        await request.run {
            let value: ProfileJSON = try await session.request("support/tickets", method: "POST", body: ["category": kind, "message": text], profileScoped: false)
            return value["ticket"]
        } apply: { ticket in
            message = ""; request.notice = "Your request was sent. Replies appear here."
            if !ticket.identity.isEmpty { tickets.removeAll { $0.identity == ticket.identity }; tickets.insert(ticket, at: 0) }
        }
    }
}

struct ProfileSupportThreadView: View {
    let id: String
    @EnvironmentObject private var session: SessionStore
    @StateObject private var request = ProfileRequestState()
    @State private var ticket: ProfileJSON = .null
    @State private var reply = ""
    @State private var closeConfirmation = false
    private var loaded: Bool { !ticket.identity.isEmpty }
    private var draft: String { reply.trimmingCharacters(in: .whitespacesAndNewlines) }
    var body: some View {
        Form {
            if loaded {
                Section(ticket["status"].string.capitalized) {
                    ForEach(ticket["messages"].array.indices, id: \.self) { i in
                        let message = ticket["messages"].array[i]
                        VStack(alignment: .leading, spacing: 10) {
                            Text(message["from"].string == "admin" ? "Sozo support" : "You").font(.headline)
                            Text(message["body"].string)
                            ForEach(message["attachments"].array.indices, id: \.self) { j in
                                let attachment = message["attachments"].array[j]
                                AsyncImage(url: URL(string: attachment["url"].string)) { image in image.resizable().scaledToFit() } placeholder: { Image(systemName: "photo") }.frame(maxWidth: 720, maxHeight: 320)
                            }
                            Text(message["at"].string.prefix(10)).foregroundStyle(.secondary).font(.caption)
                        }.padding(.vertical, 16).focusable()
                    }
                }
                if ticket["status"].string != "closed" {
                    Section("Reply") {
                        TextField("Message", text: $reply).disabled(request.busy).accessibilityIdentifier("support.reply")
                        Text("\(draft.count) / 4,000 characters").font(.caption).foregroundStyle(draft.count > 4000 ? .red : .secondary)
                        Button("Send reply") { Task { await send() } }.disabled(request.busy || draft.isEmpty || draft.count > 4000)
                        Button("Close request", role: .destructive) { closeConfirmation = true }.disabled(request.busy)
                    }
                } else { Label("This request is closed", systemImage: "checkmark.seal") }
            }
            if let notice = request.notice { Label(notice, systemImage: "checkmark.circle").foregroundStyle(.green) }
            if let error = request.error { ProfileErrorView(message: error) { Task { await load() } }.disabled(request.busy) }
            if request.busy { ProgressView("Updating conversation") }
            Button("Refresh conversation") { Task { await load() } }.disabled(request.busy)
        }.navigationTitle("Support conversation").task(id: id) { await load() }.onDisappear { request.cancel() }
        .onChange(of: reply) { _, value in if !value.isEmpty { request.notice = nil } }
        .confirmationDialog("Close this support request?", isPresented: $closeConfirmation) {
            Button("Close request", role: .destructive) { Task { await mutate("close", body: [:], notice: "Request closed") } }; Button("Cancel", role: .cancel) {}
        }
    }
    private func load() async {
        await request.run {
            let value: ProfileJSON = try await session.request("support/tickets/\(id)", profileScoped: false); return value["ticket"]
        } apply: { ticket = $0 }
    }
    private func send() async {
        guard !draft.isEmpty, draft.count <= 4000 else { return }
        await mutate("messages", body: ["message": draft], notice: "Reply sent")
    }
    private func mutate(_ action: String, body: [String: Any], notice: String) async {
        guard !request.busy, loaded, ticket["status"].string != "closed" else { return }
        await request.run {
            let value: ProfileJSON = try await session.request("support/tickets/\(id)/\(action)", method: "POST", body: body, profileScoped: false); return value["ticket"]
        } apply: { ticket = $0; if action == "messages" { reply = "" }; request.notice = notice }
    }
}
