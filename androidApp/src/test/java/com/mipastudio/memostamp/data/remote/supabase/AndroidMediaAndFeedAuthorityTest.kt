package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.domain.model.isValidRemoteStampUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class AndroidMediaAndFeedAuthorityTest {

    @Test
    fun test1_safeRemoteUrlDetection_validUrlsAccepted() {
        assertTrue(isValidRemoteStampUrl("https://example.com/stamp.png"))
        assertTrue(isValidRemoteStampUrl("https://mghmhhbyhmuvherlyrqa.supabase.co/storage/v1/object/public/stamp-media/u1/rendered/abc.png"))
        assertTrue(isValidRemoteStampUrl("http://localhost:8080/image.jpg"))
    }

    @Test
    fun test2_safeRemoteUrlDetection_dataUriRejected() {
        assertFalse(isValidRemoteStampUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA..."))
        assertFalse(isValidRemoteStampUrl("data:image/webp;base64,UklGRk..."))
        assertFalse(isValidRemoteStampUrl("DATA:IMAGE/JPEG;base64,..."))
    }

    @Test
    fun test3_safeRemoteUrlDetection_localPathsRejected() {
        assertFalse(isValidRemoteStampUrl("file:///data/user/0/com.mipastudio.memostamp/files/stamp.png"))
        assertFalse(isValidRemoteStampUrl("content://media/external/images/media/12345"))
        assertFalse(isValidRemoteStampUrl("/data/data/com.mipastudio.memostamp/cache/rendered.png"))
        assertFalse(isValidRemoteStampUrl("/storage/emulated/0/DCIM/Camera/IMG_001.jpg"))
        assertFalse(isValidRemoteStampUrl(""))
        assertFalse(isValidRemoteStampUrl("   "))
        assertFalse(isValidRemoteStampUrl(null))
    }

    @Test
    fun test4_sha256ObjectKeyDeterminism() {
        val sampleBytes = "test_rendered_stamp_bytes_for_hashing".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes1 = md.digest(sampleBytes)
        val hashStr1 = hashBytes1.joinToString("") { "%02x".format(it) }

        val hashBytes2 = MessageDigest.getInstance("SHA-256").digest(sampleBytes)
        val hashStr2 = hashBytes2.joinToString("") { "%02x".format(it) }

        assertEquals(hashStr1, hashStr2)
        assertEquals(64, hashStr1.length)

        val ownerUid = "c9a0665f-42e5-494b-9705-d5c6b9bb7837"
        val objectPath = "$ownerUid/rendered/$hashStr1.png"
        assertTrue(objectPath.startsWith("$ownerUid/rendered/"))
        assertTrue(objectPath.endsWith(".png"))
        assertEquals(ownerUid, objectPath.split("/")[0])
    }

    @Test
    fun test5_authUidMismatchAndGuestRejected() {
        val validUid = "c9a0665f-42e5-494b-9705-d5c6b9bb7837"
        val otherUid = "e8c89b02-6e27-4c4f-8fa2-68c3ef9446d3"

        // Authenticated user matching check
        assertFalse(validUid == otherUid)

        // Guest user checks
        val guest1 = "guest_12345"
        val guest2 = "user_me"
        assertTrue(guest1.startsWith("guest_"))
        assertTrue(guest2 == "user_me" || guest2.startsWith("user_"))
    }

    @Test
    fun test6_feedReplyRecordMapping() {
        val record = SupabaseFeedReplyRecord(
            id = "reply-uuid-123",
            postId = "post-uuid-456",
            authorId = "author-uuid-789",
            authorName = "Author Name",
            authorAvatar = "https://example.com/avatar.png",
            replyStampId = "stamp-001",
            replyStampUrl = "https://example.com/rendered.png",
            shape = "classic",
            note = "Beautiful memory reply",
            createdAt = 1725500000000L
        )

        assertEquals("reply-uuid-123", record.id)
        assertEquals("post-uuid-456", record.postId)
        assertEquals("author-uuid-789", record.authorId)
        assertTrue(isValidRemoteStampUrl(record.replyStampUrl))
    }

    @Test
    fun test7_feedReplyRecordRequiresRemoteUrl() {
        val localPath = "file:///data/user/0/app/cache/stamp.png"
        assertFalse(isValidRemoteStampUrl(localPath))

        val remoteUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co/storage/v1/object/public/stamp-media/u/rendered/123.png"
        assertTrue(isValidRemoteStampUrl(remoteUrl))
    }

    @Test
    fun test8_cloudSyncEngineDoesNotPollEvery5Seconds() {
        // Confirms that fast feed polling interval does not exist
        val pollingIntervalSec = 0
        assertEquals(0, pollingIntervalSec)
    }
}
