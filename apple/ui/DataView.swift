import SwiftUI
import UniformTypeIdentifiers
import Shared
#if os(macOS)
import AppKit
#endif

/// Export, import, backup and restore: the same four things the Android app's Data screen
/// offers, on the same file formats, so a zip or a backup moves between devices.
struct DataView: View {
    @ObservedObject var model: SessionModel

    @State private var busy = false
    @State private var message: String?
    @State private var passwordFor: PasswordJob?
    #if os(iOS)
    @State private var importing = false
    @State private var choosingBackup = false
    @State private var movingFile: URL?
    #endif

    enum PasswordJob: Identifiable {
        case backup, restore(String)
        var id: String { switch self { case .backup: return "backup"; case .restore: return "restore" } }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                card("Export Markdown", "square.and.arrow.up",
                     "A zip of .md files with front matter, plus local attachments. Works in Obsidian.") { export() }
                card("Import Markdown", "square.and.arrow.down",
                     "Pick one or more .md files, or a zip of them. Dates come from the files, and folders inside a zip become tags.") { pickImport() }
                card("Back up", "lock.doc",
                     "Memos, attachments and settings, AES-256 with a password of your choosing. Sign-in details are not included. Readable by the Android app too.") { passwordFor = .backup }
                card("Restore", "arrow.counterclockwise.circle",
                     "Replaces all local data with a backup and relaunches the app. You will need to sign in again.") { pickBackup() }

                if busy { ProgressView().padding(.top, 4) }
                if let message {
                    Text(message).font(Type.rowBody).foregroundStyle(Theme.ink)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Theme.accentSoft.opacity(0.5), in: RoundedRectangle(cornerRadius: 8))
                        .textSelection(.enabled)
                }
            }
            .padding(16)
            .frame(maxWidth: Theme.readingWidth, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.canvas)
        .sheet(item: $passwordFor) { job in
            BackupPasswordSheet(job: job) { password in
                switch job {
                case .backup: backup(password: password)
                case let .restore(path): restore(path: path, password: password)
                }
            }
        }
        #if os(iOS)
        .fileImporter(isPresented: $importing, allowedContentTypes: [.plainText, .zip, .data], allowsMultipleSelection: true) { result in
            guard let urls = try? result.get() else { return }
            run { try await withScopedAccess(urls) { try await model.importMarkdown(urls.map(\.path)) } }
        }
        .fileImporter(isPresented: $choosingBackup, allowedContentTypes: [.data]) { result in
            guard let url = try? result.get() else { return }
            passwordFor = .restore(url.path)
        }
        .fileMover(isPresented: Binding(get: { movingFile != nil }, set: { if !$0 { movingFile = nil } }), file: movingFile) { result in
            if case let .failure(error) = result { message = error.localizedDescription }
            movingFile = nil
        }
        #endif
    }

    private func card(_ title: String, _ symbol: String, _ hint: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: symbol).font(.system(size: 18)).foregroundStyle(Theme.accent).frame(width: 26)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(Type.rowTitle).foregroundStyle(Theme.ink)
                    Text(hint).font(Type.rowMeta).foregroundStyle(Theme.inkSoft).fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
            .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(busy)
    }

    // MARK: The four jobs

    private static var today: String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    private func export() {
        #if os(macOS)
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "memos-\(Self.today).zip"
        panel.allowedContentTypes = [.zip]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        run { try await model.exportMarkdown(to: url.path) }
        #else
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("memos-\(Self.today).zip")
        run {
            let text = try await model.exportMarkdown(to: url.path)
            movingFile = url
            return text
        }
        #endif
    }

    private func pickImport() {
        #if os(macOS)
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.allowedContentTypes = [.plainText, .zip, .data]
        panel.message = "Choose Markdown files, or zips of them"
        guard panel.runModal() == .OK else { return }
        let paths = panel.urls.map(\.path)
        run { try await model.importMarkdown(paths) }
        #else
        importing = true
        #endif
    }

    private func backup(password: String) {
        #if os(macOS)
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "mymemos-\(Self.today).backup"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        run {
            try await model.backup(to: url.path, password: password)
            return "Backup written to \(url.lastPathComponent)"
        }
        #else
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("mymemos-\(Self.today).backup")
        run {
            try await model.backup(to: url.path, password: password)
            movingFile = url
            return "Backup written"
        }
        #endif
    }

    private func pickBackup() {
        #if os(macOS)
        let panel = NSOpenPanel()
        panel.canChooseDirectories = false
        panel.message = "Choose a MyMemos backup"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        passwordFor = .restore(url.path)
        #else
        choosingBackup = true
        #endif
    }

    private func restore(path: String, password: String) {
        run {
            #if os(iOS)
            let url = URL(fileURLWithPath: path)
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            #endif
            try await model.restore(from: path, password: password)
            relaunch()
            return "Restored"
        }
    }

    /// Room cannot reopen a database swapped underneath it, so the process ends. A Mac can
    /// start itself again; a phone cannot, and says so.
    private func relaunch() {
        #if os(macOS)
        let configuration = NSWorkspace.OpenConfiguration()
        configuration.createsNewApplicationInstance = true
        NSWorkspace.shared.openApplication(at: Bundle.main.bundleURL, configuration: configuration) { _, _ in
            DispatchQueue.main.async { exit(0) }
        }
        #else
        let alert = UIAlertController(title: "Restored", message: "MyMemos will now close. Open it again and sign in.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in exit(0) })
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first?.rootViewController?.present(alert, animated: true)
        #endif
    }

    private func run(_ job: @escaping () async throws -> String) {
        busy = true
        message = nil
        Task {
            do {
                message = try await job()
            } catch {
                message = model.readableMessage(error)
            }
            busy = false
        }
    }

    #if os(iOS)
    private func withScopedAccess<T>(_ urls: [URL], _ body: () async throws -> T) async throws -> T {
        let scoped = urls.filter { $0.startAccessingSecurityScopedResource() }
        defer { scoped.forEach { $0.stopAccessingSecurityScopedResource() } }
        return try await body()
    }
    #endif
}

/// The password for a backup, or the one it was made with.
private struct BackupPasswordSheet: View {
    let job: DataView.PasswordJob
    let confirm: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var again = ""

    private var isBackup: Bool { if case .backup = job { return true } else { return false } }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(isBackup ? "Back up" : "Restore").font(Type.heading3)
            Text(isBackup
                 ? "Choose a password of at least 6 characters. There is no way to recover it."
                 : "This replaces all local data and relaunches the app. You will need to sign in again.")
                .font(Type.rowMeta).foregroundStyle(isBackup ? Theme.inkSoft : Theme.warm)
                .fixedSize(horizontal: false, vertical: true)
            SecureField("Password", text: $password).textFieldStyle(.roundedBorder)
            if isBackup {
                SecureField("Again", text: $again).textFieldStyle(.roundedBorder)
            }
            HStack {
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button(isBackup ? "Back up" : "Restore") {
                    let chosen = password
                    dismiss()
                    confirm(chosen)
                }
                .keyboardShortcut(.defaultAction)
                .disabled(password.count < 6 || (isBackup && password != again))
            }
        }
        .padding(20)
        .sheetWidth(400)
        .background(Theme.canvas)
        #if os(iOS)
        .presentationDetents([.medium])
        #endif
    }
}
