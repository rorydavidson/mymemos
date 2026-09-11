import SwiftUI
import Shared

/// The memos tab on a phone: the timeline, search, and the menu that reaches everything the
/// Mac keeps in its sidebar.
struct MemosScreen: View {
    @ObservedObject var model: SessionModel

    enum Library: Hashable { case reminders, templates, settings }

    var body: some View {
        NavigationStack {
            MemoListView(model: model, selection: $model.selection)
                .navigationTitle(model.activeTag.map { "#\($0)" } ?? "Memos")
                .navigationBarTitleDisplayMode(.large)
                .searchable(text: $model.query, prompt: "Search memos")
                .refreshable { await model.sync() }
                .toolbar { MemosToolbar(model: model) }
                .navigationDestination(item: $model.selection) { id in
                    DetailScreen(model: model, localId: id)
                }
                .navigationDestination(for: Library.self) { screen in
                    switch screen {
                    case .reminders:
                        RemindersView(model: model) { model.open($0) }.navigationTitle("Reminders")
                    case .templates:
                        TemplatesView(model: model).navigationTitle("Templates")
                    case .settings:
                        SettingsView(model: model).navigationTitle("Settings")
                    }
                }
        }
        .onChange(of: model.query) { _, _ in Task { await model.reload() } }
        .onChange(of: model.activeTag) { _, _ in Task { await model.reload() } }
    }
}

/// The timeline's toolbar: sync, a new memo, and the library menu.
struct MemosToolbar: ToolbarContent {
    @ObservedObject var model: SessionModel

    var body: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Menu {
                Section {
                    NavigationLink(value: MemosScreen.Library.reminders) {
                        Label("Reminders", systemImage: "bell")
                    }
                    NavigationLink(value: MemosScreen.Library.templates) {
                        Label("Templates", systemImage: "doc.on.doc")
                    }
                }

                if !model.tags.isEmpty {
                    Menu {
                        Button {
                            model.activeTag = nil
                        } label: {
                            Label("All memos", systemImage: model.activeTag == nil ? "checkmark" : "")
                        }
                        ForEach(model.tags, id: \.self) { tag in
                            Button {
                                model.activeTag = model.activeTag == tag ? nil : tag
                            } label: {
                                Label("#\(tag)", systemImage: model.activeTag == tag ? "checkmark" : "number")
                            }
                        }
                    } label: {
                        Label("Tags", systemImage: "number")
                    }
                }

                Section {
                    Toggle(isOn: Binding(
                        get: { model.compactList },
                        set: { enabled in Task { await model.setCompactList(enabled) } }
                    )) {
                        Label("Compact list", systemImage: "list.bullet")
                    }
                    Toggle(isOn: Binding(
                        get: { model.sortByModified },
                        set: { enabled in Task { await model.setSortByModified(enabled) } }
                    )) {
                        Label("Sort by last changed", systemImage: "arrow.up.arrow.down")
                    }
                }

                Section {
                    NavigationLink(value: MemosScreen.Library.settings) {
                        Label("Settings", systemImage: "gearshape")
                    }
                }
            } label: {
                Image(systemName: "line.3.horizontal")
            }
        }

        ToolbarItem(placement: .topBarTrailing) {
            SyncStatusButton(model: model)
        }

        ToolbarItem(placement: .topBarTrailing) {
            Button { model.newMemo() } label: {
                Image(systemName: "square.and.pencil")
            }
        }
    }
}
