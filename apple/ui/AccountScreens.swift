import SwiftUI
import Shared

// The account as the server sees it: profile, password, default visibility, tokens,
// webhooks, notifications and statistics. All online only, since they are the server's
// records rather than the phone's, and each screen says so when it cannot reach it.

/// Profile fields, the password, and the server-side default visibility.
struct ProfileView: View {
    @ObservedObject var model: SessionModel
    @State private var profile: ProfileRow?
    @State private var displayName = ""
    @State private var email = ""
    @State private var description = ""
    @State private var defaultVisibility = "PRIVATE"
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var saved = false
    @State private var failure: String?

    var body: some View {
        Form {
            if let profile {
                Section("Profile") {
                    LabeledContent("Username", value: profile.username)
                    TextField("Display name", text: $displayName)
                    TextField("Email", text: $email)
                    TextField("About you", text: $description, axis: .vertical).lineLimit(2...4)
                    Button("Save profile") {
                        Task {
                            do {
                                try await model.session.updateProfile(displayName: displayName, description: description, email: email)
                                model.displayName = displayName.isEmpty ? profile.username : displayName
                                saved = true
                            } catch { failure = model.readableMessage(error) }
                        }
                    }
                    .disabled(displayName == profile.displayName && email == profile.email && description == profile.description)
                }

                Section {
                    Picker("New memos are", selection: $defaultVisibility) {
                        Text("Private").tag("PRIVATE")
                        Text("Protected").tag("PROTECTED")
                        Text("Public").tag("PUBLIC")
                    }
                    .onChange(of: defaultVisibility) { _, value in
                        Task { try? await model.session.setDefaultVisibility(visibility: value) }
                    }
                } header: {
                    Text("Default visibility")
                } footer: {
                    Text("Kept on the server, so the web app and every device start new memos the same way.")
                        .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                }

                Section {
                    SecureField("New password", text: $newPassword)
                    SecureField("Again", text: $confirmPassword)
                    Button("Change password") {
                        Task {
                            do {
                                try await model.session.changePassword(newPassword: newPassword)
                                newPassword = ""
                                confirmPassword = ""
                                saved = true
                            } catch { failure = model.readableMessage(error) }
                        }
                    }
                    .disabled(newPassword.count < 6 || newPassword != confirmPassword)
                } header: {
                    Text("Password")
                } footer: {
                    Text("Other devices keep their tokens; only new sign-ins need the new password.")
                        .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                }
            } else if let failure {
                Section { Label(failure, systemImage: "wifi.slash").foregroundStyle(Theme.danger) }
            } else {
                Section { ProgressView() }
            }
        }
        .formStyle(.grouped)
        .alert("Saved", isPresented: $saved) { Button("OK") {} }
        .alert("Could not save", isPresented: Binding(get: { failure != nil && profile != nil }, set: { if !$0 { failure = nil } })) {
            Button("OK") { failure = nil }
        } message: { Text(failure ?? "") }
        .task {
            do {
                profile = try await model.session.profile()
                displayName = profile?.displayName ?? ""
                email = profile?.email ?? ""
                description = profile?.description ?? ""
                defaultVisibility = try await model.session.defaultVisibility()
            } catch { failure = model.readableMessage(error) }
        }
    }
}

/// Personal access tokens. The one this device minted is marked, and a new token's value is
/// shown once, because the server never shows it again.
struct TokensView: View {
    @ObservedObject var model: SessionModel
    @State private var tokens: [TokenRow] = []
    @State private var creating = false
    @State private var description = ""
    @State private var expiresInDays = 90
    @State private var minted: String?
    @State private var failure: String?

