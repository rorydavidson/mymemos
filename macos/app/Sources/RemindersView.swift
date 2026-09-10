import SwiftUI
import Shared

/// The reminders waiting to go off, and the weekly digest switch.
///
/// Reminders live in the config memo, so this list is the same one the phone shows. Setting
/// one here means the phone will ring too, and whichever device fires first clears it for
/// both.
struct RemindersView: View {
    @ObservedObject var model: SessionModel
    let open: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if model.notificationsDenied {
                    Callout(
                        symbol: "bell.slash",
                        text: "Notifications are turned off for MyMemos. Reminders still show when you open the app; System Settings › Notifications is where to turn them back on."
                    )
                } else if !model.notificationsAllowed {
                    Callout(symbol: "bell.badge", text: "MyMemos has not asked to send notifications yet.") {
                        Button("Allow Notifications") {
                            Task { await model.requestNotificationPermission() }
                        }
                        .controlSize(.small)
                    }
                }

                if !model.reminders.isEmpty {
                    section("Waiting")
                    ForEach(model.reminders, id: \.id) { reminder in
                        ReminderCard(
                            reminder: reminder,
                            open: { if !reminder.memoLocalId.isEmpty { open(reminder.memoLocalId) } },
                            remove: { Task { await model.removeReminder(reminder.id) } }
                        )
                    }
                }

                section("Weekly digest")
                DigestCard(model: model)
            }
            .padding(16)
            .frame(maxWidth: 620, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.canvas)
        .overlay(alignment: .center) {
            if model.reminders.isEmpty && model.notificationsAllowed && !model.weeklyDigest {
                EmptyState(
                    icon: "bell",
                    title: "Nothing to remind you of",
                    detail: "Open a memo and choose Remind Me to set one. Reminders follow you to your phone."
                )
            }
        }
        .task { await model.loadSchedules() }
    }

    private func section(_ title: String) -> some View {
        Text(title.uppercased())
            .font(Type.label)
            .tracking(0.8)
            .foregroundStyle(Theme.inkSoft.opacity(0.7))
            .padding(.top, 4)
    }
}

private struct ReminderCard: View {
    let reminder: ReminderRow
    let open: () -> Void
    let remove: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: reminder.overdue ? "bell.badge.fill" : "bell")
                .font(.system(size: 13))
                .foregroundStyle(reminder.overdue ? Theme.warm : Theme.accent)
                .padding(.top, 1)

            VStack(alignment: .leading, spacing: 3) {
                Text(reminder.note.isEmpty ? title : reminder.note)
                    .font(Type.rowTitle)
                    .foregroundStyle(Theme.ink)
                    .lineLimit(2)
                if !reminder.note.isEmpty && !title.isEmpty {
                    Text(title).font(Type.rowMeta).foregroundStyle(Theme.inkSoft).lineLimit(1)
                }
                Text(reminder.overdue ? "\(reminder.whenLabel) — due" : reminder.whenLabel)
                    .font(Type.rowMeta)
                    .foregroundStyle(reminder.overdue ? Theme.warm : Theme.inkSoft)
            }

            Spacer(minLength: 0)

            Button("Clear", action: remove).buttonStyle(.link).font(Type.rowMeta)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Theme.cardRadius).fill(Theme.card))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cardRadius)
                .strokeBorder(Theme.hairline.opacity(0.8), lineWidth: 0.5)
        )
        .contentShape(Rectangle())
        .onTapGesture(perform: open)
    }

    /// A memo this Mac has not pulled down yet has no title to show.
    private var title: String {
        reminder.memoTitle.isEmpty ? "A memo on the server" : reminder.memoTitle
    }
}

private struct DigestCard: View {
    @ObservedObject var model: SessionModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Toggle("Sunday evening summary", isOn: Binding(
                get: { model.weeklyDigest },
                set: { enabled in Task { await model.setWeeklyDigest(enabled) } }
            ))
            .toggleStyle(.switch)
            .font(Type.rowTitle)

            Text("What you wrote, tasks you closed, your streak, and a few old memos worth reading again. Worked out on this Mac from memos already downloaded; nothing is sent anywhere.")
                .font(Type.rowMeta)
                .foregroundStyle(Theme.inkSoft)
                .fixedSize(horizontal: false, vertical: true)

            if model.weeklyDigest && !model.digestNext.isEmpty {
                Text("Next \(model.digestNext)")
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.accent)
            }

            // The week so far, whether or not the digest is switched on. Waiting until Sunday
            // to find out what it would say is a poor way to decide whether you want it.
            if !model.digestPreview.isEmpty {
                Divider().padding(.vertical, 2)
                Text(model.weeklyDigest ? "This week so far" : "This is what it would say")
                    .font(Type.label)
                    .tracking(0.8)
                    .foregroundStyle(Theme.inkSoft.opacity(0.7))
                Text(model.digestPreview)
                    .font(Type.rowBody)
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Theme.cardRadius).fill(Theme.card))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cardRadius)
                .strokeBorder(Theme.hairline.opacity(0.8), lineWidth: 0.5)
        )
    }
}

/// A notice with an optional action, for the things the app cannot do on its own.
struct Callout<Action: View>: View {
    let symbol: String
    let text: String
    @ViewBuilder var action: () -> Action

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbol).foregroundStyle(Theme.warm)
            Text(text)
                .font(Type.rowBody)
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            action()
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Theme.cardRadius).fill(Theme.warm.opacity(0.10)))
    }
}

extension Callout where Action == EmptyView {
    init(symbol: String, text: String) {
        self.init(symbol: symbol, text: text, action: { EmptyView() })
    }
}

/// Setting a reminder on a memo: when, and optionally what to say instead of its title.
struct ReminderSheet: View {
    @ObservedObject var model: SessionModel
    let memoLocalId: String

    @Environment(\.dismiss) private var dismiss
    @State private var date = Date().addingTimeInterval(60 * 60)
    @State private var note = ""
    @State private var failure: String?
    @State private var saving = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Remind me").font(Type.heading3)
                Spacer()
                Button("Cancel") { dismiss() }
                Button("Set") { Task { await set() } }
                    .keyboardShortcut(.defaultAction)
                    .disabled(saving || date <= Date())
            }
            .padding(14)

            Divider()

            VStack(alignment: .leading, spacing: 12) {
                DatePicker("When", selection: $date, in: Date()...)
                    .datePickerStyle(.compact)

                TextField("What to say (optional)", text: $note)
                    .textFieldStyle(.roundedBorder)

                if let failure {
                    Text(failure).font(Type.rowMeta).foregroundStyle(Theme.danger)
                }

                Text("Kept with the memo, so your phone will remind you too. Whichever device shows it first clears it for both.")
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
        }
        .frame(width: 400)
        .background(Theme.canvas)
    }

    private func set() async {
        saving = true
        defer { saving = false }
        if await model.addReminder(memoLocalId, at: date, note: note.trimmingCharacters(in: .whitespaces)) {
            dismiss()
        } else {
            failure = "This memo has not reached the server yet. Sync, then try again."
        }
    }
}
