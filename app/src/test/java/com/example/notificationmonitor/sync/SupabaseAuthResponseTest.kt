package com.example.notificationmonitor.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseAuthResponseTest {

    @Test
    fun pendingEmailConfirmationReturnsActionableMessage() {
        val result = parseAuthSession(
            body = """
                {
                  "id": "$USER_ID",
                  "aud": "authenticated",
                  "role": "",
                  "email": "ada@example.com"
                }
            """.trimIndent(),
            nowMillis = 0L
        )
        assertTrue(result is AuthSessionParse.Failure)
        assertEquals(
            "Confirm your email in Supabase, then sign in again.",
            (result as AuthSessionParse.Failure).userMessage
        )
    }

    @Test
    fun parsesTokensWhenUserObjectMissingButJwtHasSubject() {
        val result = parseAuthSession(
            body = """
                {
                  "access_token": "$ACCESS_JWT",
                  "refresh_token": "refresh-token",
                  "expires_in": 3600
                }
            """.trimIndent(),
            nowMillis = 1_000L,
            fallbackEmail = "fallback@example.com"
        )
        assertTrue(result is AuthSessionParse.Success)
        val session = (result as AuthSessionParse.Success).session
        assertEquals(USER_ID, session.userId)
        assertEquals("ada@example.com", session.email)
        assertEquals(1_000L + 3_600_000L, session.expiresAtMillis)
    }

    @Test
    fun parsesNestedSessionObject() {
        val result = parseAuthSession(
            body = """
                {
                  "session": {
                    "access_token": "aaa.bbb.ccc",
                    "refresh_token": "ref-1",
                    "expires_in": 60,
                    "user": { "id": "$USER_ID", "email": "ada@example.com" }
                  }
                }
            """.trimIndent(),
            nowMillis = 0L
        )
        assertTrue(result is AuthSessionParse.Success)
        assertEquals(USER_ID, (result as AuthSessionParse.Success).session.userId)
    }

    @Test
    fun surfacesAuthErrorMessageWithoutTokens() {
        val result = parseAuthSession(
            body = """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}""",
            nowMillis = 0L
        )
        assertTrue(result is AuthSessionParse.Failure)
        assertEquals("Invalid login credentials", (result as AuthSessionParse.Failure).userMessage)
    }

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
        private const val ACCESS_JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEiLCJlbWFpbCI6ImFkYUBleGFtcGxlLmNvbSJ9." +
                "signature"
    }
}
