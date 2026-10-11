package com.barontech.paperplane.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.barontech.paperplane.MainActivity
import com.barontech.paperplane.R

object CurtisIntents {
    const val CLICK_ACTION = "CURTIS_OPEN"
    const val EXTRA_ACTION = "com.barontech.paperplane.extra.CURTIS_ACTION"
    const val EXTRA_NOTIFICATION_ID = "com.barontech.paperplane.extra.CURTIS_NOTIFICATION_ID"
    const val ACTION_SNOOZE = "com.barontech.paperplane.action.CURTIS_SNOOZE"
    const val ACTION_MARK_DONE = "com.barontech.paperplane.action.CURTIS_MARK_DONE"
    const val ACTION_DISMISS = "com.barontech.paperplane.action.CURTIS_DISMISS"

    private const val PRIVATE_PREFIX = "com.barontech.paperplane.extra."

    fun payloadFrom(bundle: Bundle?): CurtisPayload? {
        if (bundle == null) return null
        val data = linkedMapOf<String, String>()
        for (key in bundle.keySet()) {
            if (key.startsWith(PRIVATE_PREFIX)) continue
            val value = bundle.get(key) as? String ?: continue
            data[key] = value
        }
        return CurtisPayload.parse(data)
    }

    fun writeData(intent: Intent, data: Map<String, String>) {
        data.forEach { (key, value) -> intent.putExtra(key, value) }
    }
}

class CurtisNotifier(private val context: Context) {

    private val store = CurtisPayloadStore(context)

    fun show(data: Map<String, String>, forceAlert: Boolean = false): Boolean {
        val payload = CurtisPayload.parse(data)?.takeUnless { it.isExpired() } ?: return false
        LocalNotificationManager(context).ensureChannel()
        if (!LocalNotificationManager(context).canPostNotifications()) return false
        val built = build(payload, forceAlert)
        val manager = NotificationManagerCompat.from(context)
        val posted = runCatching {
            manager.notify(payload.notificationId, built)
            true
        }.getOrElse { error ->
            if (!isTooLarge(error)) return false
            runCatching {
                manager.notify(payload.notificationId, build(payload, forceAlert, includeImages = false))
                true
            }.getOrDefault(false)
        }
        if (!posted) return false
        store.saveActive(
            id = payload.notificationId,
            raw = payload.raw,
            group = payload.groupKey,
            title = payload.title,
            channelId = payload.channelId
        )
        payload.groupKey?.let { refreshSummary(it) }
        return true
    }

    fun repostStored(notificationId: Int, forceAlert: Boolean): Boolean {
        val raw = store.loadRaw(notificationId) ?: return false
        return show(raw, forceAlert)
    }

