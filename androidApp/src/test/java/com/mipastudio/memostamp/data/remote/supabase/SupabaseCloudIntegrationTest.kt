package com.mipastudio.memostamp.data.remote.supabase

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live Integration Test Suite connecting directly to the real Supabase Cloud Database:
 * Base URL: https://mghmhhbyhmuvherlyrqa.supabase.co
 */
class SupabaseCloudIntegrationTest {

    private val baseUrl = "https://mghmhhbyhmuvherlyrqa.supabase.co"
    private val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1naG1oaGJ5aG11dmhlcmx5cnFhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcyMDc1MTksImV4cCI6MjEwMjc4MzUxOX0._vviFZ3q8aSl-7wTX8nDXVN6KtN9eF-B5fBndlO6KRc"
    private val gson = Gson()

    private fun executeHttp(
        endpointPath: String,
        method: String = "GET",
        jsonBody: String? = null,
        prefer: String? = null
    ): Pair<Int, String> {
        val fullUrl = if (endpointPath.startsWith("http")) endpointPath else "$baseUrl/rest/v1/$endpointPath"
        val url = URL(fullUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            if (!prefer.isNullOrBlank()) {
                setRequestProperty("Prefer", prefer)
            }
            connectTimeout = 10000
            readTimeout = 10000
            if (!jsonBody.isNullOrBlank()) {
                doOutput = true
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        return Pair(code, responseText)
    }

    @Test
    fun testSupabaseCloudConnection() {
        val (code, response) = executeHttp("profiles?select=count&limit=1")
        assertTrue("Supabase Cloud should return HTTP 2xx, got $code", code in 200..299)
        assertTrue("Response should contain count", response.contains("count"))
    }

    @Test
    fun testProfilesCloudCrud() {
        val testUserId = "user_test_cloud_auto_" + System.currentTimeMillis()
        val profileMap = mapOf(
            "user_id" to testUserId,
            "username" to "cloud_tester",
            "display_name" to "Cloud Integration Test User",
            "bio" to "Testing MemoStamp Supabase Cloud Database",
            "city" to "Đà Lạt"
        )

        // 1. INSERT (Upsert) profile to Cloud
        val (postCode, postBody) = executeHttp("profiles?on_conflict=user_id", method = "POST", jsonBody = gson.toJson(profileMap), prefer = "resolution=merge-duplicates")
        assertTrue("Upsert profile to cloud should succeed, got HTTP $postCode: $postBody", postCode in 200..299)

        // 2. QUERY profile from Cloud
        val encodedId = URLEncoder.encode(testUserId, "UTF-8")
        val (getCode, getBody) = executeHttp("profiles?user_id=eq.$encodedId&select=*")
        assertEquals(200, getCode)
        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val profiles: List<Map<String, Any>> = gson.fromJson(getBody, listType)
        assertEquals(1, profiles.size)
        assertEquals("cloud_tester", profiles[0]["username"])
        assertEquals("Cloud Integration Test User", profiles[0]["display_name"])

        // 3. CLEANUP profile from Cloud
        val (deleteCode, deleteBody) = executeHttp("profiles?user_id=eq.$encodedId", method = "DELETE")
        assertTrue("Delete profile from cloud should succeed, got HTTP $deleteCode: $deleteBody", deleteCode in 200..299)
    }

    @Test
    fun testFriendshipCloudLifecycle() {
        val aliceId = "user_alice_test_" + System.currentTimeMillis()
        val bobId = "user_bob_test_" + System.currentTimeMillis()
        val requestId = "freq_test_" + System.currentTimeMillis()

        // 1. Create test profiles
        executeHttp("profiles?on_conflict=user_id", method = "POST", jsonBody = gson.toJson(mapOf("user_id" to aliceId, "username" to "alice_test", "display_name" to "Alice Test")), prefer = "resolution=merge-duplicates")
        executeHttp("profiles?on_conflict=user_id", method = "POST", jsonBody = gson.toJson(mapOf("user_id" to bobId, "username" to "bob_test", "display_name" to "Bob Test")), prefer = "resolution=merge-duplicates")

        // 2. Send Friend Request Alice -> Bob
        val requestMap = mutableMapOf<String, Any?>(
            "id" to requestId,
            "sender_id" to aliceId,
            "sender_username" to "alice_test",
            "sender_display_name" to "Alice Test",
            "recipient_id" to bobId,
            "recipient_username" to "bob_test",
            "recipient_display_name" to "Bob Test",
            "status" to "PENDING",
            "created_at" to System.currentTimeMillis()
        )
        val (sendCode, sendBody) = executeHttp("friend_requests?on_conflict=id", method = "POST", jsonBody = gson.toJson(requestMap), prefer = "resolution=merge-duplicates")
        assertTrue("Send friend request to cloud should succeed, got HTTP $sendCode: $sendBody", sendCode in 200..299)

        // 3. Query Pending Requests for Bob
        val encBob = URLEncoder.encode(bobId, "UTF-8")
        val (reqCode, reqBody) = executeHttp("friend_requests?recipient_id=eq.$encBob&status=eq.PENDING&select=*")
        assertEquals(200, reqCode)
        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val requests: List<Map<String, Any>> = gson.fromJson(reqBody, listType)
        assertTrue("Bob should receive pending request", requests.any { it["id"] == requestId })

        // 4. Accept Request via Upsert
        requestMap["status"] = "ACCEPTED"
        val (patchCode, patchBody) = executeHttp("friend_requests?on_conflict=id", method = "POST", jsonBody = gson.toJson(requestMap), prefer = "resolution=merge-duplicates")
        assertTrue("Update request status should succeed, got HTTP $patchCode: $patchBody", patchCode in 200..299)

        // 5. Add Friendship Relations
        val rel1 = mapOf("id" to "${aliceId}_${bobId}", "user_id" to aliceId, "friend_id" to bobId, "friend_username" to "bob_test", "friend_name" to "Bob Test", "created_at" to System.currentTimeMillis())
        val rel2 = mapOf("id" to "${bobId}_${aliceId}", "user_id" to bobId, "friend_id" to aliceId, "friend_username" to "alice_test", "friend_name" to "Alice Test", "created_at" to System.currentTimeMillis())
        val (friendCode, friendBody) = executeHttp("friends?on_conflict=id", method = "POST", jsonBody = gson.toJson(listOf(rel1, rel2)), prefer = "resolution=merge-duplicates")
        assertTrue("Insert friendship to cloud should succeed, got HTTP $friendCode: $friendBody", friendCode in 200..299)

        // 6. CLEANUP
        val encAlice = URLEncoder.encode(aliceId, "UTF-8")
        val encReqId = URLEncoder.encode(requestId, "UTF-8")
        executeHttp("friends?or=(id.eq.${aliceId}_${bobId},id.eq.${bobId}_${aliceId})", method = "DELETE")
        executeHttp("friend_requests?id=eq.$encReqId", method = "DELETE")
        executeHttp("profiles?user_id=eq.$encAlice", method = "DELETE")
        executeHttp("profiles?user_id=eq.$encBob", method = "DELETE")
    }

    @Test
    fun testDirectMessagesCloudLifecycle() {
        val msgId = "msg_test_cloud_" + System.currentTimeMillis()
        val msgMap = mutableMapOf<String, Any?>(
            "id" to msgId,
            "sender_id" to "user_sender_cloud_test",
            "sender_name" to "Người Gửi Test",
            "recipient_id" to "user_recipient_cloud_test",
            "recipient_name" to "Người Nhận Test",
            "text" to "Bưu thiếp kỷ niệm gửi từ Cloud Automation Test",
            "stamp_id" to "stamp_cloud_999",
            "stamp_title" to "Tem Bưu Chính Đà Lạt",
            "is_read" to false,
            "created_at" to System.currentTimeMillis()
        )

        // 1. Send Direct Message to Cloud
        val (sendCode, sendBody) = executeHttp("direct_messages?on_conflict=id", method = "POST", jsonBody = gson.toJson(msgMap), prefer = "resolution=merge-duplicates")
        assertTrue("Send direct message to cloud should succeed, got HTTP $sendCode: $sendBody", sendCode in 200..299)

        // 2. Query Message from Cloud
        val encMsgId = URLEncoder.encode(msgId, "UTF-8")
        val (getCode, getBody) = executeHttp("direct_messages?id=eq.$encMsgId&select=*")
        assertEquals(200, getCode)
        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val messages: List<Map<String, Any>> = gson.fromJson(getBody, listType)
        assertEquals(1, messages.size)
        assertEquals("Tem Bưu Chính Đà Lạt", messages[0]["stamp_title"])
        assertEquals(false, messages[0]["is_read"])

        // 3. Mark as Read via Upsert
        msgMap["is_read"] = true
        val (patchCode, patchBody) = executeHttp("direct_messages?on_conflict=id", method = "POST", jsonBody = gson.toJson(msgMap), prefer = "resolution=merge-duplicates")
        assertTrue("Mark message as read should succeed, got HTTP $patchCode: $patchBody", patchCode in 200..299)

        // 4. CLEANUP
        val (delCode, delBody) = executeHttp("direct_messages?id=eq.$encMsgId", method = "DELETE")
        assertTrue("Delete message should succeed, got HTTP $delCode: $delBody", delCode in 200..299)
    }

    @Test
    fun testFeedPostsCloudLifecycle() {
        val postId = "post_test_cloud_" + System.currentTimeMillis()
        val postMap = mapOf(
            "id" to postId,
            "stamp_id" to "stamp_dalat_555",
            "stamp_url" to "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800",
            "stamp_title" to "Hoàng Hôn Hồ Tuyền Lâm",
            "author_id" to "user_author_cloud_test",
            "author_name" to "Tác Giả Cloud Test",
            "caption" to "Kỷ niệm tuyệt đẹp tại Đà Lạt",
            "audience_type" to "EVERYONE",
            "created_at" to System.currentTimeMillis()
        )

        // 1. Create Feed Post on Cloud
        val (createCode, createBody) = executeHttp("feed_posts?on_conflict=id", method = "POST", jsonBody = gson.toJson(postMap), prefer = "resolution=merge-duplicates")
        assertTrue("Create feed post on cloud should succeed, got HTTP $createCode: $createBody", createCode in 200..299)

        // 2. Query Feed Post
        val encPostId = URLEncoder.encode(postId, "UTF-8")
        val (getCode, getBody) = executeHttp("feed_posts?id=eq.$encPostId&select=*")
        assertEquals(200, getCode)
        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val posts: List<Map<String, Any>> = gson.fromJson(getBody, listType)
        assertEquals(1, posts.size)
        assertEquals("Hoàng Hôn Hồ Tuyền Lâm", posts[0]["stamp_title"])

        // 3. Add Reaction
        val reactId = "react_test_" + System.currentTimeMillis()
        val reactMap = mapOf("id" to reactId, "post_id" to postId, "user_id" to "user_author_cloud_test", "user_name" to "Tác Giả Cloud Test", "emoji" to "❤️", "created_at" to System.currentTimeMillis())
        val (reactCode, reactBody) = executeHttp("feed_reactions?on_conflict=id", method = "POST", jsonBody = gson.toJson(reactMap), prefer = "resolution=merge-duplicates")
        assertTrue("Add reaction to cloud post should succeed, got HTTP $reactCode: $reactBody", reactCode in 200..299)

        // 4. Add Comment
        val commentId = "comment_test_" + System.currentTimeMillis()
        val commentMap = mapOf("id" to commentId, "post_id" to postId, "author_id" to "user_author_cloud_test", "author_name" to "Tác Giả Cloud Test", "content" to "Ảnh chụp tem đẹp lắm!", "created_at" to System.currentTimeMillis())
        val (commentCode, commentBody) = executeHttp("feed_comments?on_conflict=id", method = "POST", jsonBody = gson.toJson(commentMap), prefer = "resolution=merge-duplicates")
        assertTrue("Add comment to cloud post should succeed, got HTTP $commentCode: $commentBody", commentCode in 200..299)

        // 5. CLEANUP
        val encReactId = URLEncoder.encode(reactId, "UTF-8")
        val encCommentId = URLEncoder.encode(commentId, "UTF-8")
        executeHttp("feed_reactions?id=eq.$encReactId", method = "DELETE")
        executeHttp("feed_comments?id=eq.$encCommentId", method = "DELETE")
        val (delCode, delBody) = executeHttp("feed_posts?id=eq.$encPostId", method = "DELETE")
        assertTrue("Delete feed post from cloud should succeed, got HTTP $delCode: $delBody", delCode in 200..299)
    }
}
