package com.mipastudio.memostamp.data.local

import android.content.Context
import java.io.File

/**
 * Pure security and privacy purge helper for account deletion.
 * Enforces strict sandbox-only path validation, account-scoped preference keys,
 * and canonical UID validation.
 */
object AccountDeletionHelper {

    fun isValidAuthUid(uid: String?): Boolean {
        if (uid.isNullOrBlank()) return false
        val trimmed = uid.trim()
        val lower = trimmed.lowercase()
        if (lower == "user_me" || lower == "guest" || lower.startsWith("guest_") || lower.startsWith("guest")) {
            return false
        }
        return trimmed.length >= 8
    }

    fun isPathBeneathRoots(roots: List<File?>, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val trimmed = path.trim()
        val lower = trimmed.lowercase()

        // Explicitly reject URI schemes, web URLs, and content providers
        if (lower.startsWith("content://") ||
            lower.startsWith("http://") ||
            lower.startsWith("https://")
        ) {
            return false
        }

        // Clean file:// prefix if present
        val cleanPath = if (lower.startsWith("file://")) {
            trimmed.substring(7)
        } else {
            trimmed
        }

        return try {
            val file = File(cleanPath).canonicalFile
            val allowedRoots = roots.filterNotNull().map { it.canonicalFile }

            allowedRoots.any { root ->
                val rootPath = root.path
                val filePath = file.path
                filePath == rootPath || filePath.startsWith(rootPath + File.separator)
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isAppPrivatePath(context: Context, path: String?): Boolean {
        val roots = listOf(
            context.filesDir,
            context.cacheDir,
            context.noBackupFilesDir
        )
        return isPathBeneathRoots(roots, path)
    }

    fun safelyDeleteAppPrivateFile(context: Context, path: String?): Boolean {
        if (!isAppPrivatePath(context, path)) return false
        val cleanPath = if (path!!.trim().lowercase().startsWith("file://")) {
            path.trim().substring(7)
        } else {
            path.trim()
        }

        return try {
            val file = File(cleanPath).canonicalFile
            if (file.exists() && file.isFile) {
                file.delete()
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getMessagesPrefKey(userId: String): String = "direct_messages_of_$userId"

    fun getFriendsPrefKey(userId: String): String = "friends_list_of_$userId"
}
