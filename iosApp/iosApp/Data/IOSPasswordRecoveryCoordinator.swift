import Foundation
import SwiftUI

struct IOSRecoverySessionData {
    let accessToken: String
    let refreshToken: String?
    let userId: String
    let email: String
}

enum IOSPasswordRecoveryState: Equatable {
    case idle
    case validating
    case ready(userId: String, email: String)
    case updating
    case success
    case invalid(String)
}

struct IOSPasswordRecoveryParser {
    static let canonicalScheme = "memostamp"
    static let canonicalHost = "auth"
    static let canonicalPath = "/recovery"
    static let canonicalRedirectUrl = "memostamp://auth/recovery"

    static func parseUrl(_ url: URL) -> Result<(accessToken: String, refreshToken: String?), Error> {
        let scheme = (url.scheme ?? "").lowercased()
        guard scheme == canonicalScheme else {
            return .failure(NSError(domain: "PasswordRecovery", code: 400, userInfo: [NSLocalizedDescriptionKey: "Scheme không hợp lệ: \(scheme) (yêu cầu \(canonicalScheme))"]))
        }

        let host = (url.host ?? "").lowercased()
        guard host == canonicalHost else {
            return .failure(NSError(domain: "PasswordRecovery", code: 400, userInfo: [NSLocalizedDescriptionKey: "Host không hợp lệ: \(host) (yêu cầu \(canonicalHost))"]))
        }

        let path = url.path
        guard path == canonicalPath || path == "\(canonicalPath)/" else {
            return .failure(NSError(domain: "PasswordRecovery", code: 400, userInfo: [NSLocalizedDescriptionKey: "Path không hợp lệ: \(path) (yêu cầu \(canonicalPath))"]))
        }

        var paramMap = [String: String]()

        // Parse fragment parameters (#access_token=...&type=recovery)
        if let fragment = url.fragment, !fragment.isEmpty {
            parseParams(from: fragment, into: &paramMap)
        }

        // Parse query parameters (?access_token=...&type=recovery)
        if let query = url.query, !query.isEmpty {
            parseParams(from: query, into: &paramMap)
        }

        // Validate recovery type if present
        if let type = paramMap["type"] {
            guard type.caseInsensitiveCompare("recovery") == .orderedSame else {
                return .failure(NSError(domain: "PasswordRecovery", code: 400, userInfo: [NSLocalizedDescriptionKey: "Loại token không phải recovery: \(type)"]))
            }
        }

        // Extract token
        guard let token = paramMap["access_token"] ?? paramMap["token"], !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .failure(NSError(domain: "PasswordRecovery", code: 400, userInfo: [NSLocalizedDescriptionKey: "Thiếu token khôi phục trong liên kết"]))
        }

        let cleanToken = token.trimmingCharacters(in: .whitespacesAndNewlines)
        let refreshToken = paramMap["refresh_token"]?.trimmingCharacters(in: .whitespacesAndNewlines)

        return .success((accessToken: cleanToken, refreshToken: refreshToken))
    }

    private static func parseParams(from string: String, into target: inout [String: String]) {
        let pairs = string.components(separatedBy: "&")
        for pair in pairs {
            let parts = pair.components(separatedBy: "=")
            if parts.count >= 2 {
                let key = (parts[0].removingPercentEncoding ?? parts[0]).trimmingCharacters(in: .whitespacesAndNewlines)
                let value = (parts[1].removingPercentEncoding ?? parts[1]).trimmingCharacters(in: .whitespacesAndNewlines)
                if target[key] == nil {
                    target[key] = value
                }
            }
        }
    }

    static func validatePassword(password: String, confirm: String) -> Result<Void, Error> {
        if password.count < 6 {
            return .failure(NSError(domain: "PasswordRecovery", code: 400, userInfo: [NSLocalizedDescriptionKey: "Mật khẩu mới phải có ít nhất 6 ký tự"]))
        }
        if password != confirm {
            return .failure(NSError(domain: "PasswordRecovery", code: 400, userInfo: [NSLocalizedDescriptionKey: "Mật khẩu xác nhận không khớp"]))
        }
        return .success(())
    }
}

