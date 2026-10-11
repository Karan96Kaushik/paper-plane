package com.barontech.paperplane.notification

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object CurtisInteractions {

    fun snooze(context: Context, payload: CurtisPayload, minutes: Int) {
        val delay = minutes.coerceIn(1, 24 * 60)
        CurtisNotifier(context).hideForSnooze(payload)
        val request = OneTimeWorkRequestBuilder<CurtisSnoozeWorker>()
            .setInitialDelay(delay.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(CurtisSnoozeWorker.KEY_ID to payload.notificationId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            snoozeName(payload.notificationId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun markDone(context: Context, payload: CurtisPayload) {
        CurtisNotifier(context).removeCompletely(payload)
        val related = payload.relatedNotificationId?.toLongOrNull()
        if (related != null && related > 0L) {
            NotificationManagerCompat.from(context)
                .cancel(LocalNotificationManager.notificationIdFor(related))
        }
        payload.extras["notification_id"]?.toLongOrNull()?.takeIf { it > 0L }?.let { id ->
            NotificationManagerCompat.from(context)
                .cancel(LocalNotificationManager.notificationIdFor(id))
        }
    }

    fun cancelSnooze(context: Context, notificationId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(snoozeName(notificationId))
    }

    fun openExternal(context: Context, uri: String): Boolean {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW, parsed)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun snoozeName(notificationId: Int) = "curtis-snooze-$notificationId"
}

class CurtisActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CurtisIntents.ACTION_SNOOZE -> {
                val payload = CurtisIntents.payloadFrom(intent.extras) ?: return
                CurtisInteractions.snooze(context, payload, SNOOZE_MINUTES)
            }
            CurtisIntents.ACTION_MARK_DONE -> {
                val payload = CurtisIntents.payloadFrom(intent.extras) ?: return
                CurtisInteractions.markDone(context, payload)
            }
        }
    }

    companion object {
        const val SNOOZE_MINUTES = 15
    }
}

class CurtisSnoozeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getInt(KEY_ID, -1)
        if (id < 0) return Result.success()
        CurtisNotifier(applicationContext).repostStored(id, forceAlert = true)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "notification_id"
    }
}
