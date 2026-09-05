import Foundation
import UIKit
import UserNotifications
import Combine

/**
 * Production APNs Push Notification Manager for iOS.
 * Manages device installation UUID, APNs token conversion, Supabase token registration,
 * unregistration on logout, and privacy-preserving caching with zero token logging.
 */
final class IOSPushNotificationManager: NSObject, ObservableObject, UNUserNotificationCenterDelegate {

    static let shared = IOSPushNotificationManager()

    private let userDefaults = UserDefaults.standard
    private let installationIdKey = "memostamp_apns_installation_id"
    private let cachedTokenKey = "memostamp_apns_cached_token"
    private let lastRegisteredTokenKey = "memostamp_apns_last_token"
    private let lastRegisteredUidKey = "memostamp_apns_last_uid"

    @Published var pendingRoute: String? = nil
    @Published var pendingTargetUserId: String? = nil

    private var cachedTokenHex: String? {
        get { userDefaults.string(forKey: cachedTokenKey) }
        set { userDefaults.set(newValue, forKey: cachedTokenKey) }
    }

    private var lastRegisteredToken: String? {
        get { userDefaults.string(forKey: lastRegisteredTokenKey) }
        set { userDefaults.set(newValue, forKey: lastRegisteredTokenKey) }
    }

    private var lastRegisteredUid: String? {
        get { userDefaults.string(forKey: lastRegisteredUidKey) }
        set { userDefaults.set(newValue, forKey: lastRegisteredUidKey) }
    }

    private override init() {
        super.init()
    }

    // MARK: - Stable Installation ID
    var installationId: String {
        if let existing = userDefaults.string(forKey: installationIdKey), !existing.isEmpty {
            return existing
        }
        let newId = UUID().uuidString
        userDefaults.set(newId, forKey: installationIdKey)
        return newId
    }

    // MARK: - APNs Registration
    func requestAuthorizationAndRegister() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            if granted && error == nil {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }

    func handleDeviceToken(_ deviceToken: Data) {
        // Convert token to lowercase hex string safely with ZERO logging
        let tokenHex = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        guard !tokenHex.isEmpty else { return }

        cachedTokenHex = tokenHex

        // If an authenticated session is active, synchronize token with server
        if let session = SupabaseAuthService.shared.activeSession {
            let uid = session.userId.trimmingCharacters(in: .whitespacesAndNewlines)
            let token = session.accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
            if !uid.isEmpty && !token.isEmpty {
                registerDeviceTokenWithServer(accessToken: token, userId: uid, tokenHex: tokenHex)
            }
        }
    }

    func handleRegistrationError(_ error: Error) {
        // Silently handle simulator or permission rejection without logging sensitive data
    }

    func registerCurrentDeviceToken(session: AuthSessionData) {
        let uid = session.userId.trimmingCharacters(in: .whitespacesAndNewlines)
        let token = session.accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !uid.isEmpty, !token.isEmpty else { return }

        if let tokenHex = cachedTokenHex, !tokenHex.isEmpty {
            registerDeviceTokenWithServer(accessToken: token, userId: uid, tokenHex: tokenHex)
        } else {
            // Prompt registration if not yet retrieved
            requestAuthorizationAndRegister()
        }
    }

    func unregisterDeviceToken() {
        guard let session = SupabaseAuthService.shared.activeSession else { return }
        let token = session.accessToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty else { return }

        let installId = installationId
        executeUnregisterRpc(accessToken: token, installationId: installId)

        lastRegisteredToken = nil
        lastRegisteredUid = nil
        IOSPushEventDeduper.shared.clear()
    }

    func onAccountDeleted() {
        lastRegisteredToken = nil
        lastRegisteredUid = nil
        cachedTokenHex = nil
        IOSPushEventDeduper.shared.clear()
    }

    // MARK: - Server RPC Sync
    private func registerDeviceTokenWithServer(accessToken: String, userId: String, tokenHex: String) {
        // Coalesce duplicate registrations
        if lastRegisteredToken == tokenHex && lastRegisteredUid == userId {
            return
        }

        let installId = installationId
        #if DEBUG
        let environment = "development"
        #else
        let environment = "production"
        #endif

        let baseUrl = SupabaseAuthService.shared.supabaseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let anonKey = SupabaseAuthService.shared.anonKey

        guard let url = URL(string: "\(baseUrl)/rest/v1/rpc/register_push_device_token") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        let body: [String: Any] = [
            "p_platform": "ios",
            "p_provider": "apns",
            "p_token": tokenHex,
            "p_installation_id": installId,
            "p_environment": environment
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: body) else { return }
        request.httpBody = httpBody

        URLSession.shared.dataTask(with: request) { [weak self] _, response, error in
            if error == nil, let httpResp = response as? HTTPURLResponse, (200...299).contains(httpResp.statusCode) {
                DispatchQueue.main.async {
                    self?.lastRegisteredToken = tokenHex
                    self?.lastRegisteredUid = userId
                }
            }
        }.resume()
    }

    private func executeUnregisterRpc(accessToken: String, installationId: String) {
        let baseUrl = SupabaseAuthService.shared.supabaseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let anonKey = SupabaseAuthService.shared.anonKey

        guard let url = URL(string: "\(baseUrl)/rest/v1/rpc/unregister_push_device_token") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        let body: [String: Any] = [
            "p_provider": "apns",
            "p_installation_id": installationId
        ]

        guard let httpBody = try? JSONSerialization.data(withJSONObject: body) else { return }
        request.httpBody = httpBody

        URLSession.shared.dataTask(with: request) { _, _, _ in }.resume()
    }

    // MARK: - UNUserNotificationCenterDelegate
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        let eventId = (userInfo["event_id"] as? String) ?? notification.request.identifier

        // Deduplication: Suppress foreground banner if realtime already displayed this event
        if !IOSPushEventDeduper.shared.shouldNotify(eventId: eventId) {
            completionHandler([])
            return
        }

        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound])
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        handleRemoteNotificationData(userInfo)
        completionHandler()
    }

    func handleRemoteNotificationData(_ userInfo: [AnyHashable: Any]) {
        let rawRoute = (userInfo["route"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let targetRoute: String
        switch rawRoute {
        case "CHAT": targetRoute = "CHAT"
        case "FRIENDS": targetRoute = "FRIENDS"
        default: targetRoute = "FRIENDS"
        }

        let targetUser = (userInfo["target_user_id"] as? String) ?? (userInfo["actor_id"] as? String)

        DispatchQueue.main.async {
            self.pendingRoute = targetRoute
            self.pendingTargetUserId = targetUser
        }
    }
}
