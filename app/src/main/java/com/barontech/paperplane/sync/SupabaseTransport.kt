package com.barontech.paperplane.sync

import java.net.HttpURLConnection
import java.net.URL

internal interface SupabaseTransport {
    fun post(
        url: String,
        publishableKey: String,
        body: String,
        authorizationBearer: String? = null,
        preferMinimal: Boolean = true,
        prefer: String? = null
    ): SupabaseHttpResponse

    fun get(
        url: String,
        publishableKey: String,
        authorizationBearer: String?
    ): SupabaseHttpResponse
}

internal data class SupabaseHttpResponse(
    val statusCode: Int,
    val body: String?
) {
    val isSuccessful: Boolean get() = statusCode in 200..299
}

internal class HttpSupabaseTransport : SupabaseTransport {
    override fun post(
        url: String,
        publishableKey: String,
        body: String,
        authorizationBearer: String?,
        preferMinimal: Boolean,
        prefer: String?
    ): SupabaseHttpResponse = exchange(
        method = "POST",
        url = url,
        publishableKey = publishableKey,
        authorizationBearer = authorizationBearer,
        body = body,
        preferHeader = prefer ?: if (preferMinimal) "return=minimal" else null
    )

    override fun get(
        url: String,
        publishableKey: String,
        authorizationBearer: String?
    ): SupabaseHttpResponse = exchange(
        method = "GET",
        url = url,
        publishableKey = publishableKey,
        authorizationBearer = authorizationBearer,
        body = null,
        preferHeader = null
    )

    private fun exchange(
        method: String,
        url: String,
        publishableKey: String,
        authorizationBearer: String?,
        body: String?,
        preferHeader: String?
    ): SupabaseHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("apikey", publishableKey)
            if (!authorizationBearer.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $authorizationBearer")
            }
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (!preferHeader.isNullOrBlank()) {
                    connection.setRequestProperty("Prefer", preferHeader)
                }
                connection.outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.readText().take(MAX_BODY_CHARS).ifEmpty { null }
            }
            SupabaseHttpResponse(status, responseBody)
        } catch (error: Exception) {
            SupabaseHttpResponse(statusCode = 0, body = null)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val MAX_BODY_CHARS = 65_536
    }
}
