package com.example.notificationmonitor.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class NotificationParserTest {

    private lateinit var parser: NotificationParser

    @Before
    fun setUp() {
        parser = NotificationParser(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun parsesTitleAndText() {
        val sbn = fakeSbn(
            packageName = "com.example.chat",
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Alice")
                putCharSequence(Notification.EXTRA_TEXT, "Hello there")
            }
        )

        val entity = parser.parse(sbn)
        assertNotNull(entity)
        assertEquals("com.example.chat", entity!!.packageName)
        assertEquals("Alice", entity.title)
        assertEquals("Hello there", entity.text)
        assertNotNull(entity.notificationKey)
        assertTrue(entity.notificationKey!!.isNotBlank())
        assertFalse(entity.isOngoing)
    }

    @Test
    fun parsesBigText() {
        val sbn = fakeSbn(
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Subject")
                putCharSequence(Notification.EXTRA_BIG_TEXT, "A longer body of text")
            }
        )

        val entity = parser.parse(sbn)!!
        assertEquals("A longer body of text", entity.bigText)
        assertEquals("Subject", entity.title)
    }

    @Test
    fun handlesMissingExtras() {
        val sbn = fakeSbn(extras = Bundle())
        val entity = parser.parse(sbn)!!
        assertNull(entity.title)
        assertNull(entity.text)
        assertNull(entity.subText)
        assertNull(entity.bigText)
        assertTrue(entity.packageName.isNotEmpty())
    }

    @Test
    fun handlesNullOptionalFieldsGracefully() {
        val sbn = fakeSbn(
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_SUB_TEXT, "sub")
            },
            ongoing = true
        )
        val entity = parser.parse(sbn)!!
        assertEquals("sub", entity.subText)
        assertTrue(entity.isOngoing)
        assertTrue(entity.receivedAt > 0)
    }

    private fun fakeSbn(
        packageName: String = "com.example.app",
        extras: Bundle = Bundle(),
        ongoing: Boolean = false
    ): StatusBarNotification {
        val notification = Notification().apply {
            this.extras.putAll(extras)
            if (ongoing) {
                flags = flags or Notification.FLAG_ONGOING_EVENT
            }
            category = Notification.CATEGORY_MESSAGE
        }

        return StatusBarNotification(
            packageName,
            null,
            1,
            null,
            0,
            0,
            0,
            notification,
            android.os.UserHandle.getUserHandleForUid(0),
            1_700_000_000_000L
        ).also {
            // Robolectric StatusBarNotification may not expose key the same way;
            // parser reads sbn.key which Robolectric typically synthesizes.
        }
    }
}
