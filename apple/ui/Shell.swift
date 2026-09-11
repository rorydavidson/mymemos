import SwiftUI
import Shared

// The pieces of the app's shell that look the same on a Mac and a phone: what a sheet is
// being asked for, the detail pane, and the sign-in form.

// MARK: - Detail

/// What the memo password is being asked for.
struct PasswordRequest: Identifiable {
    enum Purpose {
        case reveal
        case encrypt
        case removeEncryption
    }

    let localId: String
    let purpose: Purpose

    var id: String { "\(localId)-\(purpose)" }
}

/// A memo the menu bar asked to set a reminder on.
struct ReminderTarget: Identifiable {
    let id: String
}

/// Identifiable so a sheet can be driven by it; nil localId means a new memo.
///
/// [initialText] is how a template arrives: already expanded, so the editor does not have to
/// know that templates exist.
struct EditorTarget: Identifiable {
    let localId: String?
    var initialText: String? = nil
    /// Images to attach once the memo exists, which is how a share arrives.
    var initialImages: [URL] = []
    var id: String { localId ?? "new" }
}

struct DetailPane: View {
    @ObservedObject var model: SessionModel
    let selection: String?
    var edit: (String) -> Void = { _ in }
    @State private var detail: MemoDetail?

    var body: some View {
        Group {
            if let detail {
                MemoDetailView(detail: detail, model: model, edit: { edit(detail.row.localId) })
            } else {
                EmptyState(
                    icon: "doc.text",
                    title: "No memo selected",
                    detail: "Pick one from the list. " + Hint.newMemo
                )
                .background(Theme.canvas)
            }
        }
        .task(id: selection) { await load() }
        .task(id: model.sections.count) { await load() }
    }

    private func load() async {
        guard let selection else {
            detail = nil
            return
        }
        detail = await model.detail(for: selection)
    }
}

// MARK: - Sign in

struct SignInView: View {
    @ObservedObject var model: SessionModel
    /// True when presented from Settings to add a second account, in which case success
    /// closes the sheet rather than replacing the whole screen.
    var embedded = false
    @Environment(\.dismiss) private var dismiss
    @State private var knownServers: [String] = []

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 6) {
                    Image(systemName: "text.alignleft")
                        .font(.system(size: 26, weight: .medium))
                        .foregroundStyle(Theme.accent)
                        .padding(10)
                        .background(Theme.accentSoft, in: RoundedRectangle(cornerRadius: 12))
                    Text("MyMemos").font(Type.title)
                    Text("Connect to your Memos server")
                        .font(Type.rowBody).foregroundStyle(Theme.inkSoft)
                }

                VStack(alignment: .leading, spacing: 10) {
                    field("Server", text: $model.server, symbol: "server.rack")
                    // Servers signed into before, so an unwanted sign-out means a tap, not
                    // retyping an address. Only the address is kept.
                    if knownServers.count > 1 || (knownServers.first != nil && knownServers.first != model.server) {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(knownServers, id: \.self) { server in
                                    Button(server.replacingOccurrences(of: "https://", with: "")) { model.server = server }
                                        .font(Type.rowMeta)
                                        .buttonStyle(.bordered)
                                        .controlSize(.small)
                                }
                            }
                        }
                    }
                    field("Username", text: $model.username, symbol: "person")
                    secureField("Password", text: $model.password)
                }

                if let warning = model.credentialWarning {
                    Label(warning, systemImage: "exclamationmark.triangle")
                        .font(Type.rowMeta).foregroundStyle(Theme.warm)
                }
                if case let .failed(message) = model.phase {
                    Label(message, systemImage: "xmark.octagon")
                        .font(Type.rowMeta).foregroundStyle(Theme.danger).textSelection(.enabled)
                }

                HStack {
                    if case let .working(what) = model.phase {
                        ProgressView().controlSize(.small)
                        Text(what).font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                    }
                    Spacer()
                    if embedded {
                        Button("Cancel") { dismiss() }
                    }
                    Button("Sign in") { Task { await submit() } }
                        .keyboardShortcut(.defaultAction)
                        .disabled(model.isBusy || model.username.isEmpty || model.password.isEmpty)
                }
            }
            .padding(32)
            .cardWidth(420)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Theme.hairline))
            .shadow(color: .black.opacity(0.08), radius: 20, y: 8)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.canvas)
        .task { knownServers = await model.knownServers() }
    }

    private func submit() async {
        await model.signIn()
        if embedded, case .failed = model.phase {} else if embedded {
            await model.loadAccounts()
            await model.reload()
            dismiss()
        }
    }

    private func field(_ label: String, text: Binding<String>, symbol: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: symbol).frame(width: 16).foregroundStyle(Theme.inkSoft)
            TextField(label, text: text).textFieldStyle(.roundedBorder)
        }
    }

    private func secureField(_ label: String, text: Binding<String>) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "lock").frame(width: 16).foregroundStyle(Theme.inkSoft)
            SecureField(label, text: text)
                .textFieldStyle(.roundedBorder)
                .onSubmit { Task { await submit() } }
        }
    }
}
