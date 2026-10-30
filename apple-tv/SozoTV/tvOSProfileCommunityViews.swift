import SwiftUI

private struct CommunityAck: Decodable {}
private struct CommunityNotification: Decodable, Identifiable {
    let id: String; let title: String; let body: String; let read: Bool
}
private struct CommunityInbox: Decodable { let items: [CommunityNotification]; let totalPages: Int }
private struct CommunityUser: Decodable, Identifiable {
    let id: String; let username: String; let displayName: String?
    let relation: String?; let requestId: String?
    var name: String { displayName ?? username }
}
private struct CommunityFriend: Decodable { let user: CommunityUser }
private struct CommunityFriends: Decodable { let items: [CommunityFriend]; let nextCursor: String? }
private struct CommunityRequest: Decodable, Identifiable { let id: String; let user: CommunityUser }
private struct CommunityRequests: Decodable { let items: [CommunityRequest] }
private struct CommunitySearch: Decodable { let items: [CommunityUser] }

struct NotificationsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var items: [CommunityNotification] = []
    @State private var pagination = InboxPagination()
    @StateObject private var request = ProfileRequestState()
    private var loading: Bool { request.busy }
    private var error: String? { request.error }
    var body: some View {
        List {
            if loading { ProgressView("Loading notifications") }
            if let error { Text(error).foregroundStyle(.red); Button("Retry") { Task { await load(reset: pagination.retryResets) } } }
            if items.isEmpty && !loading && error == nil { Text("No notifications yet.") }
            ForEach(items) { item in
                Button { Task { await markRead(item) } } label: {
                    VStack(alignment: .leading, spacing: 12) {
                        Label(item.title, systemImage: item.read ? "envelope.open" : "envelope.badge").font(.headline)
                        Text(item.body).foregroundStyle(.secondary)
                        if !item.read { Text("Mark as read").font(.caption) }
                    }.padding(.vertical, 12)
                }.disabled(loading).accessibilityIdentifier("notifications.item.\(item.id)").accessibilityHint(item.read ? "Read notification" : "Mark this notification as read")
            }
            if pagination.hasMore { Button("Load more") { Task { await load(reset: false) } }.disabled(loading) }
            if let notice = request.notice { Label(notice, systemImage: "checkmark.circle").foregroundStyle(.green) }
            Button("Refresh") { Task { await load(reset: true) } }.disabled(loading).accessibilityIdentifier("notifications.refresh")
        }.navigationTitle("Notifications")
            .task(id: session.profileScopeIdentity) { items = []; pagination.reset(); request.notice = nil; await load(reset: true) }
            .onDisappear { request.cancel() }
    }
    @MainActor private func load(reset: Bool) async {
        let next = pagination.begin(reset: reset)
        await request.run {
            let value: CommunityInbox = try await session.request("notifications", query: [.init(name: "page", value: String(next))]); return value
        } apply: { result in
            var seen = Set<String>()
            items = ((reset ? [] : items) + result.items).filter { seen.insert($0.id).inserted }
            pagination.succeeded(page: next, totalPages: result.totalPages)
        }
    }
    @MainActor private func markRead(_ item: CommunityNotification) async {
        guard !loading, !item.read else { return }
        pagination.prepareRefreshRetry()
        await request.run {
            let updated: CommunityNotification = try await session.request("notifications/\(item.id)/read", method: "POST"); return updated
        } apply: { updated in
            if let index = items.firstIndex(where: { $0.id == item.id }) { items[index] = updated }
            request.notice = "Marked as read"
        }
    }
}

