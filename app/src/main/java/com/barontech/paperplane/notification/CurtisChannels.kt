package com.barontech.paperplane.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import com.barontech.paperplane.R

object CurtisChannels {

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(channel(
            context,
            CurtisPayload.CHANNEL_URGENT,
            R.string.curtis_channel_urgent,
            R.string.curtis_channel_urgent_description,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
        })
        manager.createNotificationChannel(channel(
            context,
            CurtisPayload.CHANNEL_DEFAULT,
            R.string.curtis_channel_default,
            R.string.curtis_channel_default_description,
            NotificationManager.IMPORTANCE_DEFAULT
        ))
        manager.createNotificationChannel(channel(
            context,
            CurtisPayload.CHANNEL_DIGEST,
            R.string.curtis_channel_digest,
            R.string.curtis_channel_digest_description,
            NotificationManager.IMPORTANCE_LOW
        ))
        manager.createNotificationChannel(channel(
            context,
            CurtisPayload.CHANNEL_PROGRESS,
            R.string.curtis_channel_progress,
            R.string.curtis_channel_progress_description,
            NotificationManager.IMPORTANCE_DEFAULT
        ))
        manager.createNotificationChannel(channel(
            context,
            CurtisPayload.CHANNEL_QUIET,
            R.string.curtis_channel_quiet,
            R.string.curtis_channel_quiet_description,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null as AudioAttributes?)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        })
    }

    private fun channel(
        context: Context,
        id: String,
        nameRes: Int,
        descriptionRes: Int,
        importance: Int
    ): NotificationChannel {
        return NotificationChannel(id, context.getString(nameRes), importance).apply {
            description = context.getString(descriptionRes)
        }
    }
}
