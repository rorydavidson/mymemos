import SwiftUI
import Shared

/// Asks for the memo password.
///
/// One password covers every locked memo, the same as on the phone. It is never sent
/// anywhere: it derives a key that decrypts text the server only ever holds as a scrambled
/// blob.
///
/// The sheet says which job it is asking for and its button names that job, because "Unlock"
/// on a dialog that is about to encrypt something is worse than unhelpful.
struct PasswordSheet: View {
    @ObservedObject var model: SessionModel
    let request: PasswordRequest
    @Environment(\.dismiss) private var dismiss

    @State private var password = ""
    @State private var remember = false
    @State private var working = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 11) {
                Image(systemName: symbol)
                    .font(.system(size: 17))
                    .foregroundStyle(Theme.accent)
                    .frame(width: 36, height: 36)
                    .background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: 9))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(Type.heading3).foregroundStyle(Theme.ink)
                    Text(explanation).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            SecureField("Memo password", text: $password)
                .textFieldStyle(.roundedBorder)
                .font(Type.body)
                .focused($focused)
                .onSubmit { submit() }

            Toggle("Remember on this device", isOn: $remember)
                .font(Type.rowBody)
                .help("Kept in the Keychain, so locked memos open without asking again.")

            if model.wrongPassword {
                Label("That password did not open it.", systemImage: "exclamationmark.triangle")
                    .font(Type.rowMeta).foregroundStyle(Theme.danger)
            }

            Text("Your password never leaves this device. The server only ever holds the scrambled text.")
                .font(Type.rowMeta)
                .foregroundStyle(Theme.inkSoft)
                .fixedSize(horizontal: false, vertical: true)

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button(action: submit) {
                    if working {
                        ProgressView().controlSize(.small)
                    } else {
                        Text(actionName)
                    }
                }
                .keyboardShortcut(.defaultAction)
                .disabled(password.isEmpty || working)
            }
        }
        .padding(22)
        .sheetWidth(400)
        .background(Theme.card)
        .task { focused = true }
    }

    // MARK: What this sheet is for

    private var symbol: String {
        switch request.purpose {
        case .reveal: return "eye"
        case .encrypt: return "lock"
        case .removeEncryption: return "lock.open"
        }
    }

    private var title: String {
        switch request.purpose {
        case .reveal: return "Show this memo"
        case .encrypt: return "Encrypt this memo"
        case .removeEncryption: return "Remove encryption"
        }
    }

    private var explanation: String {
        switch request.purpose {
        case .reveal:
            return "Shown on screen only. It stays encrypted here and on the server."
        case .encrypt:
            return "The server will only ever hold the scrambled text from now on."
        case .removeEncryption:
            return "The memo's text goes back to the server in the clear."
        }
    }

    private var actionName: String {
        switch request.purpose {
        case .reveal: return "Show"
        case .encrypt: return "Encrypt"
        case .removeEncryption: return "Remove"
        }
    }

    private func submit() {
        guard !password.isEmpty else { return }
        working = true
        let entered = password
        password = ""
        Task {
            await model.usePassword(entered, remember: remember)
            working = false
            dismiss()
        }
    }
}

/// Names a locked memo. The title is the one part of a locked memo left readable, on this
/// device, on the server and in notifications, so the sheet says so and starts empty: nothing
/// is shown in the clear unless it is typed here.
///
/// When encrypting with no password held yet, it asks for that too. Handing on to the
/// password sheet instead would mean presenting one sheet as another is dismissed, which
/// SwiftUI does not reliably do.
struct LockTitleSheet: View {
    @ObservedObject var model: SessionModel
    let request: LockTitleRequest
    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var needsPassword = false
    @State private var password = ""
    @State private var remember = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 11) {
                Image(systemName: request.renaming ? "character.cursor.ibeam" : "lock")
                    .font(.system(size: 17))
                    .foregroundStyle(Theme.accent)
                    .frame(width: 36, height: 36)
                    .background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: 9))
                VStack(alignment: .leading, spacing: 2) {
                    Text(request.renaming ? "Rename locked memo" : "Encrypt this memo")
                        .font(Type.heading3).foregroundStyle(Theme.ink)
                    Text("A title is optional. Without one it shows as \u{201C}Locked memo\u{201D}.")
                        .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            TextField("Title (optional)", text: $title)
                .textFieldStyle(.roundedBorder)
                .font(Type.body)
                .focused($focused)
                .onSubmit { submit() }

            if needsPassword {
                SecureField("Memo password", text: $password)
                    .textFieldStyle(.roundedBorder)
                    .font(Type.body)
                    .onSubmit { submit() }
                Toggle("Remember on this device", isOn: $remember)
                    .font(Type.rowBody)
                    .help("Kept in the Keychain, so locked memos open without asking again.")
            }

            Label("The title is not encrypted. The server, search and notifications can all show it.",
                  systemImage: "eye")
                .font(Type.rowMeta)
                .foregroundStyle(Theme.inkSoft)
                .fixedSize(horizontal: false, vertical: true)

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button(request.renaming ? "Save" : "Encrypt", action: submit)
                    .keyboardShortcut(.defaultAction)
                    .disabled(needsPassword && password.isEmpty)
            }
        }
        .padding(22)
        .sheetWidth(400)
        .background(Theme.card)
        .task {
            title = request.current
            needsPassword = !request.renaming && !model.hasPassword
            focused = true
        }
    }

    private func submit() {
        guard !(needsPassword && password.isEmpty) else { return }
        let chosen = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let request = request
        let entered = needsPassword ? password : nil
        let remember = remember
        password = ""
        dismiss()
        Task {
            if let entered { await model.usePassword(entered, remember: remember) }
            if request.renaming {
                await model.setLockedTitle(request.localId, chosen.isEmpty ? nil : chosen)
            } else {
                await model.lock(request.localId, title: chosen.isEmpty ? nil : chosen)
            }
        }
    }
}
