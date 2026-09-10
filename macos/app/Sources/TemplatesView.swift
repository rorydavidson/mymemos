import SwiftUI
import Shared

/// Templates, and whether each one writes itself out every day.
///
/// The two belong on the same screen because they are the same object seen twice: a recurring
/// template is a template with a time on it. Splitting them would mean a list of names in one
/// place and a list of the same names in another.
struct TemplatesView: View {
    @ObservedObject var model: SessionModel

    @State private var editing: TemplateDraft?
    @State private var confirmingDelete: TemplateRow?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                ForEach(model.templates, id: \.id) { template in
                    TemplateCard(
                        template: template,
                        schedule: model.recurring.first { $0.templateTitle == template.title },
                        notificationsAllowed: model.notificationsAllowed,
                        use: { model.newFromTemplate(template) },
                        edit: { editing = TemplateDraft(template) },
                        remove: { confirmingDelete = template },
                        setSchedule: { hour, minute, enabled in
                            Task { await model.setRecurring(template.title, hour: hour, minute: minute, enabled: enabled) }
                        }
                    )
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Theme.canvas)
        .overlay(alignment: .center) {
            if model.templates.isEmpty {
                EmptyState(
                    icon: "doc.on.doc",
                    title: "No templates",
                    detail: "A template is a memo you start from often. Add one and it will be here on both this Mac and your phone."
                )
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                ToolbarIcon(symbol: "plus", help: "New template") { editing = TemplateDraft() }
            }
        }
        .sheet(item: $editing) { draft in
            TemplateEditor(draft: draft) { title, body in
                Task { await model.saveTemplate(id: draft.id, title: title, body: body) }
            }
        }
        .confirmationDialog(
            "Delete “\(confirmingDelete?.title ?? "")”?",
            isPresented: Binding(get: { confirmingDelete != nil }, set: { if !$0 { confirmingDelete = nil } })
        ) {
            Button("Delete", role: .destructive) {
                if let template = confirmingDelete {
                    Task { await model.deleteTemplate(template.id) }
                }
                confirmingDelete = nil
            }
            Button("Cancel", role: .cancel) { confirmingDelete = nil }
        } message: {
            Text("Memos already written from this template are not affected.")
        }
        .task { await model.loadTemplates() }
    }
}

private struct TemplateCard: View {
    let template: TemplateRow
    let schedule: RecurringRow?
    let notificationsAllowed: Bool
    let use: () -> Void
    let edit: () -> Void
    let remove: () -> Void
    let setSchedule: (Int, Int, Bool) -> Void

    @State private var hovering = false

    private var enabled: Bool { schedule?.enabled ?? false }
    private var hour: Int { Int(schedule?.hour ?? 8) }
    private var minute: Int { Int(schedule?.minute ?? 0) }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(template.title)
                    .font(Type.heading3)
                    .foregroundStyle(Theme.ink)
                Spacer(minLength: 0)
                if hovering {
                    Button("Edit", action: edit).buttonStyle(.link).font(Type.rowMeta)
                    Button("Delete", action: remove).buttonStyle(.link).font(Type.rowMeta)
                }
                Button("Use", action: use)
                    .controlSize(.small)
            }

            Text(preview)
                .font(Type.rowBody)
                .foregroundStyle(Theme.inkSoft)
                .lineLimit(3)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)

            Divider()

            HStack(spacing: 10) {
                Toggle("Write this every day at", isOn: Binding(
                    get: { enabled },
                    set: { setSchedule(hour, minute, $0) }
                ))
                .toggleStyle(.switch)
                .controlSize(.small)
                .font(Type.rowBody)

                TimeOfDayPicker(hour: hour, minute: minute) { h, m in
                    setSchedule(h, m, enabled)
                }
                .disabled(!enabled)

                Spacer(minLength: 0)

                if enabled, let next = schedule?.nextLabel, !next.isEmpty {
                    Text("Next \(next)")
                        .font(Type.rowMeta)
                        .foregroundStyle(Theme.inkSoft)
                }
            }

            if enabled && !notificationsAllowed {
                Label(
                    "Notifications are off, so the memo will be written the next time you open MyMemos but nothing will tell you.",
                    systemImage: "bell.slash"
                )
                .font(Type.rowMeta)
                .foregroundStyle(Theme.warm)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Theme.cardRadius).fill(Theme.card))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cardRadius)
                .strokeBorder(Theme.hairline.opacity(0.8), lineWidth: 0.5)
        )
        .onHover { hovering = $0 }
    }

    /// The body with the placeholders left as they are: seeing {{date}} is the point, because
    /// that is what makes a template a template rather than a copy of an old memo.
    private var preview: String {
        template.body
            .components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            .prefix(3)
            .joined(separator: "\n")
    }
}

/// Hour and minute, as two steppers rather than a DatePicker.
///
/// A DatePicker set to .hourAndMinute carries a date it does not use, and on a repeating alarm
/// that date is a lie waiting to be read by the next person.
private struct TimeOfDayPicker: View {
    let hour: Int
    let minute: Int
    let change: (Int, Int) -> Void

    var body: some View {
        HStack(spacing: 2) {
            Picker("", selection: Binding(get: { hour }, set: { change($0, minute) })) {
                ForEach(0..<24, id: \.self) { Text(String(format: "%02d", $0)).tag($0) }
            }
            .labelsHidden()
            .frame(width: 60)

            Text(":").foregroundStyle(Theme.inkSoft)

            Picker("", selection: Binding(get: { minute }, set: { change(hour, $0) })) {
                ForEach([0, 15, 30, 45], id: \.self) { Text(String(format: "%02d", $0)).tag($0) }
            }
            .labelsHidden()
            .frame(width: 60)
        }
    }
}

/// A template being written. Identifiable so it can drive a sheet; id 0 means a new one.
struct TemplateDraft: Identifiable {
    let templateId: Int64
    let title: String
    let body: String

    var id: Int64 { templateId }

    init() {
        templateId = 0
        title = ""
        body = ""
    }

    init(_ template: TemplateRow) {
        templateId = template.id
        title = template.title
        body = template.body
    }
}

private struct TemplateEditor: View {
    let draft: TemplateDraft
    let save: (String, String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var body_ = ""
    @State private var loaded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(draft.templateId == 0 ? "New template" : "Edit template")
                    .font(Type.heading3)
                Spacer()
                Button("Cancel") { dismiss() }
                Button("Save") {
                    save(title.trimmingCharacters(in: .whitespacesAndNewlines), body_)
                    dismiss()
                }
                .keyboardShortcut(.defaultAction)
                .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(14)

            Divider()

            VStack(alignment: .leading, spacing: 10) {
                TextField("Title", text: $title)
                    .textFieldStyle(.roundedBorder)
                TextEditor(text: $body_)
                    .font(Type.body)
                    .frame(minHeight: 240)
                    .scrollContentBackground(.hidden)
                    .background(Theme.card)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6).strokeBorder(Theme.hairline, lineWidth: 0.5)
                    )
                Text("{{date}}, {{isodate}}, {{time}}, {{weekday}}, {{month}} and {{year}} are filled in when the memo is written.")
                    .font(Type.rowMeta)
                    .foregroundStyle(Theme.inkSoft)
            }
            .padding(14)
        }
        .frame(width: 520)
        .background(Theme.canvas)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            title = draft.title
            body_ = draft.body
        }
    }
}
