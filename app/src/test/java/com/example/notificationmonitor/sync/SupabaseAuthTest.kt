package com.example.notificationmonitor.sync

import com.example.notificationmonitor.settings.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthTest {

    private val project = SupabaseConfig(
        projectUrl = "https://abc.supabase.co",
        apiKey = "anon-key",
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
    fun signInPostsEmailAndPasswordWithTheAnonKey() {
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
        assertEquals("anon-key", call.apiKey)
        assertEquals("anon-key", call.bearerToken)
        assertEquals(false, call.preferMinimal)
        assertTrue(call.body.contains(""""email":"ada@example.com""""))
        assertTrue(call.body.contains(""""password":"s3cret""""))
        val session = (attempt as AuthAttempt.Success).session
        assertEquals(USER_ID, session.userId)
        assertEquals(1_000L + 3_600_000L, session.expiresAtMillis)
    }

    @Test
    fun rejectedPasswordDoesNotReturnASession() {
        val transport = RecordingAuthTransport(SupabaseHttpResponse(400, """{"error_description":"Invalid login credentials"}"""))
        val attempt = SupabaseAuth(transport).signIn(project, "ada@example.com", "nope", 0L)
        assertTrue(attempt is AuthAttempt.Failure)
        assertEquals("Email or password was rejected.", (attempt as AuthAttempt.Failure).message)
    }

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
    }
}

private class RecordingAuthTransport(
    private val response: SupabaseHttpResponse
) : SupabaseTransport {
    val posts = mutableListOf<AuthCall>()

    override fun post(
        url: String,
        apiKey: String,
        bearerToken: String,
        body: String,
        preferMinimal: Boolean
    ): SupabaseHttpResponse {
        posts += AuthCall(url, apiKey, bearerToken, body, preferMinimal)
        return response
    }

    override fun get(url: String, apiKey: String, bearerToken: String): SupabaseHttpResponse {
        return response
    }
}

private data class AuthCall(
    val url: String,
    val apiKey: String,
    val bearerToken: String,
    val body: String,
    val preferMinimal: Boolean
)
