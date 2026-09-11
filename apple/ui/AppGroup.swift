import Foundation

/// The container the app shares with its extensions. The share extension leaves what was
/// shared in an inbox here, and the app writes a small snapshot here for the widget: neither
/// extension links the Kotlin framework or opens the database, which keeps them small and
/// keeps one process writing the database.
enum AppGroup {
    static let id = "group.com.keltruc.mymemos"

    static var container: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: id)
    }

    // MARK: The inbox, from the share extension to the app

    /// One thing shared into the app: some text and any images, as files already copied into
    /// the container so they outlive the extension.
    struct Shared: Codable {
        var text: String
        var images: [String]
        var receivedAt: Date
    }

    private static var inbox: URL? { container?.appendingPathComponent("inbox", isDirectory: true) }

    static func leave(_ item: Shared) throws {
        guard let inbox else { throw CocoaError(.fileNoSuchFile) }
        try FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(item)
        try data.write(to: inbox.appendingPathComponent("\(UUID().uuidString).json"))
    }

    /// Where a shared image goes, so the extension can copy it before it is torn down.
    static func imageDestination(extension ext: String) -> URL? {
        guard let inbox else { return nil }
        try? FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        return inbox.appendingPathComponent("\(UUID().uuidString).\(ext)")
    }

    /// Takes everything waiting, oldest first, and removes the notes (not the images, which
    /// the editor still has to attach).
    static func collect() -> [Shared] {
        guard let inbox, let names = try? FileManager.default.contentsOfDirectory(atPath: inbox.path) else { return [] }
        var found: [Shared] = []
        for name in names where name.hasSuffix(".json") {
            let url = inbox.appendingPathComponent(name)
            if let data = try? Data(contentsOf: url), let item = try? JSONDecoder().decode(Shared.self, from: data) {
                found.append(item)
            }
            try? FileManager.default.removeItem(at: url)
        }
        return found.sorted { $0.receivedAt < $1.receivedAt }
    }

    // MARK: The snapshot, from the app to the widget

    /// What the widget shows. The app rewrites it whenever the timeline changes.
    struct Snapshot: Codable {
        struct Memo: Codable {
            var localId: String
            var title: String
            var time: String
            var pinned: Bool
        }
        struct Task: Codable {
            var memoLocalId: String
            var line: Int
            var text: String
            var due: String?
            var overdue: Bool
        }
        var recent: [Memo]
        var tasks: [Task]
        var writtenAt: Date
    }

    private static var snapshotFile: URL? { container?.appendingPathComponent("widget.json") }

    static func write(_ snapshot: Snapshot) {
        guard let snapshotFile, let data = try? JSONEncoder().encode(snapshot) else { return }
        try? data.write(to: snapshotFile, options: .atomic)
    }

    static func readSnapshot() -> Snapshot? {
        guard let snapshotFile, let data = try? Data(contentsOf: snapshotFile) else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }
}
