import SwiftUI
import Shared

// For an admin: the instance's users and its general settings. Online only.

struct AdminUsersView: View {
    @ObservedObject var model: SessionModel
    @State private var users: [UserRow] = []
    @State private var creating = false
    @State private var username = ""
    @State private var password = ""
    @State private var admin = false
    @State private var failure: String?

    var body: some View {
        List {
            if let failure { Label(failure, systemImage: "wifi.slash").foregroundStyle(Theme.danger) }
            ForEach(users, id: \.name) { user in
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(user.displayName.isEmpty ? user.username : user.displayName).font(Type.rowTitle)
                        if user.admin { Text("admin").font(Type.rowMeta).foregroundStyle(Theme.accent) }
                    }
                    Text([user.username, user.email].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                }
                .swipeActions {
                    Button("Delete", role: .destructive) {
                        Task { try? await model.session.deleteUser(name: user.name); await load() }
                    }
                    Button("Archive") {
                        Task { try? await model.session.setUserArchived(name: user.name, archived: true); await load() }
                    }
                    .tint(Theme.warm)
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { creating = true } label: { Image(systemName: "person.badge.plus") }
            }
        }
        .sheet(isPresented: $creating) {
            VStack(alignment: .leading, spacing: 14) {
                Text("New user").font(Type.heading3)
                TextField("Username", text: $username).textFieldStyle(.roundedBorder)
                    #if os(iOS)
                    .textInputAutocapitalization(.never)
                    #endif
                SecureField("Password", text: $password).textFieldStyle(.roundedBorder)
                Toggle("Administrator", isOn: $admin)
                HStack {
                    Spacer()
                    Button("Cancel") { creating = false }
                    Button("Create") {
                        Task {
                            do {
                                try await model.session.createUser(username: username, password: password, admin: admin)
                                username = ""; password = ""; admin = false
                                creating = false
                                await load()
                            } catch { failure = model.readableMessage(error); creating = false }
                        }
                    }
                    .disabled(username.isEmpty || password.count < 6)
                }
            }
            .padding(20)
            .sheetWidth(400)
            .presentationDetents([.medium])
        }
        .task { await load() }
    }

    private func load() async {
        do { users = try await model.session.users(); failure = nil } catch { failure = model.readableMessage(error) }
    }
}

struct AdminInstanceView: View {
    @ObservedObject var model: SessionModel
    @State private var general: InstanceRow?
    @State private var stats: InstanceStatsRow?
    @State private var title = ""
    @State private var about = ""
    @State private var disallowRegistration = false
    @State private var disallowPasswordAuth = false
    @State private var disallowChangeUsername = false
    @State private var disallowChangeNickname = false
    @State private var weekStart = 0
    @State private var failure: String?
    @State private var saved = false

    var body: some View {
        Form {
            if let general {
                Section("Instance") {
                    TextField("Title", text: $title)
                    TextField("Description", text: $about, axis: .vertical).lineLimit(2...4)
                    Picker("Week starts on", selection: $weekStart) {
                        Text("Sunday").tag(0)
                        Text("Monday").tag(1)
                        Text("Saturday").tag(-1)
                    }
                }
                Section("Users") {
                    Toggle("Registration closed", isOn: $disallowRegistration)
                    Toggle("Password sign-in off", isOn: $disallowPasswordAuth)
                    Toggle("Usernames fixed", isOn: $disallowChangeUsername)
                    Toggle("Display names fixed", isOn: $disallowChangeNickname)
                }
                Section {
                    Button("Save") {
                        Task {
                            do {
                                try await model.session.updateInstanceGeneral(row: InstanceRow(
                                    title: title, about: about,
                                    disallowRegistration: disallowRegistration, disallowPasswordAuth: disallowPasswordAuth,
                                    disallowChangeUsername: disallowChangeUsername, disallowChangeNickname: disallowChangeNickname,
                                    weekStartDayOffset: Int32(weekStart)
                                ))
                                saved = true
                            } catch { failure = model.readableMessage(error) }
                        }
                    }
                }
                if let stats {
                    Section("Storage") {
                        LabeledContent("Database", value: stats.databaseDriver)
                        LabeledContent("Database size", value: ByteCountFormatter.string(fromByteCount: stats.databaseBytes, countStyle: .file))
                        LabeledContent("Local files", value: ByteCountFormatter.string(fromByteCount: stats.localStorageBytes, countStyle: .file))
                    }
                }
                let _ = general
            } else if let failure {
                Section { Label(failure, systemImage: "wifi.slash").foregroundStyle(Theme.danger) }
            } else {
                Section { ProgressView() }
            }
        }
        .formStyle(.grouped)
        .alert("Saved", isPresented: $saved) { Button("OK") {} }
        .task {
            do {
                general = try await model.session.instanceGeneral()
                if let general {
                    title = general.title
                    about = general.about
                    disallowRegistration = general.disallowRegistration
                    disallowPasswordAuth = general.disallowPasswordAuth
                    disallowChangeUsername = general.disallowChangeUsername
                    disallowChangeNickname = general.disallowChangeNickname
                    weekStart = Int(general.weekStartDayOffset)
                }
                stats = try? await model.session.instanceStats()
            } catch { failure = model.readableMessage(error) }
        }
    }
}
