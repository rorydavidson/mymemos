import Foundation
import SwiftUI
import Shared

#if TESTHOOKS
/// Drives the app from the launch environment, for looking at screens on a simulator that
/// nothing else can tap. Compiled only with `-D TESTHOOKS` (see `tour.sh`), never into a
/// build anyone installs.
///
/// `MYMEMOS_TEST_SIGNIN=server|username|password` signs in first. `MYMEMOS_TEST_STEPS` is a
/// `;`-separated list: `open:N` selects the Nth memo on the timeline, `pane:tasks` switches
/// pane, `new` opens the editor, `edit:N` edits a memo, `compact`, `query:text`, `tag:name`,
/// `password:secret` supplies the memo password, `reveal:N` opens a locked memo,
/// `lock:N` encrypts one, `wait:S` sleeps.
@MainActor
enum TestHooks {
    static func run(_ model: SessionModel) async {
        let env = ProcessInfo.processInfo.environment
        if let signIn = env["MYMEMOS_TEST_SIGNIN"], model.phase == .signedOut {
            let parts = signIn.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
            guard parts.count == 3 else { return }
            model.server = parts[0]
            model.username = parts[1]
            model.password = parts[2]
            await model.signIn()
            await model.loadTemplates()
            await model.loadSchedules()
        }
        guard let steps = env["MYMEMOS_TEST_STEPS"] else { return }
        for step in steps.split(separator: ";").map(String.init) {
            let parts = step.split(separator: ":", maxSplits: 1).map(String.init)
            let arg = parts.count > 1 ? parts[1] : ""
            switch parts[0] {
            case "open": if let memo = nth(model, arg) { model.open(memo.localId) }
            case "pane":
                switch arg {
                case "tasks": model.pane = .tasks
                case "review": model.pane = .review
                case "reminders": model.pane = .reminders
                case "templates": model.pane = .templates
                default: model.pane = .memos
                }
            case "new": model.newMemo()
            case "newtext": model.editing = EditorTarget(localId: nil, initialText: arg)
            case "edit": if let memo = nth(model, arg) { model.editing = EditorTarget(localId: memo.localId) }
            case "compact": await model.setCompactList(true)
            case "query": model.query = arg; await model.reload()
            case "tag": model.activeTag = arg; await model.reload()
            case "password": await model.usePassword(arg, remember: false)
            case "reveal": if let memo = nth(model, arg) { await model.reveal(memo.localId) }
            case "lock": if let memo = nth(model, arg) { await model.lock(memo.localId) }
            case "react":
                let bits = arg.split(separator: ",").map(String.init)
                if bits.count == 2, let memo = nth(model, bits[0]) { await model.toggleReaction(memo.localId, bits[1]) }
            case "comment":
                let bits = arg.split(separator: ",", maxSplits: 1).map(String.init)
                if bits.count == 2, let memo = nth(model, bits[0]) { await model.addComment(memo.localId, bits[1]) }
            case "archive": if let memo = nth(model, arg) { await model.setArchived(memo.localId, true) }
            case "archived": model.showArchived = true; await model.reload()
            case "colour":
                let bits = arg.split(separator: ",").map(String.init)
                if bits.count == 2, let memo = nth(model, bits[0]) { await model.setColour(memo.localId, bits[1]) }
            case "tick":
                let bits = arg.split(separator: ",").map(String.init)
                if bits.count == 2, let memo = nth(model, bits[0]), let line = Int(bits[1]) { await model.toggleTask(memo.localId, line: line, checked: true) }
            case "delete": if let memo = nth(model, arg) { await model.delete(memo.localId) }
            case "sync": await model.sync()
            case "library": if let screen = MemosScreen.Library(rawValue: arg) { PhoneNavigation.shared.path = [screen] }
            case "wait": try? await Task.sleep(for: .seconds(Double(arg) ?? 1))
            default: break
            }
        }
    }

    private static func nth(_ model: SessionModel, _ index: String) -> MemoRow? {
        let all = model.sections.flatMap(\.memos)
        guard let i = Int(index), i < all.count else { return nil }
        return all[i]
    }
}
#endif
