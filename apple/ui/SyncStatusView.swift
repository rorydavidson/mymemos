import SwiftUI
import Shared

/// Where the app stands with the server: what is waiting, what failed and why, and any edits
/// the merge could not settle. The same surface Android has behind its sync chip.
struct SyncStatusView: View {
    @ObservedObject var model: SessionModel
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var reauthenticating = false
    @State private var reauthFailed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Sync").font(Type.heading3)
                Spacer()
                Button("Sync now") { Task { await model.sync(); await model.loadSyncStatus() } }
                    .disabled(model.isBusy)
                Button("Done") { dismiss() }
            }
            .padding(14)
            Divider()
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let status = model.syncStatus {
                        summary(status)
                        if status.authExpired { reauth }
                    }
                    if !model.failedOps.isEmpty { failed }
                    if !model.conflicts.isEmpty { conflicts }
                }
                .padding(14)
            }
        }
        .sheetWidth(460)
        .frame(minHeight: 300)
        .background(Theme.canvas)
        .task { await model.loadSyncStatus() }
    }

    private func summary(_ status: SyncStatusRow) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            row("Last agreed with the server", status.lastSuccessLabel.isEmpty ? "Not yet" : status.lastSuccessLabel)
            row("Waiting to send", "\(status.pending)")
            row("Failed", "\(status.failed)")
            row("Conflicts", "\(status.conflicts)")
            if let error = status.lastError, !error.isEmpty {
                Text(error).font(Type.rowMeta).foregroundStyle(Theme.danger).textSelection(.enabled)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.cardRadius))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
    }

    /// The server rejected this device's token. The password is never stored, so it has to
    /// be typed again; it mints a new token and is forgotten.
    private var reauth: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Sign in again", systemImage: "person.badge.key").font(Type.rowTitle).foregroundStyle(Theme.warm)
            Text("This device's token has expired or was revoked. Your password mints a new one and is not kept.")
                .font(Type.rowMeta).foregroundStyle(Theme.inkSoft).fixedSize(horizontal: false, vertical: true)
            HStack {
                SecureField("Password", text: $password).textFieldStyle(.roundedBorder)
                Button("Sign in") {
                    reauthenticating = true
                    Task {
                        let ok = await model.reauthenticate(password)
                        reauthFailed = !ok
                        password = ""
                        reauthenticating = false
                        await model.loadSyncStatus()
                    }
                }
                .disabled(password.isEmpty || reauthenticating)
            }
            if reauthFailed {
                Text("That did not work. Check the password and try again.").font(Type.rowMeta).foregroundStyle(Theme.danger)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.warm.opacity(0.1), in: RoundedRectangle(cornerRadius: Theme.cardRadius))
    }

    private var failed: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("FAILED").font(Type.label).tracking(0.7).foregroundStyle(Theme.inkSoft.opacity(0.8))
                Spacer()
                Button("Retry all") { Task { await model.retryFailed() } }.controlSize(.small)
            }
            ForEach(model.failedOps, id: \.id) { op in
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(op.memoTitle.isEmpty ? "A memo" : op.memoTitle).font(Type.rowTitle).lineLimit(1)
                        Spacer()
                        Text("\(op.type) · \(op.attempts) tries").font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                    }
                    if !op.error.isEmpty {
                        Text(op.error).font(Type.rowMeta).foregroundStyle(Theme.danger).lineLimit(3)
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
                .contentShape(Rectangle())
                .onTapGesture { model.open(op.memoLocalId); dismiss() }
            }
        }
    }

    /// Text the three-way merge could not combine is kept as a copy rather than lost.
    private var conflicts: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("CONFLICT COPIES").font(Type.label).tracking(0.7).foregroundStyle(Theme.inkSoft.opacity(0.8))
            Text("An edit made here and one made elsewhere could not be merged. Each copy is kept; keeping one makes it an ordinary memo.")
                .font(Type.rowMeta).foregroundStyle(Theme.inkSoft).fixedSize(horizontal: false, vertical: true)
            ForEach(model.conflicts, id: \.localId) { memo in
                HStack {
                    Text(memo.title).font(Type.rowTitle).lineLimit(1)
                    Spacer()
                    Button("Open") { model.open(memo.localId); dismiss() }.linkButton().font(Type.rowMeta)
                    Button("Keep") { Task { await model.keepConflictCopy(memo.localId) } }.linkButton().font(Type.rowMeta)
                }
                .padding(10)
                .background(Theme.card, in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    private func row(_ name: String, _ value: String) -> some View {
        HStack {
            Text(name).font(Type.rowBody).foregroundStyle(Theme.inkSoft)
            Spacer()
            Text(value).font(Type.rowBody).monospacedDigit()
        }
    }
}