    var body: some View {
        List {
            if let failure { Label(failure, systemImage: "wifi.slash").foregroundStyle(Theme.danger) }
            ForEach(tokens, id: \.name) { token in
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(token.description.isEmpty ? "Token" : token.description).font(Type.rowTitle)
                        if token.thisDevice {
                            Text("this device").font(Type.rowMeta).foregroundStyle(Theme.accent)
                        }
                    }
                    Text("Created \(token.createdLabel) · expires \(token.expiresLabel) · last used \(token.lastUsedLabel)")
                        .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                }
                .swipeActions {
                    Button("Revoke", role: .destructive) {
                        Task { try? await model.session.deleteToken(name: token.name); await load() }
                    }
                }
            }
        }
        .overlay { if tokens.isEmpty && failure == nil { EmptyState(icon: "key", title: "No tokens") } }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { creating = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $creating) {
            VStack(alignment: .leading, spacing: 14) {
                Text("New token").font(Type.heading3)
                TextField("What it is for", text: $description).textFieldStyle(.roundedBorder)
                Picker("Expires", selection: $expiresInDays) {
                    Text("In 30 days").tag(30)
                    Text("In 90 days").tag(90)
                    Text("In a year").tag(365)
                    Text("Never").tag(0)
                }
                HStack {
                    Spacer()
                    Button("Cancel") { creating = false }
                    Button("Create") {
                        Task {
                            minted = try? await model.session.createToken(description: description, expiresInDays: Int32(expiresInDays))
                            description = ""
                            creating = false
                            await load()
                        }
                    }
                    .keyboardShortcut(.defaultAction)
                }
            }
            .padding(20)
            .sheetWidth(400)
            .presentationDetents([.medium])
        }
        .alert("Copy this token now", isPresented: Binding(get: { minted != nil }, set: { if !$0 { minted = nil } })) {
            Button("Copy") {
                #if os(iOS)
                UIPasteboard.general.string = minted
                #else
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(minted ?? "", forType: .string)
                #endif
                minted = nil
            }
            Button("Close", role: .cancel) { minted = nil }
        } message: {
            Text((minted ?? "") + "\n\nThe server will not show it again.")
        }
        .task { await load() }
    }

    private func load() async {
        do { tokens = try await model.session.tokens(); failure = nil } catch { failure = model.readableMessage(error) }
    }
}

struct WebhooksView: View {
    @ObservedObject var model: SessionModel
    @State private var webhooks: [WebhookRow] = []
    @State private var creating = false
    @State private var name = ""
    @State private var url = ""
    @State private var failure: String?

    var body: some View {
        List {
            if let failure { Label(failure, systemImage: "wifi.slash").foregroundStyle(Theme.danger) }
            ForEach(webhooks, id: \.name) { hook in
                VStack(alignment: .leading, spacing: 3) {
                    Text(hook.displayName.isEmpty ? "Webhook" : hook.displayName).font(Type.rowTitle)
                    Text(hook.url).font(Type.rowMeta).foregroundStyle(Theme.inkSoft).lineLimit(1).truncationMode(.middle)
                    Text("Added \(hook.createdLabel)").font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                }
                .swipeActions {
                    Button("Delete", role: .destructive) {
                        Task { try? await model.session.deleteWebhook(name: hook.name); await load() }
                    }
                }
            }
        }
        .overlay { if webhooks.isEmpty && failure == nil { EmptyState(icon: "arrow.up.forward.app", title: "No webhooks", detail: "The server calls a webhook whenever a memo changes.") } }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { creating = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $creating) {
            VStack(alignment: .leading, spacing: 14) {
                Text("New webhook").font(Type.heading3)
                TextField("Name", text: $name).textFieldStyle(.roundedBorder)
                TextField("https://", text: $url).textFieldStyle(.roundedBorder)
                    #if os(iOS)
                    .keyboardType(.URL).textInputAutocapitalization(.never)
                    #endif
                HStack {
                    Spacer()
                    Button("Cancel") { creating = false }
                    Button("Add") {
                        Task {
                            try? await model.session.createWebhook(displayName: name, url: url)
                            name = ""; url = ""
                            creating = false
                            await load()
                        }
                    }
                    .disabled(url.isEmpty)
                }
            }
            .padding(20)
            .sheetWidth(400)
            .presentationDetents([.medium])
        }
        .task { await load() }
    }

    private func load() async {
        do { webhooks = try await model.session.webhooks(); failure = nil } catch { failure = model.readableMessage(error) }
    }
}

/// What the server has to tell you: comments and reactions on your memos, mostly.
struct NotificationsView: View {
    @ObservedObject var model: SessionModel
    @State private var notifications: [NotificationRow] = []
    @State private var failure: String?

