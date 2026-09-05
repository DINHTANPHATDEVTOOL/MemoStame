package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.local.AccountDeletionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccountDeletionHardeningTest {

    private val testSandboxDir = File(System.getProperty("java.io.tmpdir"), "memostamp_test_sandbox")
    private val testFilesDir = File(testSandboxDir, "files")
    private val testCacheDir = File(testSandboxDir, "cache")
    private val testNoBackupDir = File(testSandboxDir, "no_backup")
    private val allowedRoots = listOf(testFilesDir, testCacheDir, testNoBackupDir)

    @Test
    fun test1_validAppPrivatePathAccepted() {
        val validFileInFiles = File(testFilesDir, "stamps/stamp_123.png").absolutePath
        val validFileInCache = File(testCacheDir, "temp_render.png").absolutePath
        val validFileWithPrefix = "file://" + File(testNoBackupDir, "private_key.dat").absolutePath

        assertTrue(
            "Path in filesDir should be accepted",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, validFileInFiles)
        )
        assertTrue(
            "Path in cacheDir should be accepted",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, validFileInCache)
        )
        assertTrue(
            "File:// path in noBackupFilesDir should be accepted",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, validFileWithPrefix)
        )
    }

    @Test
    fun test2_outsidePathRejected() {
        val systemPath = "/system/etc/hosts"
        val rootPath = "/root/secret.txt"
        val outsideTmp = File(System.getProperty("java.io.tmpdir"), "other_app/data.json").absolutePath
        val traversalPath = File(testFilesDir, "../../etc/passwd").absolutePath

        assertFalse(
            "System paths must be rejected",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, systemPath)
        )
        assertFalse(
            "Root paths must be rejected",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, rootPath)
        )
        assertFalse(
            "Paths outside sandbox roots must be rejected",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, outsideTmp)
        )
        assertFalse(
            "Path traversal attempts must be rejected",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, traversalPath)
        )
    }

    @Test
    fun test3_contentSchemeRejected() {
        val contentUri = "content://media/external/images/media/12345"
        val contentWithUpper = "CONTENT://com.android.providers.media.documents/document/image%3A123"

        assertFalse(
            "content:// URIs must be rejected",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, contentUri)
        )
        assertFalse(
            "Case-insensitive content:// URIs must be rejected",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, contentWithUpper)
        )
    }

    @Test
    fun test4_httpsSchemeRejected() {
        val httpsUrl = "https://example.com/stamp.png"
        val httpUrl = "http://malicious.site/script.sh"

        assertFalse(
            "https:// URLs must be rejected",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, httpsUrl)
        )
        assertFalse(
            "http:// URLs must be rejected",
            AccountDeletionHelper.isPathBeneathRoots(allowedRoots, httpUrl)
        )
    }

    @Test
    fun test5_accountAKeyDoesNotEqualBKey() {
        val uidA = "00000000-0000-0000-0000-00000000000a"
        val uidB = "00000000-0000-0000-0000-00000000000b"

        val msgKeyA = AccountDeletionHelper.getMessagesPrefKey(uidA)
        val msgKeyB = AccountDeletionHelper.getMessagesPrefKey(uidB)
        assertNotEquals("Messages cache key for A and B must differ", msgKeyA, msgKeyB)
        assertEquals("direct_messages_of_$uidA", msgKeyA)
        assertEquals("direct_messages_of_$uidB", msgKeyB)

        val friendKeyA = AccountDeletionHelper.getFriendsPrefKey(uidA)
        val friendKeyB = AccountDeletionHelper.getFriendsPrefKey(uidB)
        assertNotEquals("Friends cache key for A and B must differ", friendKeyA, friendKeyB)
        assertEquals("friends_list_of_$uidA", friendKeyA)
        assertEquals("friends_list_of_$uidB", friendKeyB)
    }

    @Test
    fun test6_invalidAuthUidRejected() {
        assertFalse("Null UID must be rejected", AccountDeletionHelper.isValidAuthUid(null))
        assertFalse("Blank UID must be rejected", AccountDeletionHelper.isValidAuthUid(""))
        assertFalse("Whitespace UID must be rejected", AccountDeletionHelper.isValidAuthUid("   "))
        assertFalse("user_me must be rejected", AccountDeletionHelper.isValidAuthUid("user_me"))
        assertFalse("guest must be rejected", AccountDeletionHelper.isValidAuthUid("guest"))
        assertFalse("guest_visitor must be rejected", AccountDeletionHelper.isValidAuthUid("guest_visitor"))
        assertFalse("guest_* must be rejected", AccountDeletionHelper.isValidAuthUid("guest_12345"))
        assertFalse("Short invalid UID must be rejected", AccountDeletionHelper.isValidAuthUid("abc"))

        // Valid UUID or canonical UID
        assertTrue(
            "Valid UUID must be accepted",
            AccountDeletionHelper.isValidAuthUid("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        )
    }
}
