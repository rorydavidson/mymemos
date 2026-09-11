import BackgroundTasks
import SwiftUI
import UserNotifications
import Shared

/// What only an app delegate can do on iOS: register the background refresh task before
/// launch finishes, and receive notification taps.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// Set by the app once the model exists; the delegate is made first.
    weak var model: SessionModel?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        BackgroundRefresh.shared.register()
        return true
    }

    /// A reminder that fires while the app is open still shows, as it does on Android.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let userInfo = response.notification.request.content.userInfo
        await MainActor.run { model?.handleNotification(userInfo: userInfo) }
    }
}

/// Catching up while the app is closed, as far as iOS allows: a refresh task the system
/// runs when it sees fit, which pushes the outbox and pulls what changed. The design does
/// not depend on it running; the next launch does the same work. Reminders are scheduled
/// notifications and need no background time at all.
@MainActor
final class BackgroundRefresh: NSObject, BackgroundSync {
    static let shared = BackgroundRefresh()
    static let taskId = "com.keltruc.mymemos.ios.refresh"

    weak var model: SessionModel?
    private var registered = false

    func register() {
        guard !registered else { return }
        registered = true
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.taskId, using: nil) { [weak self] task in
            Task { @MainActor in await self?.run(task as! BGAppRefreshTask) }
        }
    }

    private func run(_ task: BGAppRefreshTask) async {
        schedule()
        let work = Task { await model?.sync() }
        task.expirationHandler = { work.cancel() }
        await work.value
        task.setTaskCompleted(success: !work.isCancelled)
    }

    /// Asks for a run in a quarter of an hour or so. The system decides when, and whether.
    func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: Self.taskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    // The shared scheduler's three ways in.
    func enqueueRetry(full: Bool) { schedule() }
    func ensurePeriodic() { schedule() }
    func cancelAll() { BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.taskId) }
}
