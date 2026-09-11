import SwiftUI
import Shared

/// Settings. On the Mac this is the cmd-comma window; on iOS it is pushed from the timeline.
/// The same form on both, because the choices are the same choices.
struct SettingsView: View {
    @ObservedObject var model: SessionModel
    @State private var confirmingSignOut = false

    var body: some View {
        Form {
            Section("Account") {
                LabeledContent("Signed in as", value: model.displayName.isEmpty ? "—" : model.displayName)
                LabeledContent("Server", value: model.accounts.first { $0.active }?.serverUrl ?? model.server)
                LabeledContent("Memos version", value: model.serverVersion.isEmpty ? "—" : model.serverVersion)
                #if os(iOS)
                NavigationLink("Profile, password and default visibility") { ProfileView(model: model).navigationTitle("Profile") }
                NavigationLink("Notifications") { NotificationsView(model: model).navigationTitle("Notifications") }
                NavigationLink("Statistics") { StatsView(model: model).navigationTitle("Statistics") }
                NavigationLink("Access tokens") { TokensView(model: model).navigationTitle("Access tokens") }
                NavigationLink("Webhooks") { WebhooksView(model: model).navigationTitle("Webhooks") }
                #endif
            }

            AccountsSection(model: model)

            #if os(iOS)
            Section("Your data") {
                NavigationLink("Export, import, backup and restore") { DataView(model: model).navigationTitle("Your data") }
            }

            Section("Editor") {
                NavigationLink("Templates") { TemplatesView(model: model).navigationTitle("Templates") }
                NavigationLink("Tags") { TagsView(model: model).navigationTitle("Tags") }
                NavigationLink("Shortcuts") { ShortcutsView(model: model).navigationTitle("Shortcuts") }
            }
            #endif

            Section("Appearance") {
                Picker("Theme", selection: Binding(
                    get: { model.appearance },
                    set: { model.appearance = $0 }
                )) {
                    ForEach(Appearance.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
            }

            Section {
                Toggle("Sort by when memos were last changed", isOn: Binding(
                    get: { model.sortByModified },
                    set: { enabled in Task { await model.setSortByModified(enabled) } }
                ))
                Toggle("Compact list", isOn: Binding(
                    get: { model.compactList },
                    set: { enabled in Task { await model.setCompactList(enabled) } }
                ))
                Toggle("Sink completed tasks on save", isOn: Binding(
                    get: { model.sortCompletedTasks },
                    set: { enabled in Task { await model.setSortCompletedTasks(enabled) } }
                ))
            } header: {
                Text("Timeline")
            } footer: {
                Text("A compact list gives each memo one line: its title, the time, and whether it is pinned or locked. Good for finding something in a long timeline.")
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.inkSoft)
            }

            Section {
                Toggle("Show map previews", isOn: Binding(
                    get: { model.mapTiles },
                    set: { enabled in Task { await model.setMapTiles(enabled) } }
                ))
            } header: {
                Text("Places")
            } footer: {
                Text("Off by default. Drawing a map asks openstreetmap.org for the tiles around a memo, which tells them roughly where it was written. With this off nothing is drawn and nothing leaves this device."
                    + Platform.locationNote)
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.inkSoft)
            }

            Section {
                Toggle("Sunday evening digest", isOn: Binding(
                    get: { model.weeklyDigest },
                    set: { enabled in Task { await model.setWeeklyDigest(enabled) } }
                ))
                if model.notificationsDenied {
                    Text("Notifications are turned off for MyMemos in \(Platform.settingsAppName), so reminders and the digest only appear when the app is open.")
                        .font(Type.rowMeta)
                        .foregroundStyle(Theme.warm)
                } else if !model.notificationsAllowed {
                    Button("Allow Notifications") { Task { await model.requestNotificationPermission() } }
                }
            } header: {
                Text("Reminders")
            } footer: {
                Text("Reminders and recurring templates are kept with your account, so they are the same on every device you sign in on. A recurring memo is written the next time this device opens the app after its time.")
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.inkSoft)
            }

            Section("Locked memos") {
                LabeledContent("Memo password") {
                    HStack {
                        Text(model.passwordRemembered ? "Remembered on this device" : "Asked for each time")
                            .foregroundStyle(Theme.inkSoft)
                        if model.passwordRemembered {
                            Button("Forget") { model.forgetPassword() }
                        }
                    }
                }
            }

            #if os(iOS)
            if model.isAdmin {
                Section("Administration") {
                    NavigationLink("Users") { AdminUsersView(model: model).navigationTitle("Users") }
                    NavigationLink("Instance") { AdminInstanceView(model: model).navigationTitle("Instance") }
                }
            }
            #endif

            Section {
                Button("Sign Out…", role: .destructive) { confirmingSignOut = true }
                    .disabled(model.phase == .signedOut)
            } footer: {
                Text("Signing out revokes this device's token on the server and removes the local copy of your memos.")
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.inkSoft)
            }
        }
        .formStyle(.grouped)
        #if os(macOS)
        .frame(width: 460)
        #endif
        .confirmationDialog("Sign out of MyMemos?", isPresented: $confirmingSignOut) {
            Button("Sign Out", role: .destructive) { Task { await model.signOut() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This device's token is revoked on the server and the local memos are removed. Nothing on the server is deleted.")
        }
    }
}