class IOSPasswordRecoveryCoordinator: ObservableObject {
    static let shared = IOSPasswordRecoveryCoordinator()

    @Published var recoveryState: IOSPasswordRecoveryState = .idle

    // Ephemeral in-memory session only
    private var ephemeralSession: IOSRecoverySessionData? = nil

    // Consumed tokens to avoid duplicate replay in same process
    private var consumedTokens = Set<String>()

    private init() {}

    func handleDeepLink(_ url: URL) {
        let parseResult = IOSPasswordRecoveryParser.parseUrl(url)
        switch parseResult {
        case .failure(let error):
            DispatchQueue.main.async {
                self.recoveryState = .invalid(error.localizedDescription)
            }
        case .success(let tokens):
            let accessToken = tokens.accessToken
            if consumedTokens.contains(accessToken) {
                DispatchQueue.main.async {
                    self.recoveryState = .invalid("Liên kết này đã được sử dụng. Vui lòng yêu cầu liên kết mới.")
                }
                return
            }

            DispatchQueue.main.async {
                self.recoveryState = .validating
            }

            SupabaseAuthService.shared.validateRecoveryUser(accessToken: accessToken) { [weak self] result in
                guard let self = self else { return }
                DispatchQueue.main.async {
                    switch result {
                    case .failure(let err):
                        self.ephemeralSession = nil
                        self.recoveryState = .invalid(err.localizedDescription)
                    case .success(let userInfo):
                        // Cross-account check: If active user is already logged in as a different user
                        if let currentUserId = SupabaseAuthService.shared.currentUserId,
                           !currentUserId.isEmpty,
                           currentUserId != userInfo.userId {
                            self.ephemeralSession = nil
                            self.recoveryState = .invalid("Liên kết đặt lại mật khẩu này thuộc về tài khoản khác (\(userInfo.email)). Vui lòng đăng xuất khỏi tài khoản hiện tại trước khi tiếp tục.")
                            return
                        }

                        self.ephemeralSession = IOSRecoverySessionData(
                            accessToken: accessToken,
                            refreshToken: tokens.refreshToken,
                            userId: userInfo.userId,
                            email: userInfo.email
                        )
                        self.recoveryState = .ready(userId: userInfo.userId, email: userInfo.email)
                    }
                }
            }
        }
    }

    func updatePassword(newPassword: String, confirmPassword: String, completion: @escaping (Result<Void, Error>) -> Void) {
        let validation = IOSPasswordRecoveryParser.validatePassword(password: newPassword, confirm: confirmPassword)
        if case .failure(let error) = validation {
            completion(.failure(error))
            return
        }

        guard let session = ephemeralSession else {
            completion(.failure(NSError(domain: "PasswordRecovery", code: 401, userInfo: [NSLocalizedDescriptionKey: "Không có phiên khôi phục hợp lệ"])))
            return
        }

        if consumedTokens.contains(session.accessToken) {
            completion(.failure(NSError(domain: "PasswordRecovery", code: 401, userInfo: [NSLocalizedDescriptionKey: "Phiên khôi phục đã hết hạn hoặc đã được sử dụng"])))
            return
        }

        recoveryState = .updating

        SupabaseAuthService.shared.updateUserPassword(accessToken: session.accessToken, newPassword: newPassword) { [weak self] result in
            guard let self = self else { return }
            DispatchQueue.main.async {
                switch result {
                case .success:
                    // Destroy ephemeral session immediately
                    self.consumedTokens.insert(session.accessToken)
                    self.ephemeralSession = nil

                    // If active user was logged into the same account, sign out locally so they log in fresh
                    if let currentUid = SupabaseAuthService.shared.currentUserId, currentUid == session.userId {
                        SupabaseAuthService.shared.clearSessionLocally()
                    }

                    self.recoveryState = .success
                    completion(.success(()))
                case .failure(let err):
                    self.recoveryState = .ready(userId: session.userId, email: session.email)
                    completion(.failure(err))
                }
            }
        }
    }

    func resetState() {
        ephemeralSession = nil
        recoveryState = .idle
    }
}
