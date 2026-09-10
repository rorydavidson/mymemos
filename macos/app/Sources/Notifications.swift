import Foundation
import UserNotifications
import Shared

/// Reminders, recurring templates and the weekly digest, as far as the system is concerned.
///
/// The shape of this is decided by one fact: a scheduled local notification is delivered by
/// the system whether or not the app is running, but nothing can write a memo while the app is
/// closed. So the alarm is the part that has to survive, and the work it stands for is caught
/// up on the next launch. A recurring template therefore uses a *repeating* trigger rather
/// than one set for its next occurrence: a Mac left shut for a fortnight still gets told.
///
/// Every request this app owns is prefixed, so re-syncing can clear its own pending alarms
/// without touching anything else.
@MainActor
final class Notifications: ObservableObject {

    enum Permission {
        case notAsked, allowed, denied
    }

    @Published private(set) var permission: Permission = .notAsked

    private let centre = UNUserNotificationCenter.current()

    private static let reminderPrefix = "reminder."
    private static let recurringPrefix = "recurring."
    private static let digestId = "digest.weekly"

    // MARK: permission

    func refreshPermission() async {
        let settings = await centre.notificationSettings()
        permission = Self.permission(from: settings.authorizationStatus)
    }

    /// Asks once. A denial is the user's answer and is not asked again: System Settings is
    /// where that gets changed, and the settings pane says so.
    @discardableResult
    func requestPermission() async -> Bool {
        let granted = (try? await centre.requestAuthorization(options: [.alert, .sound])) ?? false
        await refreshPermission()
        return granted
    }

    private static func permission(from status: UNAuthorizationStatus) -> Permission {
        switch status {
        case .authorized, .provisional: return .allowed
        case .denied: return .denied
        default: return .notAsked
        }
    }

    // MARK: scheduling

    /// Replaces every alarm this app owns with the ones the config now asks for.
    ///
    /// Wholesale rather than incremental on purpose: the config memo syncs, so a reminder can
    /// vanish because another device fired it, and working out the difference is more ways to
    /// get it wrong than simply saying what the answer is now.
    func sync(reminders: [ReminderRow], recurring: [RecurringRow], digest: Bool, digestHour: Int) async {
        guard permission == .allowed else { return }
        await clearOwnPending()

        let now = Date()
        for reminder in reminders {
            let fireAt = Date(timeIntervalSince1970: Double(reminder.atEpochMs) / 1000)
            // A reminder already past is caught up at launch, not scheduled into the past.
            guard fireAt > now else { continue }
            add(
                id: Self.reminderPrefix + reminder.id,
                title: "Memo reminder",
                body: reminder.note.isEmpty ? reminder.memoTitle : reminder.note,
                trigger: UNCalendarNotificationTrigger(
                    dateMatching: Calendar.current.dateComponents(
                        [.year, .month, .day, .hour, .minute], from: fireAt
                    ),
                    repeats: false
                )
            )
        }

        for template in recurring where template.enabled {
            var components = DateComponents()
            components.hour = Int(template.hour)
            components.minute = Int(template.minute)
            add(
                id: Self.recurringPrefix + template.templateTitle,
                title: template.templateTitle,
                body: "Ready when you open MyMemos.",
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            )
        }

        if digest {
            var components = DateComponents()
            components.weekday = 1 // Sunday, in the Gregorian calendar's own numbering.
            components.hour = digestHour
            components.minute = 0
            add(
                id: Self.digestId,
                title: "Your week in memos",
                body: "Open MyMemos for the summary.",
                trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            )
        }
    }

    /// Posts something now, for work the app has just caught up on.
    func postNow(id: String, title: String, body: String) {
        guard permission == .allowed else { return }
        add(id: id, title: title, body: body, trigger: nil)
    }

    private func add(id: String, title: String, body: String, trigger: UNNotificationTrigger?) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        centre.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    private func clearOwnPending() async {
        let pending = await centre.pendingNotificationRequests()
        let ours = pending.map(\.identifier).filter {
            $0.hasPrefix(Self.reminderPrefix) || $0.hasPrefix(Self.recurringPrefix) || $0 == Self.digestId
        }
        centre.removePendingNotificationRequests(withIdentifiers: ours)
    }
}
