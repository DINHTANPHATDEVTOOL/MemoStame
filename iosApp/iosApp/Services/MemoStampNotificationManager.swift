import Foundation
import UserNotifications

/// Native Swift Notification Manager for scheduling daily memory reminders and stamp trade alerts.
final class MemoStampNotificationManager: NSObject, UNUserNotificationCenterDelegate {
    static let shared = MemoStampNotificationManager()

    private override init() {
        super.init()
    }

    func requestAuthorization(completion: @escaping (Bool) -> Void) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            DispatchQueue.main.async {
                completion(granted && error == nil)
            }
        }
    }

    func scheduleDailyMemoryReminder(at hour: Int = 18, minute: Int = 30) {
        let content = UNMutableNotificationContent()
        content.title = "📮 Daily Memory Stamp"
        content.body = "What memory did you capture today? Tap to stamp your moment! ✨"
        content.sound = .default

        var dateComponents = DateComponents()
        dateComponents.hour = hour
        dateComponents.minute = minute

        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)
        let request = UNNotificationRequest(identifier: "daily_memory_stamp_reminder", content: content, trigger: trigger)

        UNUserNotificationCenter.current().add(request) { error in
            if let err = error {
                print("Failed to schedule daily reminder: \(err.localizedDescription)")
            }
        }
    }

    func sendTradeRequestAlert(friendName: String, stampTitle: String) {
        let content = UNMutableNotificationContent()
        content.title = "📬 New Stamp Trade Offer!"
        content.body = "\(friendName) sent you a trade request for '\(stampTitle)'."
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1.0, repeats: false)
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: trigger)

        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }

    // MARK: - UNUserNotificationCenterDelegate
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound])
        }
    }
}
