package com.barontech.paperplane.notification

import android.content.Context
import android.content.SharedPreferences

internal class CurtisPayloadStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun saveActive(
        id: Int,
        raw: Map<String, String>,
        group: String?,
        title: String,
        channelId: String
    ) {
        synchronized(lock) {
            val meta = linkedMapOf(
                "title" to title,
                "channel" to channelId,
                "shade" to "1",
                "at" to System.currentTimeMillis().toString()
            )
            if (!group.isNullOrBlank()) meta["group"] = group
            prefs.edit()
                .putString(payloadKey(id), CurtisJson.encodeStringMap(raw))
                .putString(metaKey(id), CurtisJson.encodeStringMap(meta))
                .commit()
            pruneLocked()
        }
    }

    fun hide(id: Int) {
        synchronized(lock) {
            val meta = readMeta(id)?.toMutableMap() ?: return
            meta["shade"] = "0"
            prefs.edit().putString(metaKey(id), CurtisJson.encodeStringMap(meta)).commit()
        }
    }

    fun remove(id: Int) {
        synchronized(lock) {
            prefs.edit()
                .remove(payloadKey(id))
                .remove(metaKey(id))
                .remove(expectKey(id))
                .commit()
        }
    }

    fun loadRaw(id: Int): Map<String, String>? {
        synchronized(lock) {
            val encoded = prefs.getString(payloadKey(id), null) ?: return null
            return CurtisJson.decodeStringMap(encoded)
        }
    }

    fun groupOf(id: Int): String? = synchronized(lock) { readMeta(id)?.get("group") }

    fun shadeMembers(group: String): List<ShadeMember> {
        synchronized(lock) {
            return prefs.all.keys
                .filter { it.startsWith(META_PREFIX) }
                .mapNotNull { key ->
                    val id = key.removePrefix(META_PREFIX).toIntOrNull() ?: return@mapNotNull null
                    val meta = readMeta(id) ?: return@mapNotNull null
                    if (meta["group"] != group || meta["shade"] != "1") return@mapNotNull null
                    ShadeMember(
                        id = id,
                        title = meta["title"].orEmpty(),
                        channelId = meta["channel"] ?: CurtisPayload.CHANNEL_DEFAULT
                    )
                }
        }
    }

    fun expectProgrammaticCancel(id: Int) {
        synchronized(lock) {
            prefs.edit().putBoolean(expectKey(id), true).commit()
        }
    }

    fun consumeProgrammaticCancel(id: Int): Boolean {
        synchronized(lock) {
            val expected = prefs.getBoolean(expectKey(id), false)
            if (expected) prefs.edit().remove(expectKey(id)).commit()
            return expected
        }
    }

    private fun pruneLocked() {
        val metas = prefs.all.keys
            .filter { it.startsWith(META_PREFIX) }
            .mapNotNull { key ->
                val id = key.removePrefix(META_PREFIX).toIntOrNull() ?: return@mapNotNull null
                val at = readMeta(id)?.get("at")?.toLongOrNull() ?: 0L
                id to at
            }
            .sortedBy { it.second }
        if (metas.size <= MAX_STORED) return
        val editor = prefs.edit()
        metas.take(metas.size - MAX_STORED).forEach { (id, _) ->
            editor.remove(payloadKey(id)).remove(metaKey(id)).remove(expectKey(id))
        }
        editor.commit()
    }

    private fun readMeta(id: Int): Map<String, String>? {
        val encoded = prefs.getString(metaKey(id), null) ?: return null
        return CurtisJson.decodeStringMap(encoded)
    }

    private fun payloadKey(id: Int) = "$PAYLOAD_PREFIX$id"
    private fun metaKey(id: Int) = "$META_PREFIX$id"
    private fun expectKey(id: Int) = "$EXPECT_PREFIX$id"

    data class ShadeMember(val id: Int, val title: String, val channelId: String)

    companion object {
        private const val PREFS = "curtis_notifications"
        private const val PAYLOAD_PREFIX = "p."
        private const val META_PREFIX = "m."
        private const val EXPECT_PREFIX = "x."
        private const val MAX_STORED = 40
    }
}
