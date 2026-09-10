import SwiftUI
import Shared

/// Asks for the memo password.
///
/// One password covers every locked memo, the same as on the phone. It is never sent anywhere:
/// it derives a key that decrypts text the server only ever holds as a scrambled blob.
struct PasswordSheet: View {
    @ObservedObject var model: SessionModel
    @Environment(\.dismiss) private var dismiss

    @State private var password = ""
    @State private var remember = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 18))
                    .foregroundStyle(Theme.accent)
                    .padding(9)
                    .background(Theme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 9))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Memo password").font(Type.heading3)
                    Text("One password opens every locked memo.")
                        .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                }
            }

            SecureField("Password", text: $password)
                .textFieldStyle(.roundedBorder)
                .focused($focused)
                .onSubmit(submit)

            Toggle("Remember on this Mac", isOn: $remember)
                .font(Type.rowBody)
                .help("Kept in the Keychain, so locked memos open without asking again.")

            if model.wrongPassword {
                Label("That password did not open it.", systemImage: "exclamationmark.triangle")
                    .font(Type.rowMeta).foregroundStyle(Theme.danger)
            }

            Text("Your password never leaves this Mac. The server only ever holds the scrambled text.")
                .font(Type.rowMeta)
                .foregroundStyle(Theme.inkSoft)
                .fixedSize(horizontal: false, vertical: true)

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button("Unlock", action: submit)
                    .keyboardShortcut(.defaultAction)
                    .disabled(password.isEmpty)
            }
        }
        .padding(22)
        .frame(width: 380)
        .background(Theme.card)
        .task { focused = true }
    }

    private func submit() {
        guard !password.isEmpty else { return }
        model.usePassword(password, remember: remember)
        password = ""
        dismiss()
    }
}
