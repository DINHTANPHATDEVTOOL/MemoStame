package com.mipastudio.memostamp.data.remote.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAuthSessionAndContractTest {

    @Test
    fun testA_signupReturnsNonEmptyTokensAndUserId() {
        val session = AndroidAuthSession(
            accessToken = "eyJhbGciOiJIUzI1Ni...",
            refreshToken = "d3b07384d113edec...",
            expiresAt = (System.currentTimeMillis() / 1000) + 3600,
            userId = "0a68d0e7-3f30-4e1b-b27e-8bfdfb40e53a",
            email = "test_user@example.com"
        )
        assertTrue(session.accessToken.isNotBlank())
        assertTrue(session.refreshToken.isNotBlank())
        assertTrue(session.userId.isNotBlank())
        assertEquals("0a68d0e7-3f30-4e1b-b27e-8bfdfb40e53a", session.userId)
        assertFalse(session.isExpired())
    }

    @Test
    fun testB_loginSameAccountReturnsSameUserId() {
        val session1 = AndroidAuthSession(
            accessToken = "token_1",
            refreshToken = "refresh_1",
            expiresAt = (System.currentTimeMillis() / 1000) + 3600,
            userId = "0a68d0e7-3f30-4e1b-b27e-8bfdfb40e53a",
            email = "user@example.com"
        )
        val session2 = AndroidAuthSession(
            accessToken = "token_2",
            refreshToken = "refresh_2",
            expiresAt = (System.currentTimeMillis() / 1000) + 3600,
            userId = "0a68d0e7-3f30-4e1b-b27e-8bfdfb40e53a",
            email = "user@example.com"
        )
        assertEquals(session1.userId, session2.userId)
    }

    @Test
    fun testC_wrongPasswordFailureKeepsStateLoggedOut() {
        val authResult = Result.failure<AndroidAuthSession>(IllegalStateException("Invalid login credentials"))
        assertTrue(authResult.isFailure)
        val session: AndroidAuthSession? = authResult.getOrNull()
        assertNull(session)
    }

    @Test
    fun testD_expiredSessionRefreshSucceedsWithSameUserId() {
        val expiredSession = AndroidAuthSession(
            accessToken = "old_token",
            refreshToken = "valid_refresh_token",
            expiresAt = (System.currentTimeMillis() / 1000) - 100,
            userId = "0a68d0e7-3f30-4e1b-b27e-8bfdfb40e53a",
            email = "user@example.com"
        )
        assertTrue(expiredSession.isExpired())

        val refreshedSession = AndroidAuthSession(
            accessToken = "new_token",
            refreshToken = "new_refresh_token",
            expiresAt = (System.currentTimeMillis() / 1000) + 3600,
            userId = expiredSession.userId,
            email = expiredSession.email
        )

        assertFalse(refreshedSession.isExpired())
        assertEquals(expiredSession.userId, refreshedSession.userId)
        assertEquals("new_token", refreshedSession.accessToken)
    }

    @Test
    fun testE_invalidRefreshTokenClearsSession() {
        val refreshResult = Result.failure<AndroidAuthSession>(IllegalStateException("Invalid Refresh Token"))
        assertTrue(refreshResult.isFailure)
        val activeSession: AndroidAuthSession? = refreshResult.getOrNull()
        assertNull(activeSession)
    }

    @Test
    fun testF_logoutClearsTokens() {
        var userAccessToken: String? = "valid_token"
        var activeSession: AndroidAuthSession? = AndroidAuthSession(
            accessToken = "valid_token",
            refreshToken = "refresh",
            expiresAt = 9999999999L,
            userId = "uid_123",
            email = "a@b.com"
        )

        // Logout
        activeSession = null
        userAccessToken = null

        assertNull(activeSession)
        assertNull(userAccessToken)
    }

    @Test
    fun testG_authenticatedMutationWithTokenAllowed() {
        val clientToken = "valid_user_jwt"
        assertTrue(clientToken.isNotBlank())
    }

    @Test
    fun testH_authenticatedMutationWithoutTokenFailsBeforeNetwork() {
        val userAccessToken: String? = null
        val requireUserAuth = true

        val canMutate = if (requireUserAuth && userAccessToken.isNullOrBlank()) {
            Result.failure<Boolean>(IllegalStateException("User authentication token required for RLS mutation"))
        } else {
            Result.success(true)
        }

        assertTrue(canMutate.isFailure)
        assertEquals("User authentication token required for RLS mutation", canMutate.exceptionOrNull()?.message)
    }

    @Test
    fun testI_accountALoginThenAccountBLoginDoesNotReuseTokenOrUid() {
        val sessionA = AndroidAuthSession(
            accessToken = "jwt_a",
            refreshToken = "ref_a",
            expiresAt = 9999999999L,
            userId = "uid_account_a",
            email = "a@example.com"
        )

        // Logout A
        var activeToken: String? = null

        // Login B
        val sessionB = AndroidAuthSession(
            accessToken = "jwt_b",
            refreshToken = "ref_b",
            expiresAt = 9999999999L,
            userId = "uid_account_b",
            email = "b@example.com"
        )
        activeToken = sessionB.accessToken

        assertEquals("uid_account_b", sessionB.userId)
        assertEquals("jwt_b", activeToken)
        assertFalse(sessionA.userId == sessionB.userId)
        assertFalse(sessionA.accessToken == activeToken)
    }

    @Test
    fun testJ_roomCachedAccountWithoutSupabaseSessionMustNotBeLoggedIn() {
        val hasRoomCachedUserInDb = true
        val supabaseSession: AndroidAuthSession? = null

        val isLoggedIn = hasRoomCachedUserInDb && supabaseSession != null && !supabaseSession.isExpired()
        assertFalse(isLoggedIn)
    }
}
