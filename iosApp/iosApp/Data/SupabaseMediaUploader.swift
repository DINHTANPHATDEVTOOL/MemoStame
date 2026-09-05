import Foundation
#if canImport(UIKit)
import UIKit
#endif
import CryptoKit

class SupabaseMediaUploader {
    static let shared = SupabaseMediaUploader()

    let supabaseUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"
    let bucketName = "stamp-media"

    private init() {}

    /// Ensures that the rendered stamp artifact is available at a safe remote HTTPS URL.
    /// If already a valid remote HTTP(S) URL, returns it immediately.
    /// If a local file path, verifies session authority, hashes the content with SHA-256,
    /// uploads to `<ownerUid>/rendered/<contentHash>.<ext>`, and returns the public HTTPS URL.
    func ensureRemoteRenderedStamp(
        ownerUid: String,
        localOrRemotePath: String,
        completion: @escaping (Result<String, Error>) -> Void
    ) {
        let cleanPath = localOrRemotePath.trimmingCharacters(in: .whitespacesAndNewlines)

        // 1. If already a safe remote HTTP(S) URL, return directly
        if isValidRemoteStampUrl(cleanPath) {
            completion(.success(cleanPath))
            return
        }

        // Reject unsupported/in-memory data URIs
        if cleanPath.lowercased().hasPrefix("data:") || cleanPath.lowercased().hasPrefix("blob:") {
            completion(.failure(SupabaseSocialError.invalidData("Data/blob URIs are not allowed for cloud media: \(cleanPath)")))
            return
        }

        // 2. Strict session identity authority check
        guard let session = SupabaseAuthService.shared.activeSession else {
            completion(.failure(SupabaseSocialError.unauthorized))
            return
        }
        let sessionUid = session.userId.trimmingCharacters(in: .whitespacesAndNewlines)
        let requestedUid = ownerUid.trimmingCharacters(in: .whitespacesAndNewlines)

        guard IOSLocalPersistenceStore.shared.isValidAuthenticatedUserId(requestedUid),
              sessionUid == requestedUid,
              !session.accessToken.isEmpty else {
            completion(.failure(SupabaseSocialError.unauthorized))
            return
        }

        // 3. Resolve local filesystem path
        var localFilePath = cleanPath
        if localFilePath.hasPrefix("file://") {
            if let url = URL(string: localFilePath) {
                localFilePath = url.path
            } else {
                localFilePath = String(localFilePath.dropFirst(7))
            }
        }

        guard FileManager.default.fileExists(atPath: localFilePath) else {
            completion(.failure(SupabaseSocialError.invalidData("Tệp ảnh tem cục bộ không tồn tại: \(localFilePath)")))
            return
        }

        guard let initialData = FileManager.default.contents(atPath: localFilePath), !initialData.isEmpty else {
            completion(.failure(SupabaseSocialError.invalidData("Tệp ảnh rỗng: \(localFilePath)")))
            return
        }

        var uploadBytes = initialData
        var mimeType = "image/png"
        var ext = "png"

        let lower = localFilePath.lowercased()
        if lower.hasSuffix(".jpg") || lower.hasSuffix(".jpeg") {
            mimeType = "image/jpeg"
            ext = "jpg"
        } else if lower.hasSuffix(".webp") {
            mimeType = "image/webp"
            ext = "webp"
        }

        // 4. Check 8MB limit and compress rendered image if needed
        let maxLimit = 8 * 1024 * 1024 // 8 MB
        if uploadBytes.count > maxLimit {
            #if canImport(UIKit)
            if let uiImage = UIImage(data: uploadBytes) {
                if let compressed = uiImage.jpegData(compressionQuality: 0.85), compressed.count <= maxLimit {
                    uploadBytes = compressed
                    mimeType = "image/jpeg"
                    ext = "jpg"
                } else if let smaller = uiImage.jpegData(compressionQuality: 0.7) {
                    uploadBytes = smaller
                    mimeType = "image/jpeg"
                    ext = "jpg"
                }
            }
            #endif
        }

        guard uploadBytes.count <= maxLimit else {
            completion(.failure(SupabaseSocialError.invalidData("Kích thước ảnh vượt quá giới hạn 8MB")))
            return
        }

        // 5. Compute SHA-256 content hash for idempotent naming
        let hash = SHA256.hash(data: uploadBytes)
        let hashString = hash.compactMap { String(format: "%02x", $0) }.joined()
        let objectPath = "\(requestedUid)/rendered/\(hashString).\(ext)"
        let publicUrl = "\(supabaseUrl)/storage/v1/object/public/\(bucketName)/\(objectPath)"

        // 6. Authenticated Storage Upload
        guard let uploadUrl = URL(string: "\(supabaseUrl)/storage/v1/object/\(bucketName)/\(objectPath)") else {
            completion(.failure(SupabaseSocialError.invalidUrl))
            return
        }

        var request = URLRequest(url: uploadUrl)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(mimeType, forHTTPHeaderField: "Content-Type")
        request.setValue("true", forHTTPHeaderField: "x-upsert")
        request.httpBody = uploadBytes

        URLSession.shared.dataTask(with: request) { _, response, error in
            if let err = error {
                completion(.failure(SupabaseSocialError.networkError(err.localizedDescription)))
                return
            }

            guard let httpRes = response as? HTTPURLResponse else {
                completion(.failure(SupabaseSocialError.parseError("Phản hồi HTTP storage không hợp lệ")))
                return
            }

            // 200..299: Upload success. 409 Conflict: Object with exact hash already exists (idempotent success).
            if (200...299).contains(httpRes.statusCode) || httpRes.statusCode == 409 {
                completion(.success(publicUrl))
            } else {
                completion(.failure(SupabaseSocialError.serverError(httpRes.statusCode, "Lỗi tải ảnh lên Storage: HTTP \(httpRes.statusCode)")))
            }
        }.resume()
    }
}
