package com.barontech.paperplane.sync

import com.barontech.paperplane.database.NotificationEntity
import com.barontech.paperplane.settings.SupabaseConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun pushPendingSkipsNotificationsThatMatchAnExclusionRule() = runTest {
        val transport = RecordingTransport()
        val publisher = publisher(
            config = ready,
            transport = transport,
            pageSize = 10,
            rules = listOf(
                SupabaseExclusionRule(
                    id = 1,
                    enabled = true,
                    field = SupabaseFilterField.TITLE,
                    pattern = "*otp*"
                ),
                SupabaseExclusionRule(
                    id = 2,
                    enabled = true,
                    field = SupabaseFilterField.APP_NAME,
                    pattern = "bank"
                )
            )
        )
        val marked = mutableListOf<Long>()
        val excluded = mutableListOf<Long>()
        val pages = ArrayDeque(
            listOf(
                listOf(
                    sample(1, title = "Your OTP is 1234"),
                    sample(2, title = "Hello", appName = "Bank"),
                    sample(3, title = "Lunch")
                )
            )
        )
        val outcome = publisher.pushPending(
            loadPage = { pages.removeFirstOrNull().orEmpty() },
            markSynced = { ids -> marked += ids },
            markExcluded = { ids -> excluded += ids }
        )
        assertEquals(
            "Sent 1 notification. 2 matched an exclusion rule and were not uploaded.",
            outcome.userMessage()
        )
        assertEquals(listOf(3L), marked)
        assertEquals(listOf(1L, 2L), excluded)
        val body = checkNotNull(transport.posts.single().body)
        assertTrue(body.contains(""""local_id":3"""))
        assertTrue(!body.contains(""""local_id":1"""))
        assertTrue(!body.contains(""""local_id":2"""))
    }

    @Test
    fun pushPendingStopsWhenExcludedRowsStayQueued() = runTest {
        val transport = RecordingTransport()
        val publisher = publisher(
            config = ready,
            transport = transport,
            rules = listOf(
                SupabaseExclusionRule(
                    id = 1,
                    enabled = true,
                    field = SupabaseFilterField.TEXT,
                    pattern = "secret*"
                )
            )
        )
        var loads = 0
        val outcome = publisher.pushPending(
            loadPage = {
                loads += 1
                listOf(sample(9, text = "Secret code"))
            },
            markSynced = {},
            markExcluded = {}
        )
        assertEquals("1 matched an exclusion rule and was not uploaded.", outcome.userMessage())
        assertEquals(2, loads)
        assertTrue(transport.posts.isEmpty())
    }

    @Test
    fun signInUpsertsTheFcmTokenForTheSignedInUser() = runTest {
        val transport = RecordingTransport()
        transport.responseFor = { url ->
            if (url.contains("/auth/v1/token")) {
                SupabaseHttpResponse(200, passwordGrant())
            } else {
                SupabaseHttpResponse(201, null)
            }
        }
        val publisher = publisher(
            config = ready,
            transport = transport,
            clock = { CLOCK },
            registrationToken = { FCM_TOKEN }
        )
        val outcome = publisher.signIn(ready, "ada@example.com", "s3cret")
        assertEquals("Signed in as ada@example.com.", outcome.userMessage())
        val call = transport.posts.single { it.url.contains("device_tokens") }
        assertEquals(
            "https://abc.supabase.co/rest/v1/device_tokens?on_conflict=user_id,token",
            call.url
        )
        assertEquals("sb_publishable_test", call.publishableKey)
        assertEquals("new-access", call.authorizationBearer)
        assertEquals("resolution=merge-duplicates,return=minimal", call.prefer)
        val body = checkNotNull(call.body)
        assertTrue(body.contains(""""user_id":"$USER_ID""""))
        assertTrue(body.contains(""""token":"$FCM_TOKEN""""))
        assertTrue(body.contains(""""platform":"android""""))
        assertTrue(body.contains(""""device_id":"device-1""""))
        assertTrue(body.contains(""""updated_at":"${java.time.Instant.ofEpochMilli(CLOCK)}""""))
    }

    @Test
    fun signInSucceedsWhenThePushTokenIsNotReady() = runTest {
        val transport = RecordingTransport()
        transport.responseFor = { SupabaseHttpResponse(200, passwordGrant()) }
        val publisher = publisher(ready, transport)
        val outcome = publisher.signIn(ready, "ada@example.com", "s3cret")
        assertEquals("Signed in as ada@example.com.", outcome.userMessage())
        assertTrue(transport.posts.none { it.url.contains("device_tokens") })
    }

    @Test
    fun signInKeepsTheSessionWhenThePushTokenIsInvalid() = runTest {
        val transport = RecordingTransport()
        transport.responseFor = { SupabaseHttpResponse(200, passwordGrant()) }
        val publisher = publisher(
            config = ready,
            transport = transport,
            registrationToken = { "short" }
        )
        val outcome = publisher.signIn(ready, "ada@example.com", "s3cret")
        assertTrue(outcome is SupabaseOutcome.Success)
        assertTrue(outcome.userMessage().contains("Signed in as ada@example.com."))
        assertTrue(outcome.userMessage().contains("The push token is not valid."))
        assertTrue(transport.posts.none { it.url.contains("device_tokens") })
    }

    @Test
    fun upsertDeviceTokenSkipsWhenSignedOut() = runTest {
        val transport = RecordingTransport()
        val unsigned = ready.copy(
            userId = "",
            accessToken = "",
            refreshToken = "",
            accessTokenExpiresAt = 0L
        )
        val publisher = publisher(unsigned, transport)
        val outcome = publisher.upsertDeviceToken(FCM_TOKEN)
        assertEquals("Not signed in.", outcome.userMessage())
        assertTrue(transport.posts.isEmpty())
    }

    @Test
    fun upsertDeviceTokenRefreshesAfterUnauthorized() = runTest {
        val transport = RecordingTransport()
        var calls = 0
        transport.responseFor = {
            calls += 1
            when (calls) {
                1 -> SupabaseHttpResponse(401, """{"message":"jwt expired"}""")
                2 -> SupabaseHttpResponse(200, passwordGrant())
                else -> SupabaseHttpResponse(201, null)
            }
        }
        val publisher = publisher(ready, transport, clock = { CLOCK })
        val outcome = publisher.upsertDeviceToken(FCM_TOKEN)
        assertEquals("Device token saved.", outcome.userMessage())
        val devicePosts = transport.posts.filter { it.url.contains("device_tokens") }
        assertEquals(2, devicePosts.size)
        assertEquals("access-token", devicePosts[0].authorizationBearer)
        assertEquals("new-access", devicePosts[1].authorizationBearer)
        assertTrue(transport.posts.any { it.url.contains("/auth/v1/token") })
    }

    private fun passwordGrant(): String = """
        {"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,
        "user":{"id":"$USER_ID","email":"ada@example.com"}}
    """.trimIndent()

    private fun publisher(
        config: SupabaseConfig,
        transport: RecordingTransport,
        pageSize: Int = 25,
        rules: List<SupabaseExclusionRule> = emptyList(),
        clock: () -> Long = { System.currentTimeMillis() },
        registrationToken: suspend () -> String? = { null }
    ) = SupabasePublisher(
        currentConfig = { config },
        deviceId = "device-1",
        transport = transport,
        pageSize = pageSize,
        clock = clock,
        exclusionRules = { rules },
        registrationToken = registrationToken
    )

    private fun sample(
        id: Long,
        title: String = "t",
        text: String? = "b",
        appName: String? = "Chat"
    ) = NotificationEntity(
        id = id,
        packageName = "com.chat",
        appName = appName,
        title = title,
        text = text,
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
        private const val FCM_TOKEN = "abcdefghijklmnopqrstuvwxyz012345"
        private const val CLOCK = 1_700_000_000_000L
    }
}

private data class RecordedCall(
    val url: String,
    val publishableKey: String,
    val authorizationBearer: String?,
    val body: String?,
    val prefer: String? = null
)

private class RecordingTransport : SupabaseTransport {
    val posts = mutableListOf<RecordedCall>()
    val gets = mutableListOf<RecordedCall>()
    var response = SupabaseHttpResponse(201, null)
    var responseFor: (String) -> SupabaseHttpResponse = { response }

    override fun post(
        url: String,
        publishableKey: String,
        body: String,
        authorizationBearer: String?,
        preferMinimal: Boolean,
        prefer: String?
    ): SupabaseHttpResponse {
        posts += RecordedCall(url, publishableKey, authorizationBearer, body, prefer)
        return responseFor(url)
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
