package com.barontech.paperplane.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.barontech.paperplane.database.AppDatabase
import com.barontech.paperplane.database.NotificationEntity
import com.barontech.paperplane.settings.DedupeWindow
import com.barontech.paperplane.settings.RetentionPeriod
import com.barontech.paperplane.sync.SupabaseFilterField
import com.barontech.paperplane.settings.SupabaseConfig
import com.barontech.paperplane.settings.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotificationRepository(
            database = database,
            preferences = UserPreferences(context),
            appContext = context
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieve() = runTest {
        val entity = sampleNotification(packageName = "com.whatsapp", title = "John")
        assertNotNull(repository.insertIfAllowed(entity))

        val items = repository.observeNotifications().first()
        assertEquals(1, items.size)
        assertEquals("John", items.first().title)
    }

    @Test
    fun filteringByPackage() = runTest {
        repository.insertIfAllowed(sampleNotification(packageName = "com.whatsapp", title = "A"))
        repository.insertIfAllowed(sampleNotification(packageName = "com.telegram", title = "B"))

        val whatsapp = repository.observeNotificationsByPackage("com.whatsapp").first()
        assertEquals(1, whatsapp.size)
        assertEquals("A", whatsapp.first().title)
    }

    @Test
    fun deletionAndClearHistory() = runTest {
        repository.insertIfAllowed(sampleNotification(title = "One"))
        repository.insertIfAllowed(sampleNotification(title = "Two", key = "k2"))
        val items = repository.observeNotifications().first()
        repository.delete(items.first())
        assertEquals(1, repository.observeCount().first())

        repository.clearHistory()
        assertEquals(0, repository.observeCount().first())
    }

    @Test
    fun retentionDeletesOldRows() = runTest {
        val old = sampleNotification(
            title = "old",
            postedAt = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000
        )
        val recent = sampleNotification(
            title = "recent",
            key = "recent",
            postedAt = System.currentTimeMillis()
        )
        repository.insertIfAllowed(old)
        repository.insertIfAllowed(recent)

        repository.setRetentionPeriod(RetentionPeriod.SEVEN_DAYS)
        val deleted = repository.applyRetentionCleanup()
        assertEquals(1, deleted)
        assertEquals(1, repository.observeCount().first())
        assertEquals("recent", repository.observeNotifications().first().first().title)
    }

    @Test
    fun disabledAppNotificationsAreIgnored() = runTest {
        repository.ensureAppTracked("com.slack", "Slack")
        repository.setAppEnabled("com.slack", false)

        val stored = repository.insertIfAllowed(
            sampleNotification(packageName = "com.slack", title = "Ignored")
        )
        assertNull(stored)
        assertEquals(0, repository.observeCount().first())
    }

    @Test
    fun enabledAppNotificationsAreStored() = runTest {
        repository.ensureAppTracked("com.gmail", "Gmail")
        repository.setAppEnabled("com.gmail", true)

        val stored = repository.insertIfAllowed(
            sampleNotification(packageName = "com.gmail", title = "Mail")
        )
        assertNotNull(stored)
        assertEquals(1, repository.observeCount().first())
    }

    @Test
    fun republishRuleAndMarkRepublished() = runTest {
        repository.saveRepublishRule(
            com.barontech.paperplane.database.RepublishRuleEntity(
                packageName = "com.whatsapp",
                enabled = true,
                allTypes = false,
                categoriesCsv = "msg,email",
                includeOngoing = false
            )
        )
        val rule = repository.getRepublishRule("com.whatsapp")
        assertEquals(true, rule?.enabled)
        assertEquals(setOf("msg", "email"), rule?.selectedTypes()?.map { it.storageKey }?.toSet())

        val id = repository.insertIfAllowed(sampleNotification(packageName = "com.whatsapp"))
        assertNotNull(id)
        repository.markRepublished(id!!, 1234L)
        assertEquals(1234L, repository.observeNotification(id).first()?.republishedAt)
        assertEquals(1, repository.countRepublishedKeySince("key-1", 1000L))
    }

    @Test
    fun supabaseSyncResetsWhenTheProjectChanges() = runTest {
        val first = repository.insertIfAllowed(sampleNotification(key = "a", postedAt = 1L))
        val second = repository.insertIfAllowed(sampleNotification(key = "b", postedAt = 2L))
        assertNotNull(first)
        assertNotNull(second)
        val projectA = SupabaseConfig(
            enabled = true,
            projectUrl = "https://one.supabase.co",
            publishableKey = "sb_publishable_a",
            table = "notifications"
        )
        repository.saveSupabaseConfig(projectA)
        repository.markSupabaseSynced(listOf(first!!, second!!))
        assertTrue(repository.unsyncedNotifications(10).isEmpty())

        repository.saveSupabaseConfig(projectA.copy(publishableKey = "sb_publishable_b"))
        assertTrue(repository.unsyncedNotifications(10).isEmpty())

        repository.saveSupabaseConfig(
            projectA.copy(projectUrl = "https://two.supabase.co")
        )
        assertEquals(
            listOf(first, second),
            repository.unsyncedNotifications(10).map { it.id }
        )
    }

    @Test
    fun supabaseSyncResetsWhenSignedInUserChanges() = runTest {
        val id = repository.insertIfAllowed(sampleNotification(key = "a", postedAt = 1L))
        assertNotNull(id)
        val userA = SupabaseConfig(
            enabled = true,
            projectUrl = "https://one.supabase.co",
            publishableKey = "sb_publishable_a",
            table = "notifications",
            userId = USER_A,
            accessToken = "access-a",
            refreshToken = "refresh-a",
            accessTokenExpiresAt = Long.MAX_VALUE
        )
        repository.replaceSupabaseConfig(userA)
        repository.markSupabaseSynced(listOf(id!!))
        assertTrue(repository.unsyncedNotifications(10).isEmpty())

        repository.replaceSupabaseConfig(
            userA.copy(
                userId = USER_B,
                accessToken = "access-b",
                refreshToken = "refresh-b"
            )
        )
        assertEquals(listOf(id), repository.unsyncedNotifications(10).map { it.id })
    }

    @Test
    fun sameNotificationInsideTheWindowIsIgnored() = runTest {
        repository.setDedupeWindow(DedupeWindow.ONE_MINUTE)
        val now = System.currentTimeMillis()
        val first = repository.insertIfAllowed(sampleNotification(title = "OTP", key = "k", postedAt = now))
        val second = repository.insertIfAllowed(sampleNotification(title = "OTP", key = "k", postedAt = now))
        assertNotNull(first)
        assertNull(second)
        assertEquals(1, repository.observeCount().first())
    }

    @Test
    fun sameNotificationOutsideTheWindowIsKept() = runTest {
        repository.setDedupeWindow(DedupeWindow.ONE_MINUTE)
        val now = System.currentTimeMillis()
        val first = repository.insertIfAllowed(
            sampleNotification(title = "OTP", key = "k", postedAt = now).copy(
                receivedAt = now - DedupeWindow.ONE_MINUTE.millis - 1_000
            )
        )
        val second = repository.insertIfAllowed(sampleNotification(title = "OTP", key = "k", postedAt = now))
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(2, repository.observeCount().first())
    }

    @Test
    fun changedTextOrKeyIsNotADuplicate() = runTest {
        repository.setDedupeWindow(DedupeWindow.ONE_MINUTE)
        val now = System.currentTimeMillis()
        assertNotNull(repository.insertIfAllowed(sampleNotification(title = "OTP", key = "k", postedAt = now)))
        assertNotNull(repository.insertIfAllowed(sampleNotification(title = "OTP 2", key = "k", postedAt = now)))
        assertNotNull(repository.insertIfAllowed(sampleNotification(title = "OTP", key = "other", postedAt = now)))
        assertEquals(3, repository.observeCount().first())
    }

    @Test
    fun dedupeOffKeepsRepeatReads() = runTest {
        repository.setDedupeWindow(DedupeWindow.OFF)
        val now = System.currentTimeMillis()
        assertNotNull(repository.insertIfAllowed(sampleNotification(title = "OTP", key = "k", postedAt = now)))
        assertNotNull(repository.insertIfAllowed(sampleNotification(title = "OTP", key = "k", postedAt = now)))
        assertEquals(2, repository.observeCount().first())
        repository.setDedupeWindow(DedupeWindow.ONE_MINUTE)
    }

    @Test
    fun excludedNotificationsLeaveTheUploadQueueUntilRulesChange() = runTest {
        val id = repository.insertIfAllowed(sampleNotification(title = "OTP 123"))
        repository.markSupabaseExcluded(listOf(id!!))
        assertTrue(repository.unsyncedNotifications(10).isEmpty())

        repository.addSupabaseExclusionRule(SupabaseFilterField.TITLE, "*otp*")
        assertEquals(listOf(id), repository.unsyncedNotifications(10).map { it.id })

        repository.markSupabaseExcluded(listOf(id))
        repository.deleteSupabaseExclusionRule(
            repository.observeSupabaseExclusionRules().first().single().id
        )
        assertEquals(listOf(id), repository.unsyncedNotifications(10).map { it.id })
    }

    private fun sampleNotification(
        packageName: String = "com.example.app",
        title: String? = "Title",
        key: String? = "key-1",
        postedAt: Long = System.currentTimeMillis()
    ) = NotificationEntity(
        packageName = packageName,
        appName = "App",
        title = title,
        text = "Body",
        subText = null,
        bigText = null,
        category = "msg",
        notificationKey = key,
        postedAt = postedAt,
        receivedAt = System.currentTimeMillis(),
        isOngoing = false,
        isClearable = true
    )

    companion object {
        private const val USER_A = "11111111-1111-4111-8111-111111111111"
        private const val USER_B = "22222222-2222-4222-8222-222222222222"
    }
}
