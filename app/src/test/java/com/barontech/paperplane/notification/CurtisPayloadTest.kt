package com.barontech.paperplane.notification

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurtisPayloadTest {

    @Test
    fun requiresVersionTitleAndBody() {
        assertNull(CurtisPayload.parse(sample(version = null)))
        assertNull(CurtisPayload.parse(sample(version = "2")))
        assertNull(CurtisPayload.parse(sample(title = " ")))
        assertNull(CurtisPayload.parse(sample(body = "")))
        val payload = CurtisPayload.parse(sample())
        assertEquals("Ship it", payload!!.title)
        assertEquals("The build finished", payload.body)
    }

    @Test
    fun sameKeyUpdatesOneId() {
        val first = CurtisPayload.parse(sample(key = "run-7"))!!
        val second = CurtisPayload.parse(sample(key = "run-7", body = "Still going"))!!
        assertEquals(first.notificationId, second.notificationId)
        assertNotEquals(
            first.notificationId,
            CurtisPayload.parse(sample(key = "run-8"))!!.notificationId
        )
    }

    @Test
    fun silentUsesQuietChannelAndUnknownChannelFallsBack() {
        assertEquals(
            CurtisPayload.CHANNEL_QUIET,
            CurtisPayload.parse(sample(extra = mapOf("curtis_silent" to "1", "curtis_channel_id" to "curtis_urgent")))!!.channelId
        )
        assertEquals(
            CurtisPayload.CHANNEL_DEFAULT,
            CurtisPayload.parse(sample(extra = mapOf("curtis_channel_id" to "nope")))!!.channelId
        )
        assertEquals(
            CurtisPayload.CHANNEL_URGENT,
            CurtisPayload.parse(sample(extra = mapOf("curtis_channel_id" to "curtis_urgent")))!!.channelId
        )
    }

    @Test
    fun mapsCategoryAndLeavesMissingCategoryUnset() {
        assertEquals("msg", CurtisPayload.parse(sample(extra = mapOf("curtis_category" to "msg")))!!.category)
        assertEquals("status", CurtisPayload.parse(sample(extra = mapOf("curtis_category" to "other")))!!.category)
        assertNull(CurtisPayload.parse(sample())!!.category)
    }

    @Test
    fun progressAndShortText() {
        val payload = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_style" to "progress",
            "curtis_progress_current" to "3",
            "curtis_progress_max" to "10",
            "curtis_short_critical_text" to "123456789",
            "curtis_ongoing" to "1",
            "curtis_request_promoted" to "1"
        )))!!
        assertEquals(CurtisStyle.PROGRESS, payload.style)
        assertEquals(3, payload.progressCurrent)
        assertEquals(10, payload.progressMax)
        assertFalse(payload.progressIndeterminate)
        assertEquals("12345678", payload.shortCriticalText)
        assertTrue(payload.ongoing)
        assertTrue(payload.requestPromoted)
    }

    @Test
    fun malformedProgressIsIndeterminate() {
        val payload = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_style" to "progress",
            "curtis_progress_current" to "nope",
            "curtis_progress_max" to "0"
        )))!!
        assertTrue(payload.progressIndeterminate)
    }

    @Test
    fun malformedActionsDoNotFailThePayload() {
        val payload = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_actions" to "[{",
            "run_id" to "job-1"
        )))!!
        assertTrue(payload.actions.isEmpty())
        assertEquals("job-1", payload.extras["run_id"])
        assertFalse(payload.extras.containsKey("curtis_title"))
    }

    @Test
    fun actionsKeepKnownIdsOnlyAndCapAtThree() {
        val payload = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_actions" to """
                [
                  {"id":"nope","label":"Skip","url":"https://evil.example"},
                  {"id":"open","label":" Open "},
                  {"id":"open","label":"Again"},
                  {"id":"snooze","label":"Snooze"},
                  {"id":"mark_done","label":"Done"},
                  {"id":"view_issue","label":"Issue"}
                ]
            """.trimIndent()
        )))!!
        assertEquals(
            listOf(CurtisActionId.OPEN, CurtisActionId.SNOOZE, CurtisActionId.MARK_DONE),
            payload.actions.map { it.id }
        )
        assertEquals("Open", payload.actions.first().label)
    }

    @Test
    fun actionLabelIsNotALink() {
        val payload = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_actions" to """[{"id":"open","label":"https://evil.example"}]"""
        )))!!
        assertEquals(
            CurtisDestination.InApp("home"),
            CurtisLinks.resolve(payload, CurtisActionId.OPEN)
        )
    }

    @Test
    fun parsesWhenAndExpiry() {
        val payload = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_when" to "2026-10-11T15:00:00+01:00",
            "curtis_expires_at" to "not-a-date"
        )))!!
        val expected = OffsetDateTime.parse("2026-10-11T15:00:00+01:00").toInstant().toEpochMilli()
        assertEquals(expected, payload.whenMillis)
        assertNull(payload.expiresAtMillis)
        assertFalse(payload.isExpired())

        val expired = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_expires_at" to "2000-01-01T00:00:00Z"
        )))!!
        assertTrue(expired.isExpired())
    }

    @Test
    fun deepLinksStayOnAllowlist() {
        val history = CurtisPayload.parse(sample(extra = mapOf("curtis_deep_link" to "/history")))!!
        assertEquals(CurtisDestination.InApp("history"), CurtisLinks.resolve(history, CurtisActionId.OPEN))

        val run = CurtisPayload.parse(sample(extra = mapOf("curtis_deep_link" to "/agents/runs/abc-1")))!!
        assertEquals(
            CurtisDestination.InApp("curtis_run/abc-1"),
            CurtisLinks.resolve(run, CurtisActionId.OPEN)
        )

        val appLink = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_deep_link" to "https://paperplane.app/chat/conv_1"
        )))!!
        assertEquals(
            CurtisDestination.InApp("curtis_chat/conv_1"),
            CurtisLinks.resolve(appLink, CurtisActionId.OPEN)
        )

        val issue = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_deep_link" to "https://github.com/acme/app/issues/4"
        )))!!
        assertEquals(
            CurtisDestination.External("https://github.com/acme/app/issues/4"),
            CurtisLinks.resolve(issue, CurtisActionId.VIEW_ISSUE)
        )

        val jira = CurtisPayload.parse(sample(extra = mapOf(
            "issue_url" to "https://acme.atlassian.net/browse/APP-1"
        )))!!
        assertEquals(
            CurtisDestination.External("https://acme.atlassian.net/browse/APP-1"),
            CurtisLinks.resolve(jira, CurtisActionId.VIEW_ISSUE)
        )

        val mail = CurtisPayload.parse(sample(extra = mapOf("link" to "mailto:ada@example.com")))!!
        assertEquals(
            CurtisDestination.External("mailto:ada@example.com"),
            CurtisLinks.resolve(mail, CurtisActionId.OPEN_DRAFT)
        )

        val whatsapp = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_deep_link" to "https://wa.me/15551212"
        )))!!
        assertEquals(
            CurtisDestination.External("https://wa.me/15551212"),
            CurtisLinks.resolve(whatsapp, CurtisActionId.OPEN_DRAFT)
        )
    }

    @Test
    fun rejectsUnsafeLinks() {
        val evil = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_deep_link" to "https://evil.example/history"
        )))!!
        assertTrue(CurtisLinks.resolve(evil, CurtisActionId.OPEN) is CurtisDestination.Unresolved)

        val cleartext = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_deep_link" to "http://github.com/acme/app"
        )))!!
        assertTrue(CurtisLinks.resolve(cleartext, CurtisActionId.OPEN) is CurtisDestination.Unresolved)

        val traversal = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_deep_link" to "/agents/runs/abc/../../settings"
        )))!!
        assertTrue(CurtisLinks.resolve(traversal, CurtisActionId.OPEN) is CurtisDestination.Unresolved)

        val sneakyRun = CurtisPayload.parse(sample(extra = mapOf("run_id" to "../settings")))!!
        assertEquals(CurtisDestination.InApp("home"), CurtisLinks.resolve(sneakyRun, CurtisActionId.OPEN))

        assertNull(CurtisPayload.parse(sample(extra = mapOf("curtis_image_url" to "http://cdn.example/a.png")))!!.imageUrl)
    }

    @Test
    fun draftPrefersLinkExtra() {
        val payload = CurtisPayload.parse(sample(extra = mapOf(
            "curtis_deep_link" to "/history",
            "link" to "mailto:ada@example.com"
        )))!!
        assertEquals(
            CurtisDestination.External("mailto:ada@example.com"),
            CurtisLinks.resolve(payload, CurtisActionId.OPEN_DRAFT)
        )
        assertEquals(CurtisDestination.InApp("history"), CurtisLinks.resolve(payload, CurtisActionId.OPEN))
    }

    @Test
    fun stringMapRoundTrip() {
        val encoded = CurtisJson.encodeStringMap(mapOf("title" to "Say \"hi\"\nthere", "n" to "1"))
        assertEquals(mapOf("title" to "Say \"hi\"\nthere", "n" to "1"), CurtisJson.decodeStringMap(encoded))
    }

    @Test
    fun requestCodesDifferByAction() {
        val id = CurtisPayload.parse(sample(key = "run-7"))!!.notificationId
        assertNotEquals(
            CurtisPayload.requestCode(id, "open"),
            CurtisPayload.requestCode(id, "snooze")
        )
    }

    private fun sample(
        version: String? = "1",
        title: String? = "Ship it",
        body: String? = "The build finished",
        key: String? = null,
        extra: Map<String, String> = emptyMap()
    ): Map<String, String> {
        val data = linkedMapOf<String, String>()
        if (version != null) data["curtis_v"] = version
        if (title != null) data["curtis_title"] = title
        if (body != null) data["curtis_body"] = body
        data["curtis_channel"] = "default"
        data["curtis_channel_id"] = "curtis_default"
        data["curtis_urgency"] = "normal"
        data["curtis_silent"] = "0"
        data["curtis_only_alert_once"] = "1"
        data["curtis_style"] = "default"
        data["curtis_ongoing"] = "0"
        data["curtis_request_promoted"] = "0"
        if (key != null) data["curtis_notification_key"] = key
        data.putAll(extra)
        return data
    }
}
