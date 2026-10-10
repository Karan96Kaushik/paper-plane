package com.barontech.paperplane.sync

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
    fun parsesRealisticSupabaseUserPayload() {
        val result = parseAuthSession(
            body = """
                {
                  "access_token": "$ACCESS_JWT",
                  "refresh_token": "v1.refresh-token",
                  "expires_in": 3600,
                  "token_type": "bearer",
                  "user": {
                    "id": "$USER_ID",
                    "aud": "authenticated",
                    "role": "authenticated",
                    "email": "ada@example.com",
                    "email_confirmed_at": "2024-01-01T00:00:00Z",
                    "phone": "",
                    "confirmed_at": "2024-01-01T00:00:00Z",
                    "last_sign_in_at": "2024-01-01T00:00:00Z",
                    "app_metadata": { "provider": "email", "providers": ["email"] },
                    "user_metadata": { "full_name": "Ada" },
                    "identities": [
                      {
                        "identity_id": "22222222-2222-4222-8222-222222222222",
                        "id": "$USER_ID",
                        "user_id": "$USER_ID",
                        "identity_data": {
                          "email": "ada@example.com",
                          "email_verified": true,
                          "sub": "$USER_ID"
                        },
                        "provider": "email",
                        "created_at": "2024-01-01T00:00:00Z",
                        "updated_at": "2024-01-01T00:00:00Z"
                      }
                    ],
                    "created_at": "2024-01-01T00:00:00Z",
                    "updated_at": "2024-01-01T00:00:00Z"
                  }
                }
            """.trimIndent(),
            nowMillis = 2_000L
        )
        assertTrue(result is AuthSessionParse.Success)
        assertEquals(USER_ID, (result as AuthSessionParse.Success).session.userId)
    }

    @Test
    fun parsesTokensWhenUserJsonTailIsTruncated() {
        val userId = "fbeb92d1-941e-494b-9441-fbeb92d1941e"
        val jwt =
            "eyJhbGciOiJFUzI1NiIsImtpZCI6IjczYmJmMjAyLWUyNDEtNGZhOC1hYTgxLTE2MjdkOGQxYjk3MSIsInR5cCI6IkpXVCJ9." +
                "eyJpc3MiOiJodHRwczovL2V4YW1wbGUuc3VwYWJhc2UuY28vYXV0aC92MSIsInN1YiI6ImZiZWI5MmQxLTk0MWUtNDk0Yi05NDQxLWZiZWI5MmQxOTQxZSIsImVtYWlsIjoiYWRhQGV4YW1wbGUuY29tIn0." +
                "signature"
        val truncated = """
            {
              "access_token": "$jwt",
              "refresh_token": "v1.local-refresh",
              "expires_in": 3600,
              "token_type": "bearer",
              "user": {
                "id": "$userId",
                "email": "ada@example.com",
                "identities": [
                  { "identity_id": "22222222-2222-4222-8222-222222222222",
            """.trimIndent()
        val result = parseAuthSession(truncated, nowMillis = 500L)
        assertTrue(result is AuthSessionParse.Success)
        val session = (result as AuthSessionParse.Success).session
        assertEquals(userId, session.userId)
        assertEquals("ada@example.com", session.email)
        assertEquals(500L + 3_600_000L, session.expiresAtMillis)
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
