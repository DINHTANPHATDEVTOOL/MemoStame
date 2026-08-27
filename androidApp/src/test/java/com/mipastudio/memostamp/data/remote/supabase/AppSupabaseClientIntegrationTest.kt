package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.repository.UserProfile
import com.mipastudio.memostamp.domain.model.AudienceType
import com.mipastudio.memostamp.domain.model.FeedPost
import com.mipastudio.memostamp.domain.model.FeedPostType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-End & JWT Enforcement Test Suite validating SupabaseClient authorization contract.
 */
class AppSupabaseClientIntegrationTest {

    @Test
    fun testUnauthenticatedMutationFailsBeforeNetworkRequest() = runBlocking {
        val client = SupabaseClient()
        client.userAccessToken = null // Unauthenticated

        val testProfile = UserProfile(
            userId = "test_unauth_uid",
            username = "unauth_user",
            displayName = "Unauthenticated Test User"
        )

        val res = client.upsertProfile(testProfile)
        assertTrue("Unauthenticated mutation MUST fail client-side", res.isFailure)
        assertEquals("User authentication token required for RLS mutation", res.exceptionOrNull()?.message)
    }

    @Test
    fun testAuthenticatedMutationPassesTokenCheck() = runBlocking {
        val client = SupabaseClient()
        client.userAccessToken = "mock_valid_jwt_token"

        val testProfile = UserProfile(
            userId = "test_auth_uid",
            username = "auth_user",
            displayName = "Authenticated Test User"
        )

        val res = client.upsertProfile(testProfile)
        // With mock token, client-side check passes (fails at network layer with HTTP 401 if invalid live JWT)
        if (res.isFailure) {
            val errMsg = res.exceptionOrNull()?.message.orEmpty()
            assertFalse("Client-side JWT check should pass", errMsg.contains("User authentication token required"))
        }
    }

    @Test
    fun testPublicReadQueriesAreAllowed() = runBlocking {
        val client = SupabaseClient()
        val allProfiles = client.getAllProfiles()
        assertNotNull("Public read queries should succeed or return a valid list", allProfiles)
    }
}
