import SwiftUI
#if canImport(UIKit)
import UIKit
import UserNotifications

final class IOSAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = IOSPushNotificationManager.shared
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        IOSPushNotificationManager.shared.handleDeviceToken(deviceToken)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        IOSPushNotificationManager.shared.handleRegistrationError(error)
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        IOSPushNotificationManager.shared.handleRemoteNotificationData(userInfo)
        completionHandler(.newData)
    }
}
#endif
import shared

@main
struct iosAppApp: App {
    #if canImport(UIKit)
    @UIApplicationDelegateAdaptor(IOSAppDelegate.self) var appDelegate
    #endif

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
