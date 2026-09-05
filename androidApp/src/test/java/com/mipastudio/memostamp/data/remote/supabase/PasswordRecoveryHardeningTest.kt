package com.mipastudio.memostamp.data.remote.supabase

import com.mipastudio.memostamp.data.local.PasswordRecoveryCoordinator
import com.mipastudio.memostamp.data.local.PasswordRecoveryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PasswordRecoveryHardeningTest {

    @Test
    fun exactCanonicalRecoveryUri_withFragment_isAccepted() {
        val testToken = "test_jwt_recovery_access_token_123"
        val testRefresh = "test_jwt_recovery_refresh_token_456"
        val uri = "memostamp://auth/recovery#access_token=$testToken&refresh_token=$testRefresh&type=recovery"

        val result = PasswordRecoveryParser.parseUri(uri)
        assertTrue("Expected valid parse for canonical fragment URI", result.isSuccess)

        val (accessToken, refreshToken) = result.getOrThrow()
        assertEquals(testToken, accessToken)
        assertEquals(testRefresh, refreshToken)
    }

    @Test
    fun exactCanonicalRecoveryUri_withQuery_isAccepted() {
        val testToken = "test_jwt_recovery_access_token_789"
        val uri = "memostamp://auth/recovery?access_token=$testToken&type=recovery"

        val result = PasswordRecoveryParser.parseUri(uri)
        assertTrue("Expected valid parse for canonical query URI", result.isSuccess)

        val (accessToken, refreshToken) = result.getOrThrow()
        assertEquals(testToken, accessToken)
        assertEquals(null, refreshToken)
    }

    @Test
    fun wrongScheme_isRejected() {
        val uri = "https://auth/recovery#access_token=123&type=recovery"
        val result = PasswordRecoveryParser.parseUri(uri)
        assertTrue("Expected failure for non-memostamp scheme", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Scheme") == true)
    }

    @Test
    fun wrongHost_isRejected() {
        val uri = "memostamp://dashboard/recovery#access_token=123&type=recovery"
        val result = PasswordRecoveryParser.parseUri(uri)
        assertTrue("Expected failure for non-auth host", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Host") == true)
    }

    @Test
    fun wrongPath_isRejected() {
        val uri = "memostamp://auth/login#access_token=123&type=recovery"
        val result = PasswordRecoveryParser.parseUri(uri)
        assertTrue("Expected failure for non-recovery path", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Path") == true)
    }

    @Test
    fun wrongType_isRejected() {
        val signupUri = "memostamp://auth/recovery#access_token=123&type=signup"
        val signupResult = PasswordRecoveryParser.parseUri(signupUri)
        assertTrue("Expected failure for signup type", signupResult.isFailure)
        assertTrue(signupResult.exceptionOrNull()?.message?.contains("recovery") == true)

        val magicUri = "memostamp://auth/recovery#access_token=123&type=magiclink"
        val magicResult = PasswordRecoveryParser.parseUri(magicUri)
        assertTrue("Expected failure for magiclink type", magicResult.isFailure)
    }

    @Test
    fun missingToken_isRejected() {
        val uri = "memostamp://auth/recovery#type=recovery"
        val result = PasswordRecoveryParser.parseUri(uri)
        assertTrue("Expected failure for missing token", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Thiếu token") == true)
    }

    @Test
    fun tokenValue_neverAppearsInDiagnosticHelper() {
        val secretToken = "SUPER_SECRET_RECOVERY_BEARER_TOKEN_999"
        val uri = "memostamp://auth/recovery#access_token=$secretToken&type=recovery"

        val sanitized = PasswordRecoveryParser.sanitizeForLogging(uri)
        assertFalse("Secret token must never appear in diagnostic log", sanitized.contains(secretToken))
        assertTrue("Sanitized output must indicate redaction", sanitized.contains("[credentials redacted]"))
        assertEquals("memostamp://auth/recovery [credentials redacted]", sanitized)
    }

    @Test
    fun passwordValidation_enforcesPolicy() {
        // Short password
        val shortResult = PasswordRecoveryParser.validatePassword("12345", "12345")
        assertTrue("Password < 6 chars must be rejected", shortResult.isFailure)

        // Mismatched confirmation
        val mismatchResult = PasswordRecoveryParser.validatePassword("Password123!", "Different123!")
        assertTrue("Mismatched confirm password must be rejected", mismatchResult.isFailure)

        // Valid
        val validResult = PasswordRecoveryParser.validatePassword("Password123!", "Password123!")
        assertTrue("Matching password >= 6 chars must be accepted", validResult.isSuccess)
    }

    @Test
    fun canonicalAuthUidValidation_enforcesStrictUid() {
        val validUuid = UUID.randomUUID().toString()
        assertTrue(PasswordRecoveryParser.isValidCanonicalAuthUid(validUuid))

        assertFalse(PasswordRecoveryParser.isValidCanonicalAuthUid(""))
        assertFalse(PasswordRecoveryParser.isValidCanonicalAuthUid("guest_visitor"))
        assertFalse(PasswordRecoveryParser.isValidCanonicalAuthUid("user_me"))
        assertFalse(PasswordRecoveryParser.isValidCanonicalAuthUid("not-a-uuid"))
    }
}
