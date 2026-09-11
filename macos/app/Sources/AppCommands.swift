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
            Menu("New from Template") {
                if model.templates.isEmpty {
                    Text("No templates")
                } else {
                    ForEach(model.templates, id: \.id) { template in
                        Button(template.title) { model.newFromTemplate(template) }
                    }
                }
            }
            .disabled(model.phase == .signedOut)

            Divider()
            Button("Sync Now") { Task { await model.sync() } }
                .keyboardShortcut("r")
                .disabled(model.isBusy || model.phase == .signedOut)
            Button("Sync Status…") { model.showingSyncStatus = true }
                .keyboardShortcut("r", modifiers: [.command, .shift])
                .disabled(model.phase == .signedOut)
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

            Button(model.showArchived ? "Unarchive" : "Archive") {
                guard let memo = model.selectedMemo else { return }
                Task { await model.setArchived(memo.localId, !model.showArchived) }
            }
            .keyboardShortcut("a", modifiers: [.command, .shift])
            .disabled(model.selectedMemo == nil)

            Divider()

            Button("Attach a File…") { Task { await attach() } }
                .disabled(model.selectedMemo == nil)

            Button("Remind Me…") { model.settingReminderFor = model.selectedMemo?.localId }
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
            Button("All Memos") { Task { await model.showAllMemos() } }
                .keyboardShortcut("1", modifiers: .command)
            Button("Tasks") { model.pane = .tasks }
                .keyboardShortcut("2", modifiers: .command)
            Button("Review") { model.pane = .review }
                .keyboardShortcut("3", modifiers: .command)
            Button("Reminders") { model.pane = .reminders }
                .keyboardShortcut("4", modifiers: .command)
            Button("Templates") { model.pane = .templates }
                .keyboardShortcut("5", modifiers: .command)
            Button("Archive") { Task { await model.showArchive(true) } }
                .keyboardShortcut("6", modifiers: .command)
            Button("Shortcuts") { model.pane = .shortcuts }
                .keyboardShortcut("7", modifiers: .command)
            Button("Tags") { model.pane = .tags }
                .keyboardShortcut("8", modifiers: .command)
            Button("Notifications") { model.pane = .notifications }
                .keyboardShortcut("9", modifiers: .command)

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
