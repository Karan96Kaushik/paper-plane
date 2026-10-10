package com.barontech.paperplane.sync

/**
 * Reads auth token fields without parsing the full document. GoTrue always places
 * `access_token` and `refresh_token` before the large `user` object, so this stays
 * valid even when the tail of the JSON is huge or truncated.
 */
internal object SupabaseAuthJsonScalars {

    fun extractString(json: String, fieldName: String): String? {
        val needle = "\"$fieldName\""
        var searchFrom = 0
        while (searchFrom < json.length) {
            val keyIndex = json.indexOf(needle, searchFrom)
            if (keyIndex < 0) return null
            if (!isFieldKeyAt(json, keyIndex, fieldName)) {
                searchFrom = keyIndex + 1
                continue
            }
            var index = keyIndex + needle.length
            while (index < json.length && json[index].isWhitespace()) index++
            if (index >= json.length || json[index] != ':') {
                searchFrom = keyIndex + 1
                continue
            }
            index++
            while (index < json.length && json[index].isWhitespace()) index++
            if (index >= json.length || json[index] != '"') {
                searchFrom = keyIndex + 1
                continue
            }
            index++
            val out = StringBuilder()
            while (index < json.length) {
                when (val char = json[index++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= json.length) return null
                        when (val escaped = json[index++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > json.length) return null
                                val hex = json.substring(index, index + 4)
                                index += 4
                                out.append(hex.toInt(16).toChar())
                            }
                            else -> out.append(escaped)
                        }
                    }
                    else -> out.append(char)
                }
            }
            return null
        }
        return null
    }

    fun extractLong(json: String, fieldName: String): Long? {
        val needle = "\"$fieldName\""
        var searchFrom = 0
        while (searchFrom < json.length) {
            val keyIndex = json.indexOf(needle, searchFrom)
            if (keyIndex < 0) return null
            if (!isFieldKeyAt(json, keyIndex, fieldName)) {
                searchFrom = keyIndex + 1
                continue
            }
            var index = keyIndex + needle.length
            while (index < json.length && json[index].isWhitespace()) index++
            if (index >= json.length || json[index] != ':') {
                searchFrom = keyIndex + 1
                continue
            }
            index++
            while (index < json.length && json[index].isWhitespace()) index++
            val start = index
            if (start < json.length && json[start] == '-') index++
            val digitsStart = index
            while (index < json.length && json[index].isDigit()) index++
            if (index == digitsStart) {
                searchFrom = keyIndex + 1
                continue
            }
            return json.substring(start, index).toLongOrNull()
        }
        return null
    }

    private fun isFieldKeyAt(json: String, keyIndex: Int, fieldName: String): Boolean {
        if (keyIndex <= 0) return false
        val before = json[keyIndex - 1]
        if (before != '{' && before != ',' && !before.isWhitespace()) return false
        val expected = "\"$fieldName\""
        return keyIndex + expected.length <= json.length &&
            json.regionMatches(keyIndex, expected, 0, expected.length)
    }
}
