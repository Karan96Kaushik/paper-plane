package com.example.notificationmonitor.sync

internal sealed class JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val value: String) : JsonValue()
    data object Other : JsonValue()
}

internal fun parseJsonObject(input: String): Map<String, JsonValue>? {
    return (JsonParser(input).parseValue() as? JsonValue.Obj)?.fields
}

private class JsonParser(private val input: String) {
    private var index = 0

    fun parseValue(): JsonValue? {
        skip()
        if (index >= input.length) return null
        return when (input[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't', 'f', 'n' -> parseLiteral()
            else -> parseNumber()
        }
    }

    private fun parseObject(): JsonValue? {
        if (!consume('{')) return null
        val fields = linkedMapOf<String, JsonValue>()
        skip()
        if (peek('}')) {
            index++
            return JsonValue.Obj(fields)
        }
        while (index < input.length) {
            skip()
            if (index >= input.length || input[index] != '"') return null
            val key = parseString()
            skip()
            if (!consume(':')) return null
            val value = parseValue() ?: return null
            fields[key] = value
            skip()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    return JsonValue.Obj(fields)
                }
                else -> return null
            }
        }
        return null
    }

    private fun parseArray(): JsonValue? {
        if (!consume('[')) return null
        skip()
        if (peek(']')) {
            index++
            return JsonValue.Other
        }
        while (index < input.length) {
            if (parseValue() == null) return null
            skip()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    return JsonValue.Other
                }
                else -> return null
            }
        }
        return null
    }

    private fun parseString(): String {
        index++
        val out = StringBuilder()
        while (index < input.length) {
            val char = input[index++]
            if (char == '"') return out.toString()
            if (char != '\\') {
                out.append(char)
                continue
            }
            if (index >= input.length) break
            when (val escaped = input[index++]) {
                '"', '\\', '/' -> out.append(escaped)
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    val hex = input.substring(index, (index + 4).coerceAtMost(input.length))
                    index += hex.length
                    out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                }
                else -> out.append(escaped)
            }
        }
        return out.toString()
    }

    private fun parseLiteral(): JsonValue {
        val start = index
        while (index < input.length && input[index].isLetter()) index++
        val word = input.substring(start, index)
        return if (word == "true" || word == "false" || word == "null") JsonValue.Other else JsonValue.Other
    }

    private fun parseNumber(): JsonValue? {
        val start = index
        if (peek('-') || peek('+')) index++
        val digits = index
        while (index < input.length && (input[index].isDigit() || input[index] in ".eE+-")) index++
        if (index == digits) return null
        return JsonValue.Num(input.substring(start, index))
    }

    private fun skip() {
        while (index < input.length && input[index].isWhitespace()) index++
    }

    private fun peek(expected: Char): Boolean = index < input.length && input[index] == expected

    private fun consume(expected: Char): Boolean {
        if (!peek(expected)) return false
        index++
        return true
    }
}
