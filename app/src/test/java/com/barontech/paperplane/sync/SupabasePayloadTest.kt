package com.barontech.paperplane.sync

import com.barontech.paperplane.database.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabasePayloadTest {

    @Test
    fun escapesNotificationTextAndNulls() {
        val payload = notificationPayload(
            notification = NotificationEntity(
                id = 7,
                packageName = "com.chat",
                appName = null,
                title = "Say \"hi\"\nthere",
                text = "a\\b",
                subText = null,
                bigText = null,
                category = "msg",
                notificationKey = "k",
                postedAt = 10,
                receivedAt = 11,
                isOngoing = false,
                isClearable = true
            ),
            deviceId = "pixel",
            userId = "11111111-1111-4111-8111-111111111111"
        )

        assertTrue(payload.contains(""""local_id":7"""))
        assertTrue(payload.contains(""""user_id":"11111111-1111-4111-8111-111111111111""""))
        assertTrue(payload.contains(""""device_id":"pixel""""))
        assertTrue(payload.contains(""""app_name":null"""))
        assertTrue(payload.contains(""""title":"Say \"hi\"\nthere""""))
        assertTrue(payload.contains(""""text":"a\\b""""))
        assertTrue(payload.contains(""""is_ongoing":false"""))
        assertTrue(payload.contains(""""is_clearable":true"""))
        assertTrue(payload.contains(""""posted_at":10"""))
    }

    @Test
    fun deviceTokenPayloadUsesTheSignedInUserAndOmitsABadDeviceId() {
        val token = "a".repeat(32)
        val payload = deviceTokenPayload(
            userId = "11111111-1111-4111-8111-111111111111",
            token = token,
            deviceId = null,
            updatedAt = "2026-10-10T06:29:00Z"
        )
        assertTrue(payload.contains(""""user_id":"11111111-1111-4111-8111-111111111111""""))
        assertTrue(payload.contains(""""token":"$token""""))
        assertTrue(payload.contains(""""platform":"android""""))
        assertTrue(payload.contains(""""device_id":null"""))
        assertTrue(payload.contains(""""updated_at":"2026-10-10T06:29:00Z""""))
        assertEquals(null, normalizedFcmToken("short"))
        assertEquals("a".repeat(32), normalizedFcmToken(" ${"a".repeat(32)} "))
        assertEquals(null, normalizedDeviceId(""))
        assertEquals("pixel", normalizedDeviceId("pixel"))
    }

    @Test
    fun wrapsSeveralRowsInAnArray() {
        val body = notificationPayloadArray(
            notifications = listOf(sample(1), sample(2)),
            deviceId = "pixel",
            userId = "11111111-1111-4111-8111-111111111111"
        )
        assertTrue(body.startsWith("["))
        assertTrue(body.endsWith("]"))
        assertEquals(2, body.split(""""local_id"""").size - 1)
    }

    private fun sample(id: Long) = NotificationEntity(
        id = id,
        packageName = "com.chat",
        appName = "Chat",
        title = "t",
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
}
