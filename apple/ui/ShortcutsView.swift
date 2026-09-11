import SwiftUI
import Shared

/// Saved server-side filters. A shortcut runs on the server, so it needs a connection; what
/// it returns is shown from the local database like everything else.
struct ShortcutsView: View {
    @ObservedObject var model: SessionModel
    /// Called once a shortcut has been chosen, so a phone can pop back to the timeline.
    var chosen: () -> Void = {}

    @State private var editing: ShortcutDraft?
    @State private var confirmingDelete: ShortcutRow?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                if model.activeShortcut != nil {
                    Button {
                        Task { await model.showShortcut(nil); chosen() }
                    } label: {
                        Label("Show the timeline again", systemImage: "arrow.uturn.backward")
                            .font(Type.rowBody)
                    }
                    .linkButton()
                    .padding(.bottom, 4)
                }
                ForEach(model.shortcuts, id: \.name) { shortcut in
                    HStack(spacing: 10) {
                        Button {
                            Task { await model.showShortcut(shortcut); chosen() }
                        } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(shortcut.title).font(Type.rowTitle).foregroundStyle(Theme.ink)
                                Text(shortcut.filter).font(Type.code).foregroundStyle(Theme.inkSoft).lineLimit(2)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        Button("Edit") { editing = ShortcutDraft(shortcut) }.linkButton().font(Type.rowMeta)
                        Button("Delete") { confirmingDelete = shortcut }.linkButton().font(Type.rowMeta).foregroundStyle(Theme.danger)
                    }
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: Theme.cardRadius).fill(
                        model.activeShortcut?.name == shortcut.name ? Theme.accentSoft.opacity(0.5) : Theme.card))
                    .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius).strokeBorder(Theme.hairline))
                }
            }
            .padding(16)
            .frame(maxWidth: Theme.readingWidth, alignment: .leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.canvas)
        .overlay(alignment: .center) {
            if model.shortcuts.isEmpty {
                EmptyState(
                    icon: "line.3.horizontal.decrease.circle",
                    title: "No shortcuts",
                    detail: "A shortcut is a filter the server runs, written in CEL, such as tag in [\"work\"] && pinned. Add one and it is here on every device."
                )
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editing = ShortcutDraft() } label: { Image(systemName: "plus") }
            }
        }
        .sheet(item: $editing) { draft in
            ShortcutEditor(draft: draft) { title, filter in
                await model.saveShortcut(name: draft.name, title: title, filter: filter)
            }
        }
        .confirmationDialog(
            "Delete “\(confirmingDelete?.title ?? "")”?",
            isPresented: Binding(get: { confirmingDelete != nil }, set: { if !$0 { confirmingDelete = nil } })
        ) {
            Button("Delete", role: .destructive) {
                if let shortcut = confirmingDelete { Task { await model.deleteShortcut(shortcut.name) } }
                confirmingDelete = nil
            }
            Button("Cancel", role: .cancel) { confirmingDelete = nil }
        }
        .task { await model.loadShortcuts() }
    }
}

struct ShortcutDraft: Identifiable {
    let name: String?
    let title: String
    let filter: String
    var id: String { name ?? "new" }

    init() { name = nil; title = ""; filter = "" }
    init(_ row: ShortcutRow) { name = row.name; title = row.title; filter = row.filter }
}

private struct ShortcutEditor: View {
    let draft: ShortcutDraft
    let save: (String, String) async -> Bool

    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var filter = ""
    @State private var loaded = false
    @State private var saving = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(draft.name == nil ? "New shortcut" : "Edit shortcut").font(Type.heading3)
                Spacer()
                Button("Cancel") { dismiss() }
                Button("Save") {
                    saving = true
                    Task {
                        if await save(title.trimmingCharacters(in: .whitespaces), filter.trimmingCharacters(in: .whitespacesAndNewlines)) { dismiss() }
                        saving = false
                    }
                }
                .keyboardShortcut(.defaultAction)
                .disabled(saving || title.trimmingCharacters(in: .whitespaces).isEmpty || filter.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(14)
            Divider()
            VStack(alignment: .leading, spacing: 10) {
                TextField("Title", text: $title).textFieldStyle(.roundedBorder)
                TextField("Filter, e.g. tag in [\"work\"]", text: $filter, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .font(Type.code)
                    .lineLimit(3...6)
                Text("The filter is CEL, evaluated by the server. Fields include tag, content, pinned, visibility and creator.")
                    .font(Type.rowMeta).foregroundStyle(Theme.inkSoft)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
        }
        .sheetWidth(460)
        .background(Theme.canvas)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            title = draft.title
            filter = draft.filter
        }
    }
}
