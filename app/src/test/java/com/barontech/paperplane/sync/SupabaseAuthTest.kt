package com.barontech.paperplane.sync

import com.barontech.paperplane.settings.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseAuthTest {

    private val project = SupabaseConfig(
        projectUrl = "https://abc.supabase.co",
        publishableKey = "sb_publishable_test",
        table = "notifications"
    )

    @Test
    fun parsesPasswordGrant() {
        val session = SupabaseAuth.parseSession(
            body = """
                {
                  "access_token": "aaa.bbb",
                  "refresh_token": "ref-1",
                  "expires_at": 2000000000,
                  "user": { "id": "$USER_ID", "email": "ada@example.com" }
                }
            """.trimIndent(),
            nowMillis = 50L
        )
        assertEquals(USER_ID, session?.userId)
        assertEquals("ada@example.com", session?.email)
        assertEquals("aaa.bbb", session?.accessToken)
        assertEquals("ref-1", session?.refreshToken)
        assertEquals(2_000_000_000_000L, session?.expiresAtMillis)
    }

    @Test
    fun signInSendsPublishableKeyOnApiKeyHeaderOnly() {
        val transport = RecordingAuthTransport(
            SupabaseHttpResponse(
                200,
                """
                {"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,
                "user":{"id":"$USER_ID","email":"ada@example.com"}}
                """.trimIndent()
            )
        )
        val attempt = SupabaseAuth(transport).signIn(
            config = project,
            email = "ada@example.com",
            password = "s3cret",
            nowMillis = 1_000L
        )
        assertTrue(attempt is AuthAttempt.Success)
        val call = transport.posts.single()
        assertEquals("https://abc.supabase.co/auth/v1/token?grant_type=password", call.url)
        assertEquals("sb_publishable_test", call.publishableKey)
        assertEquals("sb_publishable_test", call.authorizationBearer)
        assertEquals(false, call.preferMinimal)
        assertTrue(call.body.contains(""""email":"ada@example.com""""))
        assertTrue(call.body.contains(""""password":"s3cret""""))
        val session = (attempt as AuthAttempt.Success).session
        assertEquals(USER_ID, session.userId)
        assertEquals(1_000L + 3_600_000L, session.expiresAtMillis)
    }

    @Test
    fun legacyJwtPublishableKeyStillSendsBearerOnAuth() {
        val legacy = project.copy(publishableKey = LEGACY_ANON_JWT)
        val transport = RecordingAuthTransport(
            SupabaseHttpResponse(
                200,
                """
                {"access_token":"new-access","refresh_token":"new-refresh","expires_in":60,
                "user":{"id":"$USER_ID","email":"ada@example.com"}}
                """.trimIndent()
            )
        )
        SupabaseAuth(transport).signIn(legacy, "ada@example.com", "pw", 0L)
        assertEquals(LEGACY_ANON_JWT, transport.posts.single().authorizationBearer)
    }

    @Test
    fun signInTrimsTrailingNewlineFromPassword() {
        val transport = RecordingAuthTransport(
            SupabaseHttpResponse(
                200,
                """
                {"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,
                "user":{"id":"$USER_ID","email":"ada@example.com"}}
                """.trimIndent()
            )
        )
        val attempt = SupabaseAuth(transport).signIn(
            config = project,
            email = "ada@example.com",
            password = "s3cret\n",
            nowMillis = 0L
        )
        assertTrue(attempt is AuthAttempt.Success)
        assertTrue(transport.posts.single().body.contains(""""password":"s3cret""""))
    }

    @Test
    fun rejectedPasswordDoesNotReturnASession() {
        val transport = RecordingAuthTransport(SupabaseHttpResponse(400, """{"error_description":"Invalid login credentials"}"""))
        val attempt = SupabaseAuth(transport).signIn(project, "ada@example.com", "nope", 0L)
        assertTrue(attempt is AuthAttempt.Failure)
        assertEquals("Invalid login credentials", (attempt as AuthAttempt.Failure).message)
    }

    @Test
    fun refreshPostsTheStoredRefreshToken() {
        val signedIn = project.copy(
            userId = USER_ID,
            accessToken = "old-access",
            refreshToken = "stored-refresh",
            accessTokenExpiresAt = 0L
        )
        val transport = RecordingAuthTransport(
            SupabaseHttpResponse(
                200,
                """
                {"access_token":"new-access","refresh_token":"new-refresh","expires_in":1800,
                "user":{"id":"$USER_ID","email":"ada@example.com"}}
                """.trimIndent()
            )
        )
        val attempt = SupabaseAuth(transport).refresh(signedIn, nowMillis = 5_000L)
        assertTrue(attempt is AuthAttempt.Success)
        val call = transport.posts.single()
        assertEquals("https://abc.supabase.co/auth/v1/token?grant_type=refresh_token", call.url)
        assertTrue(call.body.contains(""""refresh_token":"stored-refresh""""))
        assertEquals("sb_publishable_test", call.authorizationBearer)
        val session = (attempt as AuthAttempt.Success).session
        assertEquals("new-access", session.accessToken)
        assertEquals(5_000L + 1_800_000L, session.expiresAtMillis)
    }

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
        private const val LEGACY_ANON_JWT =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSJ9.sig"
    }
}

private class RecordingAuthTransport(
    private val response: SupabaseHttpResponse
) : SupabaseTransport {
    val posts = mutableListOf<AuthCall>()

    override fun post(
        url: String,
        publishableKey: String,
        body: String,
        authorizationBearer: String?,
        preferMinimal: Boolean,
        prefer: String?
    ): SupabaseHttpResponse {
        posts += AuthCall(url, publishableKey, body, authorizationBearer, preferMinimal)
        return response
    }

    override fun get(
        url: String,
        publishableKey: String,
        authorizationBearer: String?
    ): SupabaseHttpResponse {
        return response
    }
}

private data class AuthCall(
    val url: String,
    val publishableKey: String,
    val body: String,
    val authorizationBearer: String?,
    val preferMinimal: Boolean
)
