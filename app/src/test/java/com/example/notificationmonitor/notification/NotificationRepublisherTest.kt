package com.example.notificationmonitor.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.notificationmonitor.database.AppDatabase
import com.example.notificationmonitor.database.NotificationEntity
import com.example.notificationmonitor.database.RepublishRuleEntity
import com.example.notificationmonitor.repository.NotificationRepository
import com.example.notificationmonitor.settings.UserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationRepublisherTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: NotificationRepository
    private lateinit var poster: FakePoster
    private lateinit var republisher: NotificationRepublisher
    private var now = 10_000L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotificationRepository(
            database = database,
            preferences = UserPreferences(context),
            appContext = context
        )
        repository.setAutoRepublishEnabled(true)
        repository.saveRepublishRule(
            RepublishRuleEntity(
                packageName = "com.chat",
                enabled = true,
                allTypes = false,
                categoriesCsv = NotificationType.MESSAGE.storageKey,
                includeOngoing = false
            )
        )
        poster = FakePoster()
        republisher = NotificationRepublisher(
            repository = repository,
            poster = poster,
            dedupeWindowMillis = 1_000L,
            postLimit = 3,
            scanLimit = 20,
            clock = { now }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun newNotificationsRepublishOncePerKeyInsideTheWindow() = runTest {
        val first = insert(sample(key = "same", postedAt = 1L))
        republisher.onNewNotification(sample(key = "same", postedAt = 1L).copy(id = first))
        assertEquals(listOf(first), poster.ids)

        val second = insert(sample(key = "same", title = "Update", postedAt = 2L))
        republisher.onNewNotification(sample(key = "same", title = "Update", postedAt = 2L).copy(id = second))
        assertEquals(listOf(first), poster.ids)

        now += 1_001L
        republisher.onNewNotification(sample(key = "same", title = "Update", postedAt = 2L).copy(id = second))
        assertEquals(listOf(first, second), poster.ids)
    }

    @Test
    fun automaticRepublishStopsWhenMasterSwitchIsOff() = runTest {
        repository.setAutoRepublishEnabled(false)
        val id = insert(sample(key = "one"))
        republisher.onNewNotification(sample(key = "one").copy(id = id))
        assertTrue(poster.ids.isEmpty())
    }

    @Test
    fun pastRepublishPostsSelectedTypesAndSkipsOngoingAndAlreadyRepublished() = runTest {
        val message = insert(sample(title = "Hi", postedAt = 5L, key = "m1"))
        insert(sample(title = "Mail", category = "email", postedAt = 4L, key = "e1"))
        insert(sample(title = "Playing", ongoing = true, postedAt = 3L, key = "o1"))
        val already = insert(sample(title = "Old", postedAt = 2L, key = "m2"))
        repository.markRepublished(already, 50L)

        val skipped = republisher.republishPast("com.chat", includeAlreadyRepublished = false)
        assertEquals(1, skipped.posted)
        assertEquals(1, skipped.skippedAlready)
        assertEquals(listOf(message), poster.ids)

        poster.ids.clear()
        val again = republisher.republishPast("com.chat", includeAlreadyRepublished = true)
        assertEquals(2, again.posted)
        assertTrue(poster.ids.contains(already))
    }

    @Test
    fun pastRepublishStopsAtTheBatchLimit() = runTest {
        val ids = (1..5).map { index ->
            insert(sample(title = "t$index", key = "k$index", postedAt = index.toLong()))
        }
        val result = republisher.republishPast("com.chat", includeAlreadyRepublished = false)
        assertEquals(3, result.posted)
        assertEquals(2, result.notPostedDueToLimit)
        assertEquals(ids.takeLast(3).reversed(), poster.ids)
    }

    @Test
    fun explicitSelectionIgnoresTypeRules() = runTest {
        val emailId = insert(sample(title = "Mail", category = "email", key = "mail"))
        val result = republisher.republishIds(listOf(emailId))
        assertEquals(1, result.posted)
        assertEquals(listOf(emailId), poster.ids)
    }

    @Test
    fun emptyNotificationsAreSkipped() = runTest {
        val id = insert(sample(title = null, text = null, key = "empty"))
        val result = republisher.republishIds(listOf(id))
        assertEquals(0, result.posted)
        assertEquals(1, result.skippedEmpty)
        assertTrue(poster.ids.isEmpty())
        assertNull(database.notificationDao().getById(id)?.republishedAt)
    }

    @Test
    fun missingPermissionDoesNotMarkNotifications() = runTest {
        poster.allow = false
        val id = insert(sample(key = "blocked"))
        val result = republisher.republishIds(listOf(id))
        assertTrue(result.permissionDenied)
        assertEquals(0, result.posted)
        assertNull(database.notificationDao().getById(id)?.republishedAt)
    }

    private suspend fun insert(entity: NotificationEntity): Long =
        repository.insertIfAllowed(entity)!!

    private fun sample(
        title: String? = "Hello",
        text: String? = "Body",
        category: String? = "msg",
        key: String? = "key",
        ongoing: Boolean = false,
        postedAt: Long = 10L
    ) = NotificationEntity(
        packageName = "com.chat",
        appName = "Chat",
        title = title,
        text = text,
        subText = null,
        bigText = null,
        category = category,
        notificationKey = key,
        postedAt = postedAt,
        receivedAt = postedAt,
        isOngoing = ongoing,
        isClearable = !ongoing
    )

    private class FakePoster : NotificationPoster {
        val ids = mutableListOf<Long>()
        var allow = true

        override fun canPostNotifications(): Boolean = allow

        override fun showRepublished(
            entityId: Long,
            title: String,
            message: String,
            bigText: String?,
            subText: String?,
            category: String?
        ): Boolean {
            if (!allow) return false
            ids += entityId
            return true
        }
    }
}
