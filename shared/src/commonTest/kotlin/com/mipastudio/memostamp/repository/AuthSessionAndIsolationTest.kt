package com.mipastudio.memostamp.repository

import com.mipastudio.memostamp.getCurrentEpochMillis
import com.mipastudio.memostamp.domain.model.AuthSession
import com.mipastudio.memostamp.domain.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSupabaseAuthService {
    private val registeredUsers = mutableMapOf<String, String>() // email -> password
    private val userUids = mutableMapOf<String, String>() // email -> UUID string
    private val activeSessions = mutableMapOf<String, AuthSession>() // token -> session

    fun signUp(email: String, password: String): Result<AuthSession> {
        if (registeredUsers.containsKey(email)) {
            return Result.failure(Exception("Email already registered"))
        }
        val uid = "c138b1d9-7608-4171-8a9d-" + email.hashCode().toUInt().toString(16)
        registeredUsers[email] = password
        userUids[email] = uid
        val session = AuthSession(
            accessToken = "access_token_$uid",
            refreshToken = "refresh_token_$uid",
            expiresAt = (getCurrentEpochMillis() / 1000) + 3600,
            userId = uid,
            email = email
        )
        activeSessions[session.accessToken] = session
        return Result.success(session)
    }

    fun signIn(email: String, password: String): Result<AuthSession> {
        val expectedPwd = registeredUsers[email] ?: return Result.failure(Exception("User not found"))
        if (expectedPwd != password) {
            return Result.failure(Exception("Wrong password"))
        }
        val uid = userUids[email]!!
        val session = AuthSession(
            accessToken = "access_token_$uid",
            refreshToken = "refresh_token_$uid",
            expiresAt = (getCurrentEpochMillis() / 1000) + 3600,
            userId = uid,
            email = email
        )
        activeSessions[session.accessToken] = session
        return Result.success(session)
    }

    fun refreshSession(refreshToken: String): Result<AuthSession> {
        val oldSession = activeSessions.values.find { it.refreshToken == refreshToken }
            ?: return Result.failure(Exception("Invalid refresh token"))

        val newSession = oldSession.copy(
            accessToken = "new_access_token_" + oldSession.userId,
            expiresAt = (getCurrentEpochMillis() / 1000) + 3600
        )
        activeSessions.remove(oldSession.accessToken)
        activeSessions[newSession.accessToken] = newSession
        return Result.success(newSession)
    }

    fun logout(accessToken: String): Boolean {
        return activeSessions.remove(accessToken) != null
    }

    fun getSession(accessToken: String): AuthSession? {
        return activeSessions[accessToken]
    }
}

class AuthSessionAndIsolationTest {

    @Test
    fun testSignUpReturnsRealUUID() {
        val authService = FakeSupabaseAuthService()
        val result = authService.signUp("alice@memostamp.com", "password123")
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertTrue(session.userId.startsWith("c138b1d9-7608-4171-8a9d-"))
        assertEquals("alice@memostamp.com", session.email)
        val now = getCurrentEpochMillis() / 1000
        assertFalse(session.isExpired(now))
    }

    @Test
    fun testLoginReturnsSameUid() {
        val authService = FakeSupabaseAuthService()
        val signUpResult = authService.signUp("bob@memostamp.com", "securePass456")
        val signUpUid = signUpResult.getOrThrow().userId

        val signInResult = authService.signIn("bob@memostamp.com", "securePass456")
        assertTrue(signInResult.isSuccess)
        assertEquals(signUpUid, signInResult.getOrThrow().userId)
    }

    @Test
    fun testWrongPasswordFailure() {
        val authService = FakeSupabaseAuthService()
        authService.signUp("charlie@memostamp.com", "correctPassword")
        val signInResult = authService.signIn("charlie@memostamp.com", "wrongPassword")
        assertTrue(signInResult.isFailure)
    }

    @Test
    fun testLogoutRemovesSession() {
        val authService = FakeSupabaseAuthService()
        val session = authService.signUp("david@memostamp.com", "pass123456").getOrThrow()
        assertNotNull(authService.getSession(session.accessToken))

        val loggedOut = authService.logout(session.accessToken)
        assertTrue(loggedOut)
        assertNull(authService.getSession(session.accessToken))
    }

    @Test
    fun testExpiredAccessTokenRefresh() {
        val authService = FakeSupabaseAuthService()
        val session = authService.signUp("eve@memostamp.com", "pass123456").getOrThrow()

        val now = getCurrentEpochMillis() / 1000
        // Create an expired session object
        val expiredSession = session.copy(expiresAt = now - 100)
        assertTrue(expiredSession.isExpired(now))

        // Refresh session using refreshToken
        val refreshResult = authService.refreshSession(session.refreshToken)
        assertTrue(refreshResult.isSuccess)
        val refreshed = refreshResult.getOrThrow()
        assertFalse(refreshed.isExpired(now))
        assertEquals(session.userId, refreshed.userId)
    }

    @Test
    fun testRefreshFailureLogout() {
        val authService = FakeSupabaseAuthService()
        val refreshResult = authService.refreshSession("invalid_refresh_token_xyz")
        assertTrue(refreshResult.isFailure)
    }

    @Test
    fun testMultiAccountStateIsolationAndRestoration() {
        val repo = SharedMemoStampRepository()

        // Account A logs in
        val userA = UserProfile(uid = "uid-account-aaaa-1111", username = "alice", displayName = "Alice")
        repo.setCurrentUser(userA)
        repo.addStamp(
            title = "Alice Special Memory",
            note = "Notes from Hanoi",
            location = "Hanoi Old Quarter",
            imageUrl = "https://example.com/stamp_a.png"
        )
        val aliceSavedStamps = repo.stamps.value
        assertEquals(1, aliceSavedStamps.size)
        assertEquals("Alice Special Memory", aliceSavedStamps.first().title)

        // Account A logs out
        repo.resetUserScopedState()
        assertEquals(0, repo.stamps.value.size)

        // Account B logs in
        val userB = UserProfile(uid = "uid-account-bbbb-2222", username = "bob", displayName = "Bob")
        repo.setCurrentUser(userB)
        // Must NOT leak Account A data to Account B
        assertTrue(repo.stamps.value.none { it.title == "Alice Special Memory" })

        // Account B adds Bob's stamp
        repo.addStamp(
            title = "Bob Memory Saigon",
            note = "Notes from Saigon",
            location = "Saigon Central Post Office",
            imageUrl = "https://example.com/stamp_b.png"
        )
        assertEquals(1, repo.stamps.value.size)
        assertEquals("Bob Memory Saigon", repo.stamps.value.first().title)

        // Account B logs out & Account A logs back in
        repo.resetUserScopedState()
        repo.setCurrentUser(userA)
        // Restoring Account A's saved state
        aliceSavedStamps.forEach { stamp ->
            repo.addStamp(stamp.title, stamp.note, stamp.location ?: "", stamp.stampImagePath)
        }
        assertEquals(1, repo.stamps.value.size)
        assertEquals("Alice Special Memory", repo.stamps.value.first().title)
        assertTrue(repo.stamps.value.none { it.title == "Bob Memory Saigon" })
    }
}
