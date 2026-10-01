package com.example.notificationmonitor.notification

import com.example.notificationmonitor.database.NotificationEntity
import com.example.notificationmonitor.database.WorkflowEntity
import com.example.notificationmonitor.repository.NotificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepublisher(
    private val repository: NotificationRepository,
    private val poster: NotificationPoster,
    private val dedupeWindowMillis: Long = DEFAULT_DEDUPE_WINDOW_MS,
    private val postLimit: Int = POST_LIMIT,
    private val scanLimit: Int = SCAN_LIMIT,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun onNewNotification(entity: NotificationEntity) {
        if (!repository.isAutoRepublishEnabled()) return
        val rule = repository.getRepublishRule(entity.packageName)
        val plan = WorkflowEngine.decide(
            notification = entity,
            appRule = rule,
            workflows = repository.getEnabledWorkflows()
        )
        if (!plan.republish) return
        val key = entity.notificationKey
        if (!key.isNullOrBlank()) {
            val since = clock() - dedupeWindowMillis
            if (repository.countRepublishedKeySince(key, since) > 0) return
        }
        postAll(
            items = listOf(entity),
            skipAlreadyRepublished = false,
            applyLimit = false,
            titlePrefix = plan.titlePrefix
        )
    }

    suspend fun republishPast(
        packageName: String,
        includeAlreadyRepublished: Boolean
    ): RepublishBatchResult = withContext(dispatcher) {
        val rule = repository.getRepublishRule(packageName) ?: return@withContext RepublishBatchResult()
        val matched = repository.recentByPackage(packageName, scanLimit)
            .filter { RepublishMatcher.matches(it, rule, requireEnabled = false) }
        postAll(
            items = matched,
            skipAlreadyRepublished = !includeAlreadyRepublished,
            applyLimit = true
        )
    }

    suspend fun republishPastForEnabledApps(
        includeAlreadyRepublished: Boolean
    ): RepublishBatchResult = withContext(dispatcher) {
        val workflows = repository.getEnabledWorkflows()
        val rules = repository.getEnabledRepublishRules().associateBy { it.packageName }
        val matched = repository.recentNotifications(scanLimit)
            .mapNotNull { notification ->
                val plan = WorkflowEngine.decide(
                    notification = notification,
                    appRule = rules[notification.packageName],
                    workflows = workflows
                )
                if (!plan.republish) null else notification to plan.titlePrefix
            }
            .sortedByDescending { it.first.postedAt }
        val prefixById = matched.associate { it.first.id to it.second }
        postAll(
            items = matched.map { it.first },
            skipAlreadyRepublished = !includeAlreadyRepublished,
            applyLimit = true,
            titlePrefixFor = { prefixById[it.id].orEmpty() }
        )
    }

    suspend fun runWorkflow(
        workflowId: Long,
        includeAlreadyRepublished: Boolean
    ): RepublishBatchResult = withContext(dispatcher) {
        val workflow = repository.getWorkflow(workflowId) ?: return@withContext RepublishBatchResult()
        runWorkflow(workflow, includeAlreadyRepublished)
    }

    suspend fun runWorkflow(
        workflow: WorkflowEntity,
        includeAlreadyRepublished: Boolean
    ): RepublishBatchResult = withContext(dispatcher) {
        if (workflow.action() == WorkflowAction.SKIP) {
            return@withContext RepublishBatchResult(blockedByWorkflow = true)
        }
        val source = if (workflow.packageName.isBlank()) {
            repository.recentNotifications(scanLimit)
        } else {
            repository.recentByPackage(workflow.packageName, scanLimit)
        }
        val matched = source.filter { WorkflowEngine.matches(it, workflow) }
        postAll(
            items = matched,
            skipAlreadyRepublished = !includeAlreadyRepublished,
            applyLimit = true,
            titlePrefix = workflow.titlePrefix
        )
    }

    suspend fun republishIds(ids: List<Long>): RepublishBatchResult {
        if (ids.isEmpty()) return RepublishBatchResult()
        return withContext(dispatcher) {
            val items = repository.getByIds(ids).sortedByDescending { it.postedAt }
            postAll(
                items = items,
                skipAlreadyRepublished = false,
                applyLimit = false
            )
        }
    }

    private suspend fun postAll(
        items: List<NotificationEntity>,
        skipAlreadyRepublished: Boolean,
        applyLimit: Boolean,
        titlePrefix: String = "",
        titlePrefixFor: (NotificationEntity) -> String = { titlePrefix }
    ): RepublishBatchResult {
        var skippedAlready = 0
        var skippedEmpty = 0
        val ready = ArrayList<Pair<NotificationEntity, RepublishContent>>(items.size)
        for (item in items) {
            if (skipAlreadyRepublished && item.republishedAt != null) {
                skippedAlready++
                continue
            }
            val content = RepublishContent.from(item)?.withTitlePrefix(titlePrefixFor(item))
            if (content == null) {
                skippedEmpty++
                continue
            }
            ready += item to content
        }

        val posting = if (applyLimit) ready.take(postLimit) else ready
        val notPostedDueToLimit = ready.size - posting.size
        if (posting.isEmpty()) {
            return RepublishBatchResult(
                skippedAlready = skippedAlready,
                skippedEmpty = skippedEmpty,
                notPostedDueToLimit = notPostedDueToLimit
            )
        }
        if (!poster.canPostNotifications()) {
            return RepublishBatchResult(
                skippedAlready = skippedAlready,
                skippedEmpty = skippedEmpty,
                notPostedDueToLimit = notPostedDueToLimit + posting.size,
                permissionDenied = true
            )
        }

        var posted = 0
        for ((item, content) in posting) {
            val ok = poster.showRepublished(
                entityId = item.id,
                title = content.title,
                message = content.message,
                bigText = content.bigText,
                subText = content.subText,
                category = item.category
            )
            if (!ok) {
                return RepublishBatchResult(
                    posted = posted,
                    skippedAlready = skippedAlready,
                    skippedEmpty = skippedEmpty,
                    notPostedDueToLimit = notPostedDueToLimit + (posting.size - posted),
                    permissionDenied = true
                )
            }
            if (item.id > 0L) {
                repository.markRepublished(item.id, clock())
            }
            posted++
        }
        return RepublishBatchResult(
            posted = posted,
            skippedAlready = skippedAlready,
            skippedEmpty = skippedEmpty,
            notPostedDueToLimit = notPostedDueToLimit
        )
    }

    companion object {
        const val DEFAULT_DEDUPE_WINDOW_MS = 2 * 60 * 1000L
        const val POST_LIMIT = 40
        const val SCAN_LIMIT = 500
    }
}

data class RepublishBatchResult(
    val posted: Int = 0,
    val skippedAlready: Int = 0,
    val skippedEmpty: Int = 0,
    val notPostedDueToLimit: Int = 0,
    val permissionDenied: Boolean = false,
    val blockedByWorkflow: Boolean = false
) {
    fun userMessage(): String {
        if (blockedByWorkflow) {
            return "This workflow keeps matching notifications from being republished."
        }
        if (permissionDenied && posted == 0) {
            return "Cannot post notifications. Grant notification permission in system settings."
        }
        if (posted == 0 && skippedAlready == 0 && skippedEmpty == 0 && notPostedDueToLimit == 0) {
            return "No matching notifications to republish."
        }
        val parts = mutableListOf("Republished $posted")
        if (skippedAlready > 0) parts += "$skippedAlready already republished"
        if (skippedEmpty > 0) parts += "$skippedEmpty empty"
        if (notPostedDueToLimit > 0) parts += "$notPostedDueToLimit not posted because the batch limit was reached"
        if (permissionDenied) parts += "stopped because notification permission is missing"
        return parts.joinToString(", ")
    }
}
