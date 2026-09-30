package com.example.notificationmonitor.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.notificationmonitor.repository.NotificationRepository
import java.util.concurrent.TimeUnit

class RetentionCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val deleted = NotificationRepository.getInstance(applicationContext)
                .applyRetentionCleanup()
            Log.d(TAG, "Retention cleanup deleted=$deleted rows")
            Result.success()
        } catch (error: Exception) {
            Log.e(TAG, "Retention cleanup failed", error)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "RetentionCleanup"
        private const val UNIQUE_WORK = "notification_retention_cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionCleanupWorker>(
                24, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
