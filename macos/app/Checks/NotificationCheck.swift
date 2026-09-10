import Foundation
import UserNotifications

/// Checks the notification centre is actually reachable from a bundle built the way this app
/// is built.
///
/// Worth checking because the failure is abrupt and easy to ship: `UNUserNotificationCenter
/// .current()` traps rather than returning nil when the calling code has no bundle identifier
/// or no code signature the system will accept, and everything that schedules a reminder goes
/// through it. A plain command-line binary cannot answer that question, so this check runs
/// from inside a real bundle: reaching the point where a status is printed is the result.
///
/// It deliberately does not ask for permission. That is the user's to give, and a check that
/// popped a system prompt would be a check nobody could run twice.
@main
struct NotificationCheck {
    static func main() {
        guard let identifier = Bundle.main.bundleIdentifier else {
            print("FAIL the check is not running inside a bundle, so this proves nothing")
            exit(1)
        }
        print("ok   running as \(identifier)")

        let centre = UNUserNotificationCenter.current()
        print("ok   UNUserNotificationCenter.current() returned without trapping")

        let waiting = DispatchSemaphore(value: 0)
        var status: UNAuthorizationStatus?
        centre.getNotificationSettings { settings in
            status = settings.authorizationStatus
            waiting.signal()
        }

        guard waiting.wait(timeout: .now() + 10) == .success, let status else {
            print("FAIL the notification centre did not answer within ten seconds")
            exit(1)
        }
        print("ok   authorisation status is \(name(status))")

        // Scheduling has to work too, and it is a separate permission gate from reading the
        // status. With permission the request lands; without it the centre refuses, which is
        // the correct answer rather than a failure of this check.
        let content = UNMutableNotificationContent()
        content.title = "MyMemos check"
        let request = UNNotificationRequest(
            identifier: "check.selftest",
            content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 3600, repeats: false)
        )
        let added = DispatchSemaphore(value: 0)
        var addError: Error?
        centre.add(request) { error in
            addError = error
            added.signal()
        }
        guard added.wait(timeout: .now() + 10) == .success else {
            print("FAIL adding a request never came back")
            exit(1)
        }
        centre.removePendingNotificationRequests(withIdentifiers: [request.identifier])

        switch (status, addError) {
        case (.authorized, nil), (.provisional, nil):
            print("ok   a request can be scheduled and withdrawn")
        case (.authorized, let error?), (.provisional, let error?):
            print("FAIL permission is granted but scheduling failed: \(error.localizedDescription)")
            exit(1)
        default:
            print("note scheduling is refused until the user allows notifications, which is correct")
        }

        print("\nnotification check passed")
    }

    static func name(_ status: UNAuthorizationStatus) -> String {
        switch status {
        case .notDetermined: return "not asked yet"
        case .denied: return "denied"
        case .authorized: return "allowed"
        case .provisional: return "provisional"
        case .ephemeral: return "ephemeral"
        @unknown default: return "unknown"
        }
    }
}