struct FriendsView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var friends: [CommunityFriend] = []
    @State private var incoming: [CommunityRequest] = []
    @State private var outgoing: [CommunityRequest] = []
    @State private var results: [CommunityUser] = []
    @State private var query = ""
    @State private var searched = false
    @State private var cursor: String?
    @StateObject private var request = ProfileRequestState()
    private var loading: Bool { request.busy }
    private var error: String? { request.error }
    @State private var removal: CommunityUser?
    @State private var retryOperation = FriendsRetryOperation.refresh
    private var isKids: Bool { session.activeProfile?.isKids == true }
    init() {
        #if DEBUG
        if let seed = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--friends-query=") }) {
            _query = State(initialValue: String(seed.dropFirst("--friends-query=".count)))
        }
        #endif
    }
    var body: some View {
        Group {
            if isKids { ContentUnavailableView("Friends are unavailable in kids profiles", systemImage: "person.crop.circle.badge.checkmark") }
            else {
                List {
                    if loading { ProgressView("Updating friends") }
                    if let notice = request.notice { Label(notice, systemImage: "checkmark.circle").foregroundStyle(.green) }
                    if let error { Text(error).foregroundStyle(.red); Button("Retry") { Task { await retry() } } }
                    Section("Find friends") {
                        TextField("Username", text: $query).onChange(of: query) { _, _ in results = []; searched = false }.onSubmit { Task { await search() } }.accessibilityIdentifier("friends.search")
                        Button("Search") { Task { await search() } }.disabled(loading || query.trimmingCharacters(in: .whitespacesAndNewlines).count < 2).accessibilityIdentifier("friends.search.submit")
                        if searched && results.isEmpty && !loading { Text("No matching users.").foregroundStyle(.secondary) }
                        ForEach(results) { user in
                            HStack {
                                VStack(alignment: .leading) { Text(user.name); Text("@\(user.username) · \(relationship(user.relation))").font(.caption).foregroundStyle(.secondary) }
                                Spacer()
                                if user.relation == "none" { Button("Add friend") { Task { await mutate("social/requests", body: ["userId": user.id], notice: "Friend request sent") } } }
                            }
                        }
                    }
                    Section("Incoming requests") {
                        if incoming.isEmpty && !loading { Text("No incoming requests.").foregroundStyle(.secondary) }
                        ForEach(incoming) { request in
                            HStack { Text(request.user.name); Spacer(); Button("Accept") { Task { await mutate("social/requests/\(request.id)/accept", notice: "Friend request accepted") } }; Button("Decline") { Task { await mutate("social/requests/\(request.id)", method: "DELETE") } } }
                        }
                    }
                    Section("Friends") {
                        if friends.isEmpty && !loading { Text("No friends yet.").foregroundStyle(.secondary) }
                        ForEach(friends, id: \.user.id) { friend in
                            HStack { Text(friend.user.name); Spacer(); Button("Remove") { removal = friend.user } }
                        }
                        if cursor != nil { Button("Load more friends") { Task { await more() } }.accessibilityIdentifier("friends.loadMore") }
                    }
                    Section("Sent requests") {
                        if outgoing.isEmpty && !loading { Text("No pending requests.").foregroundStyle(.secondary) }
                        ForEach(outgoing) { request in HStack { Text(request.user.name); Spacer(); Button("Cancel request") { Task { await mutate("social/requests/\(request.id)", method: "DELETE") } } } }
                    }
                    Button("Refresh") { Task { await load() } }
                }.disabled(loading)
            }
        }.navigationTitle("Friends")
            .task(id: session.profileScopeIdentity) { friends = []; incoming = []; outgoing = []; results = []; searched = false; removal = nil; cursor = nil; request.notice = nil; await load() }
            .onDisappear { request.cancel() }
            .confirmationDialog("Remove \(removal?.name ?? "friend") from your friends?", isPresented: Binding(get: { removal != nil }, set: { if !$0 { removal = nil } }), titleVisibility: .visible) {
                if let user = removal { Button("Remove friend", role: .destructive) { Task { await mutate("social/friends/\(user.id)", method: "DELETE") }; removal = nil } }
                Button("Cancel", role: .cancel) { removal = nil }
            }
    }
    @MainActor private func retry() async {
        switch retryOperation {
        case .refresh: await load()
        case .search: await search()
        case .more: await more()
        }
    }
    private func relationship(_ value: String?) -> String {
        switch value {
        case "friends": return "Friends"
        case "outgoing", "pending_out": return "Request sent"
        case "incoming", "pending_in": return "Wants to connect"
        default: return "Sozo member"
        }
    }
    @MainActor private func load() async {
        guard !isKids else { request.cancel(); return }
        retryOperation = .refresh
        await request.run {
            async let f: CommunityFriends = session.request("social/friends")
            async let i: CommunityRequests = session.request("social/requests", query: [.init(name: "dir", value: "in")])
            async let o: CommunityRequests = session.request("social/requests", query: [.init(name: "dir", value: "out")])
            return try await (f, i, o)
        } apply: { f, i, o in friends = f.items; cursor = f.nextCursor; incoming = i.items; outgoing = o.items }
    }
    @MainActor private func search() async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !isKids, !loading, text.count >= 2 else { return }
        retryOperation = .search
        results = []; searched = false
        await request.run {
            let value: CommunitySearch = try await session.request("social/users/search", query: [.init(name: "q", value: text)]); return value
        } apply: { value in
            guard text == query.trimmingCharacters(in: .whitespacesAndNewlines) else { return }
            results = value.items; searched = true
        }
    }
    @MainActor private func more() async {
        guard !isKids, !loading, let cursor else { return }
        retryOperation = .more
        await request.run {
            let value: CommunityFriends = try await session.request("social/friends", query: [.init(name: "cursor", value: cursor)]); return value
        } apply: { value in
            var seen = Set<String>()
            friends = (friends + value.items).filter { seen.insert($0.user.id).inserted }
            self.cursor = value.nextCursor == cursor ? nil : value.nextCursor
        }
    }
    @MainActor private func mutate(_ path: String, method: String = "POST", body: [String: Any]? = nil, notice: String = "Friends updated") async {
        guard !isKids, !loading else { return }
        retryOperation = .refresh
        await request.run {
            let _: CommunityAck = try await session.request(path, method: method, body: body)
            try Task.checkCancellation()
            async let f: CommunityFriends = session.request("social/friends")
            async let i: CommunityRequests = session.request("social/requests", query: [.init(name: "dir", value: "in")])
            async let o: CommunityRequests = session.request("social/requests", query: [.init(name: "dir", value: "out")])
            return try await (f, i, o)
        } apply: { f, i, o in
            friends = f.items; cursor = f.nextCursor; incoming = i.items; outgoing = o.items
            results = []; searched = false; request.notice = notice
        }
    }
}
