package com.example.notificationmonitor.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.notificationmonitor.database.AppDatabase
import com.example.notificationmonitor.database.NotificationEntity
import com.example.notificationmonitor.settings.RetentionPeriod
import com.example.notificationmonitor.settings.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            com.example.notificationmonitor.database.RepublishRuleEntity(
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
}
