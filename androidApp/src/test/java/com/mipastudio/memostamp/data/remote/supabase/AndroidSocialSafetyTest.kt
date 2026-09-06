package com.mipastudio.memostamp.data.remote.supabase

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSocialSafetyTest {

    private val gson = Gson()

    @Test
    fun test1_supabaseBlockedUserSerialization() {
        val json = """
            [
                {
                    "id": "block_123",
                    "blocker_id": "11111111-1111-1111-1111-111111111111",
                    "blocked_id": "22222222-2222-2222-2222-222222222222",
                    "created_at": "2026-09-06T00:00:00Z"
                }
            ]
        """.trimIndent()

        val listType = object : TypeToken<List<SupabaseBlockedUser>>() {}.type
        val list: List<SupabaseBlockedUser> = gson.fromJson(json, listType)

        assertEquals(1, list.size)
        val item = list[0]
        assertEquals("block_123", item.id)
        assertEquals("11111111-1111-1111-1111-111111111111", item.blockerId)
        assertEquals("22222222-2222-2222-2222-222222222222", item.blockedId)
        assertNotNull(item.createdAt)
    }

    @Test
    fun test2_blockUserRpcSuccess() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["rpc/block_user"] = Result.success("""{"success": true, "blocker_id": "u1", "blocked_id": "u2"}""")

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "mock_valid_jwt"

        val res = client.blockUserRpc("22222222-2222-2222-2222-222222222222")

        assertTrue("blockUserRpc should succeed", res.isSuccess)
        assertTrue(transport.callLogs.any { it.contains("rpc/block_user") })
    }

    @Test
    fun test3_unblockUserRpcSuccess() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["rpc/unblock_user"] = Result.success("""{"success": true, "unblocked": true}""")

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "mock_valid_jwt"

        val res = client.unblockUserRpc("22222222-2222-2222-2222-222222222222")

        assertTrue("unblockUserRpc should succeed", res.isSuccess)
        assertTrue(transport.callLogs.any { it.contains("rpc/unblock_user") })
    }

    @Test
    fun test4_reportUserRpcWithValidCategories() = runBlocking {
        val validCategories = listOf("spam", "harassment", "impersonation", "inappropriate_content", "other")
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["rpc/report_user"] = Result.success("""{"success": true, "report_id": "rep_1", "status": "PENDING"}""")

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "mock_valid_jwt"

        for (cat in validCategories) {
            val res = client.reportUserRpc(
                reportedUserId = "22222222-2222-2222-2222-222222222222",
                category = cat,
                note = "Detailed report note"
            )
            assertTrue("Category $cat should succeed", res.isSuccess)
        }
    }

    @Test
    fun test5_reportUserRpcHandlesError() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["rpc/report_user"] = Result.failure(IllegalStateException("400 Bad Request: Cannot report oneself"))

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "mock_valid_jwt"

        val res = client.reportUserRpc(
            reportedUserId = "11111111-1111-1111-1111-111111111111",
            category = "spam"
        )
        assertTrue("Self-report or bad request should fail", res.isFailure)
    }

    @Test
    fun test6_getBlockedUsersQueriesCorrectEndpoint() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        transport.endpointResponses["user_blocks"] = Result.success("""
            [
                {"id": "b1", "blocker_id": "u1", "blocked_id": "u2"},
                {"id": "b2", "blocker_id": "u1", "blocked_id": "u3"}
            ]
        """.trimIndent())

        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = "mock_valid_jwt"

        val res = client.getBlockedUsers()

        assertTrue(res.isSuccess)
        val list = res.getOrNull().orEmpty()
        assertEquals(2, list.size)
        assertEquals("u2", list[0].blockedId)
        assertEquals("u3", list[1].blockedId)
        assertTrue(transport.callLogs.any { it.contains("user_blocks") })
    }

    @Test
    fun test7_unauthenticatedMutationsRejected() = runBlocking {
        val transport = FakeSupabaseHttpTransport()
        val client = SupabaseClient()
        client.transport = transport
        client.userAccessToken = null // Unauthenticated

        val blockRes = client.blockUserRpc("u2")
        assertTrue("Unauthenticated block should fail", blockRes.isFailure)

        val unblockRes = client.unblockUserRpc("u2")
        assertTrue("Unauthenticated unblock should fail", unblockRes.isFailure)

        val reportRes = client.reportUserRpc("u2", "spam")
        assertTrue("Unauthenticated report should fail", reportRes.isFailure)

        val getBlocksRes = client.getBlockedUsers()
        assertTrue("Unauthenticated getBlockedUsers should fail", getBlocksRes.isFailure)
    }
}
