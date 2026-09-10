import SwiftUI
import Shared

/// The menu bar.
///
/// Every action the app can take lives here, whether or not there is a button for it. That is
/// the Mac convention and it is also what makes an app keyboard-usable and discoverable: a
/// shortcut hidden on a toolbar button is a shortcut nobody finds.
struct AppCommands: Commands {
    @ObservedObject var model: SessionModel

    var body: some Commands {
        // File
        CommandGroup(replacing: .newItem) {
            Button("New Memo") { model.newMemo() }
                .keyboardShortcut("n")
                .disabled(model.phase == .signedOut)
        }

        CommandGroup(after: .newItem) {
            Divider()
            Button("Sync Now") { Task { await model.sync() } }
                .keyboardShortcut("r")
                .disabled(model.isBusy || model.phase == .signedOut)
        }

        // Memo: everything that acts on the one selected.
        CommandMenu("Memo") {
            Button("Edit") { model.editSelected() }
                .keyboardShortcut("e")
                .disabled(model.selectedMemo == nil || model.selectedMemo?.locked == true)

            Button(model.selectedMemo?.pinned == true ? "Unpin" : "Pin") {
                guard let memo = model.selectedMemo else { return }
                Task { await model.setPinned(memo.localId, !memo.pinned) }
            }
            .keyboardShortcut("p", modifiers: [.command, .shift])
            .disabled(model.selectedMemo == nil)

            Divider()

            Button("Attach a File…") { Task { await attach() } }
                .disabled(model.selectedMemo == nil)

            Divider()

            if model.selectedMemo?.locked == true {
                Button("Show Locked Memo") {
                    guard let memo = model.selectedMemo else { return }
                    Task { await model.reveal(memo.localId) }
                }
                .keyboardShortcut("u", modifiers: [.command, .shift])

                Button("Remove Encryption") {
                    guard let memo = model.selectedMemo else { return }
                    Task { await model.unlockForGood(memo.localId) }
                }
            } else {
                Button("Encrypt") {
                    guard let memo = model.selectedMemo else { return }
                    Task { await model.lock(memo.localId) }
                }
                .keyboardShortcut("l", modifiers: [.command, .shift])
                .disabled(model.selectedMemo == nil)
            }

            Divider()

            Button("Delete", role: .destructive) {
                guard let memo = model.selectedMemo else { return }
                Task { await model.delete(memo.localId) }
            }
            .keyboardShortcut(.delete, modifiers: .command)
            .disabled(model.selectedMemo == nil)
        }

        // View
        CommandGroup(after: .toolbar) {
            Divider()
            Picker("Appearance", selection: Binding(
                get: { model.appearance },
                set: { model.appearance = $0 }
            )) {
                ForEach(Appearance.allCases) { Text($0.title).tag($0) }
            }
            Divider()
            Button("All Memos") { model.pane = .memos; model.activeTag = nil }
                .keyboardShortcut("1", modifiers: .command)
            Button("Tasks") { model.pane = .tasks }
                .keyboardShortcut("2", modifiers: .command)
            Button("Review") { model.pane = .review }
                .keyboardShortcut("3", modifiers: .command)

            Divider()

            Toggle("Sort by Last Changed", isOn: Binding(
                get: { model.sortByModified },
                set: { enabled in Task { await model.setSortByModified(enabled) } }
            ))
            .disabled(model.phase == .signedOut)

            Toggle("Compact List", isOn: Binding(
                get: { model.compactList },
                set: { enabled in Task { await model.setCompactList(enabled) } }
            ))
            .keyboardShortcut("c", modifiers: [.command, .option])
            .disabled(model.phase == .signedOut)
        }

        // Help, replacing the stub that points at nothing.
        CommandGroup(replacing: .help) {
            Link("Memos on the web", destination: URL(string: "https://usememos.com")!)
        }
    }

    /// The standard open panel: the app never reaches for a file the user has not chosen.
    private func attach() async {
        guard let memo = model.selectedMemo else { return }
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.message = "Choose files to attach to this memo"
        guard panel.runModal() == .OK else { return }
        await model.attach(memo.localId, urls: panel.urls)
    }
}

/// Settings, reachable on cmd-comma the way every Mac app is.
struct SettingsView: View {
    @ObservedObject var model: SessionModel
    @State private var confirmingSignOut = false

    var body: some View {
        Form {
            Section("Account") {
                LabeledContent("Signed in as", value: model.displayName.isEmpty ? "—" : model.displayName)
                LabeledContent("Server", value: model.server)
                LabeledContent("Memos version", value: model.serverVersion.isEmpty ? "—" : model.serverVersion)
            }

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
                Text("Off by default. Drawing a map asks openstreetmap.org for the tiles around a memo, which tells them roughly where it was written. With this off nothing is drawn and nothing leaves this Mac. No Apple location service is used either way.")
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.inkSoft)
            }

            Section("Locked memos") {
                LabeledContent("Memo password") {
                    HStack {
                        Text(model.passwordRemembered ? "Remembered on this Mac" : "Asked for each time")
                            .foregroundStyle(Theme.inkSoft)
                        if model.passwordRemembered {
                            Button("Forget") { model.forgetPassword() }
                        }
                    }
                }
            }

            Section {
                Button("Sign Out…", role: .destructive) { confirmingSignOut = true }
                    .disabled(model.phase == .signedOut)
            } footer: {
                Text("Signing out revokes this Mac's token on the server and removes the local copy of your memos.")
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.inkSoft)
            }
        }
        .formStyle(.grouped)
        .frame(width: 460)
        .confirmationDialog("Sign out of MyMemos?", isPresented: $confirmingSignOut) {
            Button("Sign Out", role: .destructive) { Task { await model.signOut() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This Mac's token is revoked on the server and the local memos are removed. Nothing on the server is deleted.")
        }
    }
}
