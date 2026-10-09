package com.example.notificationmonitor.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.notificationmonitor.database.AppDatabase
import com.example.notificationmonitor.database.MonitoredAppEntity
import com.example.notificationmonitor.database.NotificationEntity
import com.example.notificationmonitor.database.RepublishRuleEntity
import com.example.notificationmonitor.database.WorkflowEntity
import com.example.notificationmonitor.notification.NotificationType
import com.example.notificationmonitor.settings.RetentionPeriod
import com.example.notificationmonitor.settings.SupabaseConfig
import com.example.notificationmonitor.settings.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

class NotificationRepository(
    private val database: AppDatabase,
    private val preferences: UserPreferences,
    private val appContext: Context
) {

    private val notificationDao = database.notificationDao()
    private val monitoredAppDao = database.monitoredAppDao()
    private val republishRuleDao = database.republishRuleDao()
    private val workflowDao = database.workflowDao()

    fun observeNotifications(limit: Int = DEFAULT_LIMIT): Flow<List<NotificationEntity>> =
        notificationDao.observeNotifications(limit)

    fun observeNotificationsByPackage(
        packageName: String,
        limit: Int = DEFAULT_LIMIT
    ): Flow<List<NotificationEntity>> =
        notificationDao.observeNotificationsByPackage(packageName, limit)

    fun observeHistory(
        packageName: String?,
        type: NotificationType?,
        limit: Int = DEFAULT_LIMIT
    ): Flow<List<NotificationEntity>> {
        val base = if (packageName == null) {
            notificationDao.observeNotifications(limit)
        } else {
            notificationDao.observeNotificationsByPackage(packageName, limit)
        }
        if (type == null) return base
        return base.map { items ->
            items.filter { NotificationType.fromCategory(it.category) == type }
        }
    }

    fun observeNotification(id: Long): Flow<NotificationEntity?> =
        notificationDao.observeById(id)

    fun observeCount(): Flow<Int> = notificationDao.observeCount()

    fun observeCountToday(): Flow<Int> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return notificationDao.observeCountSince(startOfDay)
    }

    fun observeLatestNotification(): Flow<NotificationEntity?> =
        notificationDao.observeNotifications(1).map { it.firstOrNull() }

    fun observeDistinctPackageNames(): Flow<List<String>> =
        notificationDao.observeDistinctPackageNames()

    fun observeMonitoredApps(): Flow<List<MonitoredAppEntity>> =
        monitoredAppDao.observeAll()

    fun observeEnabledAppCount(): Flow<Int> =
        monitoredAppDao.observeEnabledCount()

    fun observeRetentionPeriod(): Flow<RetentionPeriod> =
        preferences.retentionPeriod

    suspend fun insertIfAllowed(notification: NotificationEntity): Long? {
        return try {
            if (!isPackageEnabled(notification.packageName)) {
                return null
            }
            ensureAppTracked(notification.packageName, notification.appName)
            notificationDao.insert(notification)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to persist notification for package=${notification.packageName}", error)
            null
        }
    }

    suspend fun delete(notification: NotificationEntity) {
        notificationDao.delete(notification)
    }

    suspend fun clearHistory() {
        notificationDao.deleteAll()
    }

    suspend fun deleteOlderThan(timestamp: Long): Int =
        notificationDao.deleteOlderThan(timestamp)

    suspend fun applyRetentionCleanup(): Int {
        val retention = preferences.retentionPeriod.first()
        val cutoff = retention.cutoffMillis(System.currentTimeMillis()) ?: return 0
        return deleteOlderThan(cutoff)
    }

    suspend fun setAppEnabled(packageName: String, enabled: Boolean) {
        monitoredAppDao.setEnabled(packageName, enabled)
    }

    suspend fun setRetentionPeriod(period: RetentionPeriod) {
        preferences.setRetentionPeriod(period)
    }

    fun observeAutoRepublishEnabled(): Flow<Boolean> = preferences.autoRepublishEnabled

    suspend fun setAutoRepublishEnabled(enabled: Boolean) {
        preferences.setAutoRepublishEnabled(enabled)
    }

    suspend fun isAutoRepublishEnabled(): Boolean = preferences.isAutoRepublishEnabled()

    fun observeSupabaseConfig(): Flow<SupabaseConfig> = preferences.supabaseConfig

    suspend fun currentSupabaseConfig(): SupabaseConfig = preferences.currentSupabaseConfig()

    suspend fun saveSupabaseConfig(config: SupabaseConfig) {
        val previous = preferences.currentSupabaseConfig()
        val projectChanged = previous.normalizedUrl() != config.normalizedUrl() ||
            previous.normalizedApiKey() != config.normalizedApiKey()
        val saved = if (projectChanged) {
            config.clearedSession()
        } else {
            config.copy(
                accountEmail = previous.accountEmail,
                userId = previous.userId,
                accessToken = previous.accessToken,
                refreshToken = previous.refreshToken,
                accessTokenExpiresAt = previous.accessTokenExpiresAt
            )
        }
        replaceSupabaseConfig(saved)
    }

    suspend fun replaceSupabaseConfig(updated: SupabaseConfig) {
        val previous = preferences.currentSupabaseConfig()
        preferences.setSupabaseConfig(updated)
        if (deliveryTargetChanged(previous, updated)) {
            notificationDao.clearSupabaseSync()
        }
    }

    suspend fun clearSupabaseSession() {
        val current = preferences.currentSupabaseConfig()
        replaceSupabaseConfig(current.clearedSession())
    }

    private fun deliveryTargetChanged(previous: SupabaseConfig, updated: SupabaseConfig): Boolean {
        if (previous.normalizedUrl() == null) return false
        if (previous.normalizedUrl() != updated.normalizedUrl()) return true
        if (previous.normalizedTable() != updated.normalizedTable()) return true
        val previousUser = previous.normalizedUserId()
        val nextUser = updated.normalizedUserId()
        return previousUser != null && nextUser != null && previousUser != nextUser
    }

    suspend fun unsyncedNotifications(limit: Int): List<NotificationEntity> =
        notificationDao.getUnsynced(limit)

    suspend fun markSupabaseSynced(ids: List<Long>, syncedAt: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        notificationDao.markSupabaseSynced(ids, syncedAt)
    }

    fun observeRepublishRules(): Flow<List<RepublishRuleEntity>> = republishRuleDao.observeAll()

    fun observeRepublishRule(packageName: String): Flow<RepublishRuleEntity?> =
        republishRuleDao.observeByPackage(packageName)

    fun observeRepublishEnabledAppCount(): Flow<Int> = republishRuleDao.observeEnabledCount()

    suspend fun getRepublishRule(packageName: String): RepublishRuleEntity? =
        republishRuleDao.getByPackage(packageName)

    suspend fun getEnabledRepublishRules(): List<RepublishRuleEntity> =
        republishRuleDao.getEnabled()

    suspend fun saveRepublishRule(rule: RepublishRuleEntity) {
        republishRuleDao.upsert(rule)
    }

    suspend fun setRepublishEnabled(packageName: String, enabled: Boolean) {
        val existing = republishRuleDao.getByPackage(packageName)
            ?: RepublishRuleEntity(packageName = packageName)
        val noTypes = !existing.allTypes && existing.categoriesCsv.isBlank()
        republishRuleDao.upsert(
            existing.copy(
                enabled = enabled,
                allTypes = if (enabled && noTypes) true else existing.allTypes
            )
        )
    }

    suspend fun setRepublishEnabledForPackages(packageNames: List<String>, enabled: Boolean) {
        packageNames.forEach { packageName ->
            setRepublishEnabled(packageName, enabled)
        }
    }

    suspend fun recentByPackage(packageName: String, limit: Int): List<NotificationEntity> =
        notificationDao.getRecentByPackage(packageName, limit)

    suspend fun recentNotifications(limit: Int): List<NotificationEntity> =
        notificationDao.getRecent(limit)

    fun observeWorkflows(): Flow<List<WorkflowEntity>> = workflowDao.observeAll()

    fun observeWorkflow(id: Long): Flow<WorkflowEntity?> = workflowDao.observeById(id)

    suspend fun getWorkflow(id: Long): WorkflowEntity? = workflowDao.getById(id)

    suspend fun getEnabledWorkflows(): List<WorkflowEntity> = workflowDao.getEnabled()

    suspend fun saveWorkflow(workflow: WorkflowEntity): Long = workflowDao.upsert(workflow)

    suspend fun deleteWorkflow(id: Long) {
        workflowDao.delete(id)
    }

    suspend fun setWorkflowEnabled(id: Long, enabled: Boolean) {
        val existing = workflowDao.getById(id) ?: return
        workflowDao.upsert(existing.copy(enabled = enabled))
    }

    suspend fun getByIds(ids: List<Long>): List<NotificationEntity> {
        if (ids.isEmpty()) return emptyList()
        return notificationDao.getByIds(ids)
    }

    suspend fun markRepublished(id: Long, republishedAt: Long) {
        notificationDao.markRepublished(id, republishedAt)
    }

    suspend fun countRepublishedKeySince(notificationKey: String, since: Long): Int =
        notificationDao.countRepublishedKeySince(notificationKey, since)

    suspend fun isPackageEnabled(packageName: String): Boolean {
        val stored = monitoredAppDao.isEnabled(packageName)
        return stored ?: true
    }

    /**
     * Discover installed launcher apps and ensure each has a monitoring preference row.
     * Default: enabled for all newly discovered apps.
     */
    suspend fun syncInstalledApps() {
        val pm = appContext.packageManager
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        resolveInfos
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .forEach { info ->
                val label = try {
                    pm.getApplicationLabel(info)?.toString()
                } catch (_: Exception) {
                    null
                }
                monitoredAppDao.insertIfAbsent(
                    MonitoredAppEntity(
                        packageName = info.packageName,
                        appName = label,
                        enabled = true
                    )
                )
            }
    }

    suspend fun ensureAppTracked(packageName: String, appName: String?) {
        monitoredAppDao.insertIfAbsent(
            MonitoredAppEntity(
                packageName = packageName,
                appName = appName,
                enabled = true
            )
        )
    }

    fun observeHomeStats(): Flow<HomeStats> {
        return combine(
            observeCountToday(),
            observeEnabledAppCount(),
            observeLatestNotification(),
            observeRepublishEnabledAppCount()
        ) { today, enabledApps, latest, republishApps ->
            HomeStats(
                notificationsToday = today,
                monitoredApps = enabledApps,
                latest = latest,
                republishApps = republishApps
            )
        }
    }

    data class HomeStats(
        val notificationsToday: Int,
        val monitoredApps: Int,
        val latest: NotificationEntity?,
        val republishApps: Int = 0
    )

    companion object {
        private const val TAG = "NotificationRepository"
        const val DEFAULT_LIMIT = 200

        @Volatile
        private var instance: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: NotificationRepository(
                    database = AppDatabase.getInstance(context),
                    preferences = UserPreferences(context.applicationContext),
                    appContext = context.applicationContext
                ).also { instance = it }
            }
        }
    }
}
