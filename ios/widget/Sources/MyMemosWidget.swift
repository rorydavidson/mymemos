import SwiftUI
import WidgetKit

// Two widgets, the same two Android has: recent memos with a quick-capture button, and open
// tasks. Both read the snapshot the app writes into the app group; neither opens the
// database. Tapping goes through the mymemos:// scheme the app already handles.

@main
struct MyMemosWidgets: WidgetBundle {
    var body: some Widget {
        RecentMemosWidget()
        OpenTasksWidget()
    }
}

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: AppGroup.Snapshot?
}

struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry {
        SnapshotEntry(date: Date(), snapshot: AppGroup.Snapshot(
            recent: [.init(localId: "", title: "A memo", time: "09:00", pinned: false)],
            tasks: [.init(memoLocalId: "", line: 0, text: "Something to do", due: "Tomorrow", overdue: false)],
            writtenAt: Date()
        ))
    }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(SnapshotEntry(date: Date(), snapshot: AppGroup.readSnapshot()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        // The app refreshes the widget whenever it rewrites the snapshot; this is the fallback.
        let entry = SnapshotEntry(date: Date(), snapshot: AppGroup.readSnapshot())
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(30 * 60))))
    }
}

struct RecentMemosWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "com.keltruc.mymemos.recent", provider: SnapshotProvider()) { entry in
            RecentMemosView(entry: entry)
                .containerBackground(Color(red: 0.945, green: 0.925, blue: 0.894), for: .widget)
        }
        .configurationDisplayName("Recent memos")
        .description("Your latest memos, and a button to write a new one.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct OpenTasksWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "com.keltruc.mymemos.tasks", provider: SnapshotProvider()) { entry in
            OpenTasksView(entry: entry)
                .containerBackground(Color(red: 0.945, green: 0.925, blue: 0.894), for: .widget)
        }
        .configurationDisplayName("Open tasks")
        .description("What is still unticked, across all your memos.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

private let accent = Color(red: 0.122, green: 0.435, blue: 0.361)

struct RecentMemosView: View {
    let entry: SnapshotEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("MEMOS").font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
                Link(destination: URL(string: "mymemos://new?open=true")!) {
                    Image(systemName: "square.and.pencil").foregroundStyle(accent)
                }
            }
            let memos = Array((entry.snapshot?.recent ?? []).prefix(family == .systemLarge ? 8 : 3))
            if memos.isEmpty {
                Text("Open MyMemos to sync.").font(.footnote).foregroundStyle(.secondary)
            }
            ForEach(memos, id: \.localId) { memo in
                Link(destination: URL(string: "mymemos://memo/\(memo.localId)")!) {
                    HStack(spacing: 6) {
                        if memo.pinned { Image(systemName: "pin.fill").font(.caption2).foregroundStyle(accent) }
                        Text(memo.title.isEmpty ? "Untitled" : memo.title).font(.footnote).lineLimit(1)
                        Spacer()
                        Text(memo.time).font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }
            Spacer(minLength: 0)
        }
    }
}

struct OpenTasksView: View {
    let entry: SnapshotEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("TASKS").font(.caption2.weight(.semibold)).foregroundStyle(.secondary)
            let tasks = Array((entry.snapshot?.tasks ?? []).prefix(family == .systemLarge ? 9 : 4))
            if tasks.isEmpty {
                Text("Nothing open.").font(.footnote).foregroundStyle(.secondary)
            }
            ForEach(Array(tasks.enumerated()), id: \.offset) { _, task in
                Link(destination: URL(string: "mymemos://memo/\(task.memoLocalId)")!) {
                    HStack(spacing: 6) {
                        Image(systemName: "square").font(.footnote).foregroundStyle(.secondary)
                        Text(task.text).font(.footnote).lineLimit(1)
                        Spacer()
                        if let due = task.due {
                            Text(due).font(.caption2).foregroundStyle(task.overdue ? .red : accent)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
        }
    }
}
