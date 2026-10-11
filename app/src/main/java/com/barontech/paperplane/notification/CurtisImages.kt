package com.barontech.paperplane.notification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal object CurtisImages {

    private const val TIMEOUT_MS = 4_000
    private const val MAX_BYTES = 2_000_000
    private const val MAX_PIXELS = 2_000_000

    private val cache = object : LruCache<String, Bitmap>(6) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun load(url: String, maxEdge: Int): Bitmap? {
        val key = "$maxEdge:$url"
        cache.get(key)?.let { return it }
        val bitmap = download(url, maxEdge) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun download(url: String, maxEdge: Int): Bitmap? {
        val first = open(url) ?: return null
        val bytes = first.bytes ?: follow(first.redirect)?.let { open(it)?.bytes } ?: return null
        return decode(bytes, maxEdge)
    }

    private fun follow(location: String?): String? {
        if (location.isNullOrBlank() || !location.startsWith("https://")) return null
        return location.takeIf { allowed(it) }
    }

    private fun open(url: String): Download? {
        if (!allowed(url)) return null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doInput = true
            setRequestProperty("Accept", "image/*")
        }
        return try {
            val code = connection.responseCode
            if (code in 300..399) {
                Download(bytes = null, redirect = connection.getHeaderField("Location"))
            } else if (code != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val type = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
                if (type != null && !type.startsWith("image/")) return null
                val length = connection.contentLength
                if (length > MAX_BYTES) return null
                connection.inputStream.use { stream ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val read = stream.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return null
                        buffer.write(chunk, 0, read)
                    }
                    Download(bytes = buffer.toByteArray(), redirect = null)
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun decode(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return shrink(decoded, maxEdge)
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (sample < 64) {
            val sampledWidth = width / sample
            val sampledHeight = height / sample
            val pixels = sampledWidth.toLong() * sampledHeight
            if (sampledWidth <= maxEdge && sampledHeight <= maxEdge && pixels <= MAX_PIXELS) break
            sample *= 2
        }
        return sample
    }

    private fun shrink(bitmap: Bitmap, maxEdge: Int): Bitmap {
        var current = bitmap
        while (current.width > maxEdge || current.height > maxEdge || current.byteCount > 500_000) {
            val nextWidth = (current.width / 2).coerceAtLeast(1)
            val nextHeight = (current.height / 2).coerceAtLeast(1)
            if (nextWidth == current.width && nextHeight == current.height) break
            val scaled = Bitmap.createScaledBitmap(current, nextWidth, nextHeight, true)
            if (scaled != current) current.recycle()
            current = scaled
            if (current.width <= 32 || current.height <= 32) break
        }
        return current
    }

    private fun allowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.host.isNullOrBlank() || !uri.userInfo.isNullOrEmpty()) return false
        return true
    }

    private data class Download(val bytes: ByteArray?, val redirect: String?)
}
