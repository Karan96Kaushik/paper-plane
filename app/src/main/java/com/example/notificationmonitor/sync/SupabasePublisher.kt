package com.example.notificationmonitor.sync

import com.example.notificationmonitor.database.NotificationEntity
import com.example.notificationmonitor.settings.SupabaseConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SupabasePublisher internal constructor(
    private val currentConfig: suspend () -> SupabaseConfig,
    private val deviceId: String,
    private val transport: SupabaseTransport,
    private val pageSize: Int,
    private val onSessionUpdated: suspend (SupabaseConfig) -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val drainLock = Mutex()
    private val auth = SupabaseAuth(transport)

    constructor(
        currentConfig: suspend () -> SupabaseConfig,
        deviceId: String,
        onSessionUpdated: suspend (SupabaseConfig) -> Unit = {}
    ) : this(
        currentConfig = currentConfig,
        deviceId = deviceId,
        transport = HttpSupabaseTransport(),
        pageSize = DEFAULT_PAGE_SIZE,
        onSessionUpdated = onSessionUpdated
    )

    suspend fun push(notification: NotificationEntity): SupabaseOutcome = drainLock.withLock {
        val config = currentConfig()
        if (!config.enabled) return SupabaseOutcome.Disabled
        post(config, listOf(notification))
    }

    suspend fun signIn(
        config: SupabaseConfig,
        email: String,
        password: String
    ): SupabaseOutcome = drainLock.withLock {
        when (val attempt = auth.signIn(config, email, password, clock())) {
            is AuthAttempt.Success -> {
                onSessionUpdated(config.withSession(attempt.session))
                val label = attempt.session.email.ifBlank { email.trim() }
                SupabaseOutcome.Success(count = 0, detail = "Signed in as $label.")
            }
            is AuthAttempt.Failure -> SupabaseOutcome.Failure(statusCode = null, message = attempt.message)
        }
    }

    suspend fun test(config: SupabaseConfig): SupabaseOutcome {
        val error = config.validationError()
        if (error != null) return SupabaseOutcome.Failure(statusCode = null, message = error)
        val ready = refreshIfNeeded(config)
            ?: return SupabaseOutcome.Failure(
                statusCode = null,
                message = "Sign in again. The saved session could not be refreshed."
            )
        val url = ready.destination()?.let { "$it?limit=0" }
            ?: return SupabaseOutcome.Failure(statusCode = null, message = "Enter the Supabase project URL.")
        val apiKey = ready.normalizedApiKey()
            ?: return SupabaseOutcome.Failure(statusCode = null, message = "Enter the anon key.")
        val bearer = ready.session()?.accessToken
            ?: return SupabaseOutcome.Failure(
                statusCode = null,
                message = "Sign in with the Supabase user that should receive notifications."
            )
        val response = transport.get(url, apiKey, bearer)
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
        markSynced: suspend (ids: List<Long>) -> Unit
    ): SupabaseOutcome = drainLock.withLock {
        val config = currentConfig()
        if (!config.enabled) return SupabaseOutcome.Disabled
        val error = config.validationError()
        if (error != null) return SupabaseOutcome.Failure(statusCode = null, message = error)

        var sent = 0
        while (true) {
            val page = loadPage(pageSize)
            if (page.isEmpty()) {
                return if (sent == 0) {
                    SupabaseOutcome.Success(count = 0, detail = "No stored notifications to send.")
                } else {
                    SupabaseOutcome.Success(count = sent, detail = "Sent $sent notifications.")
                }
            }
            when (val outcome = post(config, page)) {
                is SupabaseOutcome.Success -> {
                    markSynced(page.map { it.id })
                    sent += page.size
                }
                is SupabaseOutcome.Failure -> return outcome.copy(sent = sent)
                SupabaseOutcome.Disabled -> return SupabaseOutcome.Disabled
            }
        }
    }

    private suspend fun post(
        config: SupabaseConfig,
        notifications: List<NotificationEntity>
    ): SupabaseOutcome {
        val error = config.validationError()
        if (error != null) return SupabaseOutcome.Failure(statusCode = null, message = error)
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
        val apiKey = config.normalizedApiKey()
            ?: return SupabaseHttpResponse(0, null)
        val session = config.session()
            ?: return SupabaseHttpResponse(0, null)
        val body = notificationPayloadArray(notifications, deviceId, session.userId)
        return transport.post(url, apiKey, session.accessToken, body)
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
        val detail: String? = null
    ) : SupabaseOutcome()

    data class Failure(
        val statusCode: Int?,
        val message: String,
        val sent: Int = 0
    ) : SupabaseOutcome()

    fun userMessage(): String = when (this) {
        Disabled -> "Supabase push is off. Turn it on and save."
        is Success -> detail ?: if (count == 1) "Sent 1 notification." else "Sent $count notifications."
        is Failure -> if (sent > 0) "Sent $sent, then failed. $message" else message
    }
}
