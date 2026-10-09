package com.example.notificationmonitor.sync

import java.net.HttpURLConnection
import java.net.URL

internal interface SupabaseTransport {
    fun post(
        url: String,
        apiKey: String,
        bearerToken: String,
        body: String,
        preferMinimal: Boolean = true
    ): SupabaseHttpResponse

    fun get(url: String, apiKey: String, bearerToken: String): SupabaseHttpResponse
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
        apiKey: String,
        bearerToken: String,
        body: String,
        preferMinimal: Boolean
    ): SupabaseHttpResponse = exchange("POST", url, apiKey, bearerToken, body, preferMinimal)

    override fun get(url: String, apiKey: String, bearerToken: String): SupabaseHttpResponse =
        exchange("GET", url, apiKey, bearerToken, null, preferMinimal = false)

    private fun exchange(
        method: String,
        url: String,
        apiKey: String,
        bearerToken: String,
        body: String?,
        preferMinimal: Boolean
    ): SupabaseHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (preferMinimal) {
                    connection.setRequestProperty("Prefer", "return=minimal")
                }
                connection.outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use { input ->
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(MAX_BODY_CHARS)
                    val read = reader.read(buffer)
                    if (read <= 0) null else String(buffer, 0, read)
                }
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
