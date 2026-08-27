import Foundation
import Security

struct AuthSessionData: Codable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: Int64
    let userId: String
    let email: String

    var isExpired: Bool {
        let now = Int64(Date().timeIntervalSince1970)
        return now >= (expiresAt - 30)
    }
}

struct KeychainStore {
    private static let service = "com.mipastudio.memostamp.auth"
    private static let account = "supabase_session"

    static func saveSession(_ session: AuthSessionData) -> Bool {
        guard let data = try? JSONEncoder().encode(session) else { return false }
        deleteSession()

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]

        let status = SecItemAdd(query as CFDictionary, nil)
        return status == errSecSuccess
    }

    static func loadSession() -> AuthSessionData? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else {
            return nil
        }

        return try? JSONDecoder().decode(AuthSessionData.self, from: data)
    }

    static func deleteSession() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
