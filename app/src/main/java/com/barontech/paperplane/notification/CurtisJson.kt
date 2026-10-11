package com.barontech.paperplane.notification

internal object CurtisJson {

    fun encodeStringMap(map: Map<String, String>): String {
        return map.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${encodeString(key)}:${encodeString(value)}"
        }
    }

    fun decodeStringMap(text: String): Map<String, String>? {
        val value = runCatching { JsonParser(text).parse() }.getOrNull() ?: return null
        val objectValue = value as? Map<*, *> ?: return null
        val decoded = linkedMapOf<String, String>()
        for ((key, entry) in objectValue) {
            if (key !is String || entry !is String) return null
            decoded[key] = entry
        }
        return decoded
    }

    fun parseActions(text: String): List<Pair<String, String>> {
        if (text.isBlank()) return emptyList()
        val value = runCatching { JsonParser(text).parse() }.getOrNull() ?: return emptyList()
        val array = value as? List<*> ?: return emptyList()
        return array.mapNotNull { item ->
            val objectValue = item as? Map<*, *> ?: return@mapNotNull null
            val id = objectValue["id"] as? String ?: return@mapNotNull null
            val label = objectValue["label"] as? String ?: return@mapNotNull null
            id to label
        }
    }

    private fun encodeString(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char.code < 0x20) {
                    out.append("\\u")
                    out.append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
        return out.toString()
    }
}

private class JsonParser(private val text: String) {
    private var index = 0

    fun parse(): Any? {
        val value = readValue()
        skip()
        if (index != text.length) error("trailing")
        return value
    }

    private fun readValue(): Any? {
        skip()
        if (index >= text.length) error("eof")
        return when (val char = text[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            '-', in '0'..'9' -> readNumber()
            else -> error("bad $char")
        }
    }

    private fun readObject(): Map<String, Any?> {
        index++
        val values = linkedMapOf<String, Any?>()
        skip()
        if (peek('}')) {
            index++
            return values
        }
        while (index < text.length) {
            skip()
            if (index >= text.length || text[index] != '"') error("key")
            val key = readString()
            skip()
            if (!peek(':')) error("colon")
            index++
            values[key] = readValue()
            skip()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    return values
                }
                else -> error("object")
            }
        }
        error("object")
    }

    private fun readArray(): List<Any?> {
        index++
        val values = mutableListOf<Any?>()
        skip()
        if (peek(']')) {
            index++
            return values
        }
        while (index < text.length) {
            values += readValue()
            skip()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    return values
                }
                else -> error("array")
            }
        }
        error("array")
    }

    private fun readString(): String {
        index++
        val out = StringBuilder()
        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' -> {
                    index++
                    return out.toString()
                }
                char == '\\' -> {
                    index++
                    if (index >= text.length) error("escape")
                    when (val escaped = text[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 4 > text.length) error("unicode")
                            val hex = text.substring(index, index + 4)
                            index += 4
                            out.append(hex.toInt(16).toChar())
                        }
                        else -> error("escape")
                    }
                }
                char.code < 0x20 -> error("control")
                else -> {
                    out.append(char)
                    index++
                }
            }
        }
        error("string")
    }

    private fun readNumber(): String {
        val start = index
        if (text[index] == '-') index++
        if (index >= text.length || !text[index].isDigit()) error("number")
        while (index < text.length && text[index].isDigit()) index++
        if (index < text.length && text[index] == '.') {
            index++
            if (index >= text.length || !text[index].isDigit()) error("number")
            while (index < text.length && text[index].isDigit()) index++
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            if (index >= text.length || !text[index].isDigit()) error("number")
            while (index < text.length && text[index].isDigit()) index++
        }
        return text.substring(start, index)
    }

    private fun literal(expected: String, value: Any?): Any? {
        if (!text.startsWith(expected, index)) error(expected)
        index += expected.length
        return value
    }

    private fun skip() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun peek(char: Char): Boolean = index < text.length && text[index] == char
}
