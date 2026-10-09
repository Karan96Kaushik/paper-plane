package com.example.notificationmonitor.sync

import com.example.notificationmonitor.database.NotificationEntity
import com.example.notificationmonitor.settings.SupabaseConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabasePublisherTest {

    private val ready = SupabaseConfig(
        enabled = true,
        projectUrl = "https://abc.supabase.co/",
        publishableKey = "  sb_publishable_test  ",
        table = "notifications",
        email = "ada@example.com",
        accountEmail = "ada@example.com",
        userId = USER_ID,
        accessToken = "access-token",
        refreshToken = "refresh-token",
        accessTokenExpiresAt = Long.MAX_VALUE
    )

    @Test
    fun skipsPushWhenDisabled() = runTest {
        val transport = RecordingTransport()
        val publisher = publisher(SupabaseConfig(enabled = false), transport)
        val outcome = publisher.push(sample(1))
        assertTrue(outcome is SupabaseOutcome.Disabled)
        assertTrue(transport.posts.isEmpty())
    }

    @Test
    fun postsTrimmedKeyAndPayload() = runTest {
        val transport = RecordingTransport()
        val publisher = publisher(ready, transport)
        val outcome = publisher.push(sample(4, title = "Hello"))
        assertTrue(outcome is SupabaseOutcome.Success)
        val call = transport.posts.single()
        val body = checkNotNull(call.body)
        assertEquals("https://abc.supabase.co/rest/v1/notifications", call.url)
        assertEquals("sb_publishable_test", call.publishableKey)
        assertEquals("access-token", call.authorizationBearer)
        assertTrue(body.startsWith("["))
        assertTrue(body.contains(""""local_id":4"""))
        assertTrue(body.contains(""""user_id":"$USER_ID""""))
        assertTrue(body.contains(""""title":"Hello""""))
        assertTrue(body.contains(""""device_id":"device-1""""))
    }

    @Test
    fun reportsHttpFailureWithoutMarkingRows() = runTest {
        // Use 403, not 401: a 401 triggers a token refresh and a second POST.
        val transport = RecordingTransport().apply {
            response = SupabaseHttpResponse(403, """{"message":"forbidden"}""")
        }
        val publisher = publisher(ready, transport, pageSize = 2)
        val marked = mutableListOf<Long>()
        val pages = ArrayDeque(listOf(listOf(sample(1), sample(2)), listOf(sample(3))))
        val outcome = publisher.pushPending(
            loadPage = { pages.removeFirstOrNull().orEmpty() },
            markSynced = { ids -> marked += ids }
        )
        assertTrue(outcome is SupabaseOutcome.Failure)
        val failure = outcome as SupabaseOutcome.Failure
        assertEquals(403, failure.statusCode)
        assertEquals(0, failure.sent)
        assertTrue(failure.userMessage().contains("Sign in"))
        assertTrue(marked.isEmpty())
        assertEquals(1, transport.posts.size)
    }

    @Test
    fun pushPendingMarksEachSuccessfulPage() = runTest {
        val transport = RecordingTransport()
        val publisher = publisher(ready, transport, pageSize = 2)
        val marked = mutableListOf<List<Long>>()
        val pages = ArrayDeque(
            listOf(
                listOf(sample(1), sample(2)),
                listOf(sample(3))
            )
        )
        val outcome = publisher.pushPending(
            loadPage = { pages.removeFirstOrNull().orEmpty() },
            markSynced = { ids -> marked += ids }
        )
        assertEquals("Sent 3 notifications.", outcome.userMessage())
        assertEquals(listOf(listOf(1L, 2L), listOf(3L)), marked)
        assertEquals(2, transport.posts.size)
    }

    @Test
    fun testConnectionProbesTheTable() = runTest {
        val transport = RecordingTransport().apply {
            response = SupabaseHttpResponse(200, "[]")
        }
        val publisher = publisher(ready, transport)
        val outcome = publisher.test(ready.copy(enabled = false))
        assertEquals("Connected.", outcome.userMessage())
        val call = transport.gets.single()
        assertEquals("https://abc.supabase.co/rest/v1/notifications?limit=0", call.url)
        assertEquals("sb_publishable_test", call.publishableKey)
        assertEquals("access-token", call.authorizationBearer)
    }

    @Test
    fun testConnectionDoesNotCallTheNetworkForInvalidConfig() = runTest {
        val transport = RecordingTransport()
        val publisher = publisher(ready, transport)
        val outcome = publisher.test(ready.copy(projectUrl = "http://insecure.example"))
        assertTrue(outcome is SupabaseOutcome.Failure)
        assertTrue(transport.gets.isEmpty())
    }

    @Test
    fun pushPendingRequiresSignedInUser() = runTest {
        val transport = RecordingTransport()
        val unsigned = ready.copy(
            userId = "",
            accessToken = "",
            refreshToken = "",
            accessTokenExpiresAt = 0L
        )
        val publisher = publisher(unsigned, transport)
        val outcome = publisher.pushPending(
            loadPage = { listOf(sample(1)) },
            markSynced = { }
        )
        assertTrue(outcome is SupabaseOutcome.Failure)
        assertTrue((outcome as SupabaseOutcome.Failure).message.contains("Sign in"))
        assertTrue(transport.posts.isEmpty())
    }

    @Test
    fun testConnectionRequiresSignedInUser() = runTest {
        val transport = RecordingTransport()
        val unsigned = ready.copy(
            userId = "",
            accessToken = "",
            refreshToken = "",
            accessTokenExpiresAt = 0L
        )
        val publisher = publisher(unsigned, transport)
        val outcome = publisher.test(unsigned)
        assertTrue(outcome is SupabaseOutcome.Failure)
        assertTrue((outcome as SupabaseOutcome.Failure).message.contains("Sign in"))
        assertTrue(transport.gets.isEmpty())
    }

    @Test
    fun unauthorizedInsertRetriesRefreshBeforeFailure() = runTest {
        val transport = RecordingTransport().apply {
            response = SupabaseHttpResponse(401, """{"message":"jwt expired"}""")
        }
        val publisher = publisher(ready, transport, pageSize = 2)
        val marked = mutableListOf<Long>()
        val outcome = publisher.pushPending(
            loadPage = { listOf(sample(1), sample(2)) },
            markSynced = { ids -> marked += ids }
        )
        assertTrue(outcome is SupabaseOutcome.Failure)
        val failure = outcome as SupabaseOutcome.Failure
        assertEquals(401, failure.statusCode)
        assertTrue(marked.isEmpty())
        assertEquals(2, transport.posts.size)
        assertTrue(transport.posts[0].url.endsWith("/rest/v1/notifications"))
        assertTrue(transport.posts[1].url.contains("/auth/v1/token"))
    }

    private fun publisher(
        config: SupabaseConfig,
        transport: RecordingTransport,
        pageSize: Int = 25
    ) = SupabasePublisher(
        currentConfig = { config },
        deviceId = "device-1",
        transport = transport,
        pageSize = pageSize
    )

    private fun sample(id: Long, title: String = "t") = NotificationEntity(
        id = id,
        packageName = "com.chat",
        appName = "Chat",
        title = title,
        text = "b",
        subText = null,
        bigText = null,
        category = null,
        notificationKey = null,
        postedAt = id,
        receivedAt = id,
        isOngoing = false,
        isClearable = true
    )

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
    }
}

private data class RecordedCall(
    val url: String,
    val publishableKey: String,
    val authorizationBearer: String?,
    val body: String?
)

private class RecordingTransport : SupabaseTransport {
    val posts = mutableListOf<RecordedCall>()
    val gets = mutableListOf<RecordedCall>()
    var response = SupabaseHttpResponse(201, null)

    override fun post(
        url: String,
        publishableKey: String,
        body: String,
        authorizationBearer: String?,
        preferMinimal: Boolean
    ): SupabaseHttpResponse {
        posts += RecordedCall(url, publishableKey, authorizationBearer, body)
        return response
    }

    override fun get(
        url: String,
        publishableKey: String,
        authorizationBearer: String?
    ): SupabaseHttpResponse {
        gets += RecordedCall(url, publishableKey, authorizationBearer, null)
        return response
    }
}