    fun cancel(notificationId: Int) {
        store.expectProgrammaticCancel(notificationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun onUserDismiss(notificationId: Int) {
        if (store.consumeProgrammaticCancel(notificationId)) return
        val group = store.groupOf(notificationId)
        store.remove(notificationId)
        CurtisInteractions.cancelSnooze(context, notificationId)
        group?.let { refreshSummary(it) }
    }

    fun hideForSnooze(payload: CurtisPayload) {
        store.saveActive(
            id = payload.notificationId,
            raw = payload.raw,
            group = payload.groupKey,
            title = payload.title,
            channelId = payload.channelId
        )
        store.hide(payload.notificationId)
        cancel(payload.notificationId)
        payload.groupKey?.let { refreshSummary(it) }
    }

    fun removeCompletely(payload: CurtisPayload) {
        val group = payload.groupKey ?: store.groupOf(payload.notificationId)
        store.remove(payload.notificationId)
        cancel(payload.notificationId)
        CurtisInteractions.cancelSnooze(context, payload.notificationId)
        group?.let { refreshSummary(it) }
    }

    private fun build(
        payload: CurtisPayload,
        forceAlert: Boolean,
        includeImages: Boolean = true
    ): Notification {
        val picture = if (includeImages && payload.style == CurtisStyle.BIG_PICTURE) {
            payload.imageUrl?.let { CurtisImages.load(it, PICTURE_EDGE) }
        } else {
            null
        }
        val largeIcon = if (includeImages) {
            payload.largeIconUrl?.let { CurtisImages.load(it, ICON_EDGE) }
        } else {
            null
        }
        val builder = NotificationCompat.Builder(context, payload.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setSubText(payload.subtitle)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setAutoCancel(!payload.ongoing)
            .setOngoing(payload.ongoing)
            .setOnlyAlertOnce(payload.onlyAlertOnce && !forceAlert)
            .setSilent(payload.silent)
            .setPriority(priority(payload))
            .setContentIntent(contentIntent(payload))
            .setDeleteIntent(deleteIntent(payload))
            .setAllowSystemGeneratedContextualActions(false)
        payload.category?.let { builder.setCategory(it) }
        payload.whenMillis?.let {
            builder.setWhen(it)
            builder.setShowWhen(true)
        }
        payload.groupKey?.let {
            builder.setGroup(it)
            builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        }
        largeIcon?.let { builder.setLargeIcon(it) }
        applyStyle(builder, payload, picture)
        applyTimeout(builder, payload)
        applyPromotion(builder, payload)
        payload.actions.forEach { action ->
            actionIntent(payload, action)?.let { pending ->
                builder.addAction(iconFor(action.id), action.label, pending)
            }
        }
        return builder.build()
    }

    private fun applyStyle(
        builder: NotificationCompat.Builder,
        payload: CurtisPayload,
        picture: android.graphics.Bitmap?
    ) {
        val expanded = payload.expandedBody ?: payload.body
        when (payload.style) {
            CurtisStyle.BIG_PICTURE -> {
                if (picture != null) {
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(picture)
                            .setSummaryText(payload.body)
                            .setBigContentTitle(payload.title)
                    )
                } else {
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
                }
            }
            CurtisStyle.BIG_TEXT -> builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expanded)
                    .setBigContentTitle(payload.title)
            )
            CurtisStyle.DEFAULT -> if (payload.expandedBody != null) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(payload.expandedBody))
            }
            CurtisStyle.PROGRESS -> {
                val max = payload.progressMax ?: 0
                val current = (payload.progressCurrent ?: 0).coerceAtLeast(0)
                builder.setProgress(max, current, payload.progressIndeterminate)
                if (payload.expandedBody != null) {
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText(payload.expandedBody))
                }
            }
        }
    }

    private fun applyTimeout(builder: NotificationCompat.Builder, payload: CurtisPayload) {
        val now = System.currentTimeMillis()
        val expiryDelay = payload.expiresAtMillis?.minus(now)?.takeIf { it > 0 }
        val terminalDelay = if (payload.style == CurtisStyle.PROGRESS && !payload.ongoing) {
            TERMINAL_DISMISS_MS
        } else {
            null
        }
        val delay = listOfNotNull(expiryDelay, terminalDelay).minOrNull() ?: return
        builder.setTimeoutAfter(delay)
    }

    private fun applyPromotion(builder: NotificationCompat.Builder, payload: CurtisPayload) {
        val chip = payload.shortCriticalText
        val request = payload.requestPromoted && payload.ongoing && canPostPromoted()
        if (chip == null && !request) return
        val extras = Bundle()
        if (request) extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
        if (chip != null) extras.putString(EXTRA_SHORT_CRITICAL_TEXT, chip)
        builder.addExtras(extras)
    }

    private fun canPostPromoted(): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            manager.javaClass.getMethod("canPostPromotedNotifications").invoke(manager) as? Boolean
        }.getOrNull() == true
    }

    private fun refreshSummary(group: String) {
        val members = store.shadeMembers(group)
        val summaryId = CurtisPayload.summaryId(group)
        val manager = NotificationManagerCompat.from(context)
        if (members.size <= 1) {
            manager.cancel(summaryId)
            return
        }
        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle(context.getString(R.string.app_name))
            .setSummaryText(context.getString(R.string.curtis_group_summary, members.size))
        members.take(5).forEach { member ->
            if (member.title.isNotBlank()) inbox.addLine(member.title)
        }
        val summary = NotificationCompat.Builder(context, CurtisPayload.CHANNEL_QUIET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.curtis_group_summary, members.size))
            .setGroup(group)
            .setGroupSummary(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setStyle(inbox)
            .setContentIntent(homeIntent(summaryId))
            .build()
        manager.notify(summaryId, summary)
    }

    private fun contentIntent(payload: CurtisPayload): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = CurtisIntents.CLICK_ACTION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            CurtisIntents.writeData(this, payload.raw)
        }
        return PendingIntent.getActivity(
            context,
            CurtisPayload.requestCode(payload.notificationId, "content"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun deleteIntent(payload: CurtisPayload): PendingIntent {
        val intent = Intent(context, CurtisActionReceiver::class.java).apply {
            action = CurtisIntents.ACTION_DISMISS
            putExtra(CurtisIntents.EXTRA_NOTIFICATION_ID, payload.notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            CurtisPayload.requestCode(payload.notificationId, "dismiss"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun homeIntent(requestId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(payload: CurtisPayload, action: CurtisAction): PendingIntent? {
        val request = CurtisPayload.requestCode(payload.notificationId, action.id.wire)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return when (action.id) {
            CurtisActionId.OPEN, CurtisActionId.OPEN_DRAFT, CurtisActionId.VIEW_ISSUE -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    this.action = CurtisIntents.CLICK_ACTION
                    this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    CurtisIntents.writeData(this, payload.raw)
                    putExtra(CurtisIntents.EXTRA_ACTION, action.id.wire)
                }
                PendingIntent.getActivity(context, request, intent, flags)
            }
            CurtisActionId.SNOOZE -> PendingIntent.getBroadcast(
                context,
                request,
                broadcast(payload, CurtisIntents.ACTION_SNOOZE),
                flags
            )
            CurtisActionId.MARK_DONE -> PendingIntent.getBroadcast(
                context,
                request,
                broadcast(payload, CurtisIntents.ACTION_MARK_DONE),
                flags
            )
        }
    }

    private fun broadcast(payload: CurtisPayload, action: String): Intent {
        return Intent(context, CurtisActionReceiver::class.java).apply {
            this.action = action
            CurtisIntents.writeData(this, payload.raw)
            putExtra(CurtisIntents.EXTRA_NOTIFICATION_ID, payload.notificationId)
        }
    }

    private fun priority(payload: CurtisPayload): Int {
        if (payload.silent) return NotificationCompat.PRIORITY_LOW
        return when (payload.urgency) {
            "high" -> NotificationCompat.PRIORITY_HIGH
            "low" -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    private fun iconFor(id: CurtisActionId): Int = when (id) {
        CurtisActionId.OPEN -> R.drawable.ic_curtis_open
        CurtisActionId.SNOOZE -> R.drawable.ic_curtis_snooze
        CurtisActionId.MARK_DONE -> R.drawable.ic_curtis_done
        CurtisActionId.OPEN_DRAFT -> R.drawable.ic_curtis_draft
        CurtisActionId.VIEW_ISSUE -> R.drawable.ic_curtis_issue
    }

    private fun isTooLarge(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current.javaClass.name == "android.os.TransactionTooLargeException") return true
            current = current.cause
        }
        return false
    }

    companion object {
        private const val PICTURE_EDGE = 480
        private const val ICON_EDGE = 128
        private const val TERMINAL_DISMISS_MS = 10_000L
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    }
}
