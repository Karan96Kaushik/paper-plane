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
        apiKey = "  anon-key  ",
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
        assertEquals("anon-key", call.apiKey)
        assertEquals("access-token", call.bearerToken)
        assertTrue(body.startsWith("["))
        assertTrue(body.contains(""""local_id":4"""))
        assertTrue(body.contains(""""user_id":"$USER_ID""""))
        assertTrue(body.contains(""""title":"Hello""""))
        assertTrue(body.contains(""""device_id":"device-1""""))
    }

    @Test
    fun reportsHttpFailureWithoutMarkingRows() = runTest {
        val transport = RecordingTransport().apply {
            response = SupabaseHttpResponse(401, """{"message":"bad key"}""")
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
        assertEquals(401, failure.statusCode)
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
        assertEquals("anon-key", call.apiKey)
        assertEquals("access-token", call.bearerToken)
    }

    @Test
    fun testConnectionDoesNotCallTheNetworkForInvalidConfig() = runTest {
        val transport = RecordingTransport()
        val publisher = publisher(ready, transport)
        val outcome = publisher.test(ready.copy(projectUrl = "http://insecure.example"))
        assertTrue(outcome is SupabaseOutcome.Failure)
        assertTrue(transport.gets.isEmpty())
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
    val apiKey: String,
    val bearerToken: String,
    val body: String?
)

private class RecordingTransport : SupabaseTransport {
    val posts = mutableListOf<RecordedCall>()
    val gets = mutableListOf<RecordedCall>()
    var response = SupabaseHttpResponse(201, null)

    override fun post(
        url: String,
        apiKey: String,
        bearerToken: String,
        body: String,
        preferMinimal: Boolean
    ): SupabaseHttpResponse {
        posts += RecordedCall(url, apiKey, bearerToken, body)
        return response
    }

    override fun get(url: String, apiKey: String, bearerToken: String): SupabaseHttpResponse {
        gets += RecordedCall(url, apiKey, bearerToken, null)
        return response
    }
}