    var body: some View {
        List {
            if let failure { Label(failure, systemImage: "wifi.slash").foregroundStyle(Theme.danger) }
            ForEach(notifications, id: \.name) { item in
                Button {
                    Task {
                        if item.unread { try? await model.session.markNotificationRead(name: item.name) }
                        await model.openRemote(item.memoRemoteName)
                        await load()
                    }
                } label: {
                    HStack(alignment: .top, spacing: 10) {
                        Circle().fill(item.unread ? Theme.accent : .clear).frame(width: 8, height: 8).padding(.top, 6)
                        VStack(alignment: .leading, spacing: 3) {
                            Text("\(item.sender) · \(item.type)").font(Type.rowTitle)
                            if !item.relatedSnippet.isEmpty {
                                Text(item.relatedSnippet).font(Type.rowBody).foregroundStyle(Theme.ink).lineLimit(2)
                            }
                            if !item.memoSnippet.isEmpty {
                                Text("on “\(item.memoSnippet)”").font(Type.rowMeta).foregroundStyle(Theme.inkSoft).lineLimit(1)
                            }
                            Text(item.dateLabel).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                        }
                    }
                }
                .buttonStyle(.plain)
                .swipeActions {
                    Button("Delete", role: .destructive) {
                        Task { try? await model.session.deleteNotification(name: item.name); await load() }
                    }
                }
            }
        }
        .overlay { if notifications.isEmpty && failure == nil { EmptyState(icon: "bell", title: "Nothing new") } }
        .task { await load() }
    }

    private func load() async {
        do {
            notifications = try await model.session.notifications()
            failure = nil
            model.unreadNotifications = notifications.filter(\.unread).count
        } catch { failure = model.readableMessage(error) }
    }
}

struct StatsView: View {
    @ObservedObject var model: SessionModel
    @State private var stats: StatsRow?
    @State private var failure: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let stats {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 130), spacing: 10)], spacing: 10) {
                        figure("Memos", stats.totalMemos)
                        figure("Days with writing", stats.activeDays)
                        figure("Tasks", stats.todos)
                        figure("Still open", stats.undone)
                        figure("With links", stats.links)
                        figure("With code", stats.code)
                    }
                    if !stats.tagCounts.isEmpty {
                        Text("TAGS").font(Type.label).tracking(0.7).foregroundStyle(Theme.inkSoft.opacity(0.8))
                        ForEach(stats.tagCounts, id: \.tag) { count in
                            HStack {
                                TagChip(tag: count.tag, style: model.tagStyles[count.tag])
                                Spacer()
                                Text("\(count.count)").font(Type.rowMeta).monospacedDigit().foregroundStyle(Theme.inkSoft)
                            }
                        }
                    }
                } else if let failure {
                    Label(failure, systemImage: "wifi.slash").foregroundStyle(Theme.danger)
                } else {
                    ProgressView()
                }
            }
            .padding(16)
            .frame(maxWidth: Theme.readingWidth, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.canvas)
        .task {
            do { stats = try await model.session.stats() } catch { failure = model.readableMessage(error) }
        }
    }

    private func figure(_ name: String, _ value: Int32) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(value)").font(Type.numeral).foregroundStyle(Theme.accent)
            Text(name).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
    }
}

/// Every account signed in on this device, and a way to add another or switch.
struct AccountsSection: View {
    @ObservedObject var model: SessionModel
    @State private var adding = false

    var body: some View {
        Section {
            ForEach(model.accounts, id: \.id) { account in
                Button {
                    guard !account.active else { return }
                    Task { await model.switchAccount(account.id) }
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(account.displayName).font(Type.rowTitle).foregroundStyle(Theme.ink)
                            Text("\(account.username) · \(account.serverUrl)").font(Type.rowMeta).foregroundStyle(Theme.inkSoft).lineLimit(1)
                        }
                        Spacer()
                        if account.active { Image(systemName: "checkmark").foregroundStyle(Theme.accent) }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            Button("Add another account…") { adding = true }
        } header: {
            Text("Accounts")
        } footer: {
            Text("Several accounts, on the same server or different ones, can be signed in at once. Only one is shown at a time.")
                .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
        }
        .sheet(isPresented: $adding) {
            SignInView(model: model, embedded: true)
        }
        .task { await model.loadAccounts() }
    }
}
