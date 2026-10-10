package com.barontech.paperplane.sync

import com.barontech.paperplane.database.NotificationEntity
import com.barontech.paperplane.settings.SupabaseConfig
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SupabasePublisher internal constructor(
    private val currentConfig: suspend () -> SupabaseConfig,
    private val deviceId: String,
    private val transport: SupabaseTransport,
    private val pageSize: Int,
    private val onSessionUpdated: suspend (SupabaseConfig) -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val exclusionRules: suspend () -> List<SupabaseExclusionRule> = { emptyList() },
    private val registrationToken: suspend () -> String? = { null }
) {
    private val drainLock = Mutex()
    private val auth = SupabaseAuth(transport)

    constructor(
        currentConfig: suspend () -> SupabaseConfig,
        deviceId: String,
        onSessionUpdated: suspend (SupabaseConfig) -> Unit = {},
        exclusionRules: suspend () -> List<SupabaseExclusionRule> = { emptyList() },
        registrationToken: suspend () -> String? = { null }
    ) : this(
        currentConfig = currentConfig,
        deviceId = deviceId,
        transport = HttpSupabaseTransport(),
        pageSize = DEFAULT_PAGE_SIZE,
        onSessionUpdated = onSessionUpdated,
        exclusionRules = exclusionRules,
        registrationToken = registrationToken
    )

    suspend fun push(notification: NotificationEntity): SupabaseOutcome = drainLock.withLock {
        val config = currentConfig()
        if (!config.enabled) return SupabaseOutcome.Disabled
        if (exclusionRules().excludes(notification)) {
            return SupabaseOutcome.Success(count = 0, excluded = 1)
        }
        post(config, listOf(notification))
    }

    suspend fun signIn(
        config: SupabaseConfig,
        email: String,
        password: String
    ): SupabaseOutcome {
        val signedIn = drainLock.withLock {
            when (val attempt = auth.signIn(config, email, password, clock())) {
                is AuthAttempt.Success -> {
                    val updated = config.withSession(attempt.session)
                    onSessionUpdated(updated)
                    updated to attempt.session.email.ifBlank { email.trim() }
                }
                is AuthAttempt.Failure -> {
                    SupabaseLog.signInBlocked(attempt.message)
                    null to attempt.message
                }
            }
        }
        val sessionConfig = signedIn.first
            ?: return SupabaseOutcome.Failure(statusCode = null, message = signedIn.second)
        val label = signedIn.second
        val tokenNote = registerCurrentToken(sessionConfig)
        val detail = if (tokenNote == null) "Signed in as $label." else "Signed in as $label. $tokenNote"
        return SupabaseOutcome.Success(count = 0, detail = detail)
    }

    /**
     * Upserts this device's FCM token for the signed-in user.
     * Skips quietly when there is no session yet.
     */
    suspend fun upsertDeviceToken(token: String): SupabaseOutcome = drainLock.withLock {
        val config = currentConfig()
        if (config.session() == null) {
            SupabaseLog.deviceTokenSkipped("not signed in")
            return@withLock SupabaseOutcome.Success(count = 0, detail = "Not signed in.")
        }
        writeDeviceToken(config, token)
    }

    suspend fun test(config: SupabaseConfig): SupabaseOutcome {
        val error = config.validationError()
        if (error != null) {
            SupabaseLog.validationBlocked(error)
            return SupabaseOutcome.Failure(statusCode = null, message = error)
        }
        val ready = refreshIfNeeded(config)
            ?: return SupabaseOutcome.Failure(
                statusCode = null,
                message = "Sign in again. The saved session could not be refreshed."
            )
        val url = ready.destination()?.let { "$it?limit=0" }
            ?: return SupabaseOutcome.Failure(statusCode = null, message = "Enter the Supabase project URL.")
        val publishableKey = ready.normalizedPublishableKey()
            ?: return SupabaseOutcome.Failure(statusCode = null, message = "Enter the publishable key.")
        val bearer = ready.session()?.accessToken
            ?: return SupabaseOutcome.Failure(
                statusCode = null,
                message = ready.validationError() ?: "Sign in again."
            )
        val response = transport.get(url, publishableKey, bearer)
        return if (response.isSuccessful) {
            SupabaseOutcome.Success(count = 0, detail = "Connected.")
        } else {
            failure(response, sent = 0)
        }
    }

    /**
     * Inserts stored notifications that have not been marked as delivered.
     * [loadPage] must return only rows that are still pending.
     */
    suspend fun pushPending(
        loadPage: suspend (limit: Int) -> List<NotificationEntity>,
        markSynced: suspend (ids: List<Long>) -> Unit,
        markExcluded: suspend (ids: List<Long>) -> Unit = {}
    ): SupabaseOutcome = drainLock.withLock {
        val config = currentConfig()
        if (!config.enabled) {
            SupabaseOutcome.Disabled
        } else {
            val error = config.validationError()
            if (error != null) {
                SupabaseLog.validationBlocked(error)
                SupabaseOutcome.Failure(statusCode = null, message = error)
            } else {
                drainPending(config, loadPage, markSynced, markExcluded)
            }
        }
    }

    private suspend fun drainPending(
        config: SupabaseConfig,
        loadPage: suspend (limit: Int) -> List<NotificationEntity>,
        markSynced: suspend (ids: List<Long>) -> Unit,
        markExcluded: suspend (ids: List<Long>) -> Unit
    ): SupabaseOutcome {
        var sent = 0
        var excludedCount = 0
        val seenExcluded = mutableSetOf<Long>()
        while (true) {
            val page = loadPage(pageSize)
            if (page.isEmpty()) {
                return pendingResult(sent, excludedCount)
            }
            val rules = exclusionRules()
            val (excluded, included) = page.partition { rules.excludes(it) }
            val freshExcluded = excluded.filter { it.id !in seenExcluded }
            if (freshExcluded.isNotEmpty()) {
                markExcluded(freshExcluded.map { it.id })
                seenExcluded += freshExcluded.map { it.id }
                excludedCount += freshExcluded.size
            }
            if (included.isEmpty()) {
                if (freshExcluded.isEmpty()) {
                    return pendingResult(sent, excludedCount)
                }
                continue
            }
            when (val outcome = post(config, included)) {
                is SupabaseOutcome.Success -> {
                    markSynced(included.map { it.id })
                    sent += included.size
                }
                is SupabaseOutcome.Failure -> return outcome.copy(sent = sent)
                SupabaseOutcome.Disabled -> return SupabaseOutcome.Disabled
            }
        }
    }

    private fun pendingResult(sent: Int, excluded: Int): SupabaseOutcome.Success {
        val detail = when {
            sent == 0 && excluded == 0 -> "No stored notifications to send."
            sent == 0 -> excludedMessage(excluded)
            excluded == 0 -> sentMessage(sent)
            else -> "${sentMessage(sent)} ${excludedMessage(excluded)}"
        }
        return SupabaseOutcome.Success(count = sent, excluded = excluded, detail = detail)
    }

    private suspend fun post(
        config: SupabaseConfig,
        notifications: List<NotificationEntity>
    ): SupabaseOutcome {
        val error = config.validationError()
        if (error != null) {
            SupabaseLog.validationBlocked(error)
            return SupabaseOutcome.Failure(statusCode = null, message = error)
        }
        val ready = refreshIfNeeded(config)
            ?: return SupabaseOutcome.Failure(
                statusCode = null,
                message = "Sign in again. The saved session could not be refreshed."
            )
        var response = send(ready, notifications)
        if (response.statusCode == 401) {
            val refreshed = refresh(ready)
            if (refreshed != null) {
                response = send(refreshed, notifications)
            }
        }
        return if (response.isSuccessful) {
            SupabaseOutcome.Success(count = notifications.size)
        } else {
            failure(response, sent = 0)
        }
    }

    private fun send(
        config: SupabaseConfig,
        notifications: List<NotificationEntity>
    ): SupabaseHttpResponse {
        val url = config.destination()
            ?: return SupabaseHttpResponse(0, null)
        val publishableKey = config.normalizedPublishableKey()
            ?: return SupabaseHttpResponse(0, null)
        val session = config.session()
            ?: return SupabaseHttpResponse(0, null)
        val body = notificationPayloadArray(notifications, deviceId, session.userId)
        return transport.post(
            url = url,
            publishableKey = publishableKey,
            body = body,
            authorizationBearer = session.accessToken
        )
    }

    private suspend fun registerCurrentToken(config: SupabaseConfig): String? {
        val raw = try {
            registrationToken()?.trim().orEmpty()
        } catch (error: Exception) {
            SupabaseLog.deviceTokenSkipped(error.message ?: "token lookup failed")
            return "Push token is not available yet."
        }
        if (raw.isEmpty()) {
            SupabaseLog.deviceTokenSkipped("token not ready")
            return null
        }
        return when (val outcome = drainLock.withLock { writeDeviceToken(config, raw) }) {
            is SupabaseOutcome.Failure -> outcome.message
            else -> null
        }
    }

    private suspend fun writeDeviceToken(config: SupabaseConfig, token: String): SupabaseOutcome {
        val normalized = normalizedFcmToken(token)
        if (normalized == null) {
            SupabaseLog.deviceTokenFailed("token length ${token.trim().length}")
            return SupabaseOutcome.Failure(statusCode = null, message = "The push token is not valid.")
        }
        val ready = refreshIfNeeded(config)
            ?: return SupabaseOutcome.Failure(
                statusCode = null,
                message = "Sign in again. The saved session could not be refreshed."
            )
        var response = sendDeviceToken(ready, normalized)
        if (response.statusCode == 401) {
            val refreshed = refresh(ready)
            if (refreshed != null) {
                response = sendDeviceToken(refreshed, normalized)
            }
        }
        return if (response.isSuccessful) {
            SupabaseOutcome.Success(count = 1, detail = "Device token saved.")
        } else {
            val failed = failure(response, sent = 0)
            SupabaseLog.deviceTokenFailed(failed.message)
            failed
        }
    }

    private fun sendDeviceToken(config: SupabaseConfig, token: String): SupabaseHttpResponse {
        val projectUrl = config.normalizedUrl()
            ?: return SupabaseHttpResponse(0, null)
        val publishableKey = config.normalizedPublishableKey()
            ?: return SupabaseHttpResponse(0, null)
        val session = config.session()
            ?: return SupabaseHttpResponse(0, null)
        val body = deviceTokenPayload(
            userId = session.userId,
            token = token,
            deviceId = normalizedDeviceId(deviceId),
            updatedAt = Instant.ofEpochMilli(clock()).toString()
        )
        return transport.post(
            url = "$projectUrl/rest/v1/$DEVICE_TOKENS_TABLE?on_conflict=user_id,token",
            publishableKey = publishableKey,
            body = body,
            authorizationBearer = session.accessToken,
            prefer = DEVICE_TOKEN_UPSERT_PREFER
        )
    }

    private suspend fun refreshIfNeeded(config: SupabaseConfig): SupabaseConfig? {
        val session = config.session() ?: return null
        if (!session.refreshDue(clock())) return config
        return refresh(config)
    }

    private suspend fun refresh(config: SupabaseConfig): SupabaseConfig? {
        val updated = when (val attempt = auth.refresh(config, clock())) {
            is AuthAttempt.Success -> config.withSession(attempt.session)
            is AuthAttempt.Failure -> return null
        }
        onSessionUpdated(updated)
        return updated
    }

    private fun failure(response: SupabaseHttpResponse, sent: Int): SupabaseOutcome.Failure {
        return SupabaseOutcome.Failure(
            statusCode = response.statusCode.takeIf { it > 0 },
            message = failureMessage(response.statusCode, response.body),
            sent = sent
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 25

        internal fun sentMessage(count: Int): String =
            if (count == 1) "Sent 1 notification." else "Sent $count notifications."

        internal fun excludedMessage(count: Int): String =
            if (count == 1) {
                "1 matched an exclusion rule and was not uploaded."
            } else {
                "$count matched an exclusion rule and were not uploaded."
            }

        internal fun failureMessage(status: Int, body: String?): String {
            val summary = when (status) {
                0 -> "Could not reach Supabase."
                401, 403 -> "The signed-in user was rejected. Sign in again."
                404 -> "The table was not found at that project URL."
                409 -> "Supabase rejected the insert."
                in 400..499 -> "Supabase rejected the request."
                in 500..599 -> "Supabase returned a server error."
                else -> "Supabase returned status $status."
            }
            if (status == 0) return summary
            val detail = body
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(160)
                ?.takeIf { it.isNotEmpty() }
            return if (detail == null) summary else "$summary $detail"
        }
    }
}

sealed class SupabaseOutcome {
    data object Disabled : SupabaseOutcome()

    data class Success(
        val count: Int,
        val excluded: Int = 0,
        val detail: String? = null
    ) : SupabaseOutcome()

    data class Failure(
        val statusCode: Int?,
        val message: String,
        val sent: Int = 0
    ) : SupabaseOutcome()

    fun userMessage(): String = when (this) {
        Disabled -> "Supabase push is off. Turn it on and save."
        is Success -> detail ?: if (excluded == 0) {
            SupabasePublisher.sentMessage(count)
        } else if (count == 0) {
            SupabasePublisher.excludedMessage(excluded)
        } else {
            "${SupabasePublisher.sentMessage(count)} ${SupabasePublisher.excludedMessage(excluded)}"
        }
        is Failure -> if (sent > 0) "Sent $sent, then failed. $message" else message
    }
}
