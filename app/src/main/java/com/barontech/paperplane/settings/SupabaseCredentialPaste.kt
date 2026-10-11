package com.barontech.paperplane.settings

/**
 * Splits one pasted block into Supabase settings fields.
 * Newlines between credentials stay as separators. Line breaks inside a URL, key, or email are removed.
 */
data class SupabaseCredentialPaste(
    val projectUrl: String? = null,
    val publishableKey: String? = null,
    val table: String? = null,
    val email: String? = null,
    val password: String? = null
) {
    fun matchedAny(): Boolean = matchedLabels().isNotEmpty()

    fun matchedLabels(): List<String> = buildList {
        if (!projectUrl.isNullOrBlank()) add("project URL")
        if (!publishableKey.isNullOrBlank()) add("publishable key")
        if (!table.isNullOrBlank()) add("table")
        if (!email.isNullOrBlank()) add("email")
        if (!password.isNullOrBlank()) add("password")
    }

    fun summary(): String {
        val labels = matchedLabels()
        if (labels.isEmpty()) {
            return "No credentials found. Include a project URL, publishable key, email, or password."
        }
        return "Filled ${joinLabels(labels)}. Review the fields, then sign in or save."
    }

    companion object {
        private val EMPTY = SupabaseCredentialPaste()
        private val ASSIGNMENT = Regex("^([^=:\\n]{1,80}?)\\s*[:=]\\s*(.*)$")
        private val TABLE_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        private val WHITESPACE = Regex("\\s+")
        private val INVISIBLE = Regex("[\\u200B\\u200C\\u200D\\uFEFF]")

        fun parse(raw: String): SupabaseCredentialPaste {
            val logical = mergeWrappedLines(raw)
            if (logical.isEmpty()) return EMPTY

            val assigned = linkedMapOf<Field, String>()
            val unlabeled = mutableListOf<String>()
            var index = 0
            while (index < logical.size) {
                val line = logical[index]
                val labeled = matchLabel(line)
                if (labeled != null) {
                    val consumed = consumeValue(logical, index + 1, labeled.field, labeled.inline)
                    if (consumed.value.isNotEmpty()) {
                        assigned[labeled.field] = consumed.value
                    }
                    index = consumed.nextIndex
                    continue
                }
                val bare = bareLabel(line)
                if (bare != null) {
                    val consumed = consumeValue(logical, index + 1, bare, "")
                    if (consumed.value.isNotEmpty()) {
                        assigned[bare] = consumed.value
                        index = consumed.nextIndex
                        continue
                    }
                }
                unlabeled += expandUnlabeled(line)
                index++
            }

            val pending = mutableListOf<String>()
            for (token in unlabeled) {
                val field = classifyToken(collapse(token))
                if (field != null && field !in assigned) {
                    assigned[field] = clean(field, token)
                } else if (field == null) {
                    pending += token.trim()
                }
            }
            assignTableAndPassword(assigned, pending)

            return SupabaseCredentialPaste(
                projectUrl = assigned[Field.URL],
                publishableKey = assigned[Field.KEY],
                table = assigned[Field.TABLE],
                email = assigned[Field.EMAIL],
                password = assigned[Field.PASSWORD]
            )
        }

        private fun consumeValue(
            lines: List<String>,
            start: Int,
            field: Field,
            inline: String
        ): Consumed {
            val chunks = mutableListOf<String>()
            if (inline.isNotEmpty()) chunks += inline
            var index = start
            if (chunks.isEmpty() && index < lines.size) {
                val next = lines[index]
                if (matchLabel(next) == null && bareLabel(next) == null) {
                    chunks += next
                    index++
                }
            }
            while (index < lines.size && chunks.isNotEmpty() && shouldJoin(chunks.joinToString(""), lines[index])) {
                chunks += lines[index]
                index++
            }
            return Consumed(joinChunks(field, chunks), index)
        }

        private fun assignTableAndPassword(
            assigned: MutableMap<Field, String>,
            pending: List<String>
        ) {
            val tokens = pending.map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return
            when {
                Field.TABLE in assigned && Field.PASSWORD in assigned -> return
                Field.TABLE in assigned -> {
                    assigned[Field.PASSWORD] = tokens.joinToString("")
                }
                Field.PASSWORD in assigned -> {
                    tokens.firstOrNull { isTableLine(it) && looksLikeExplicitTable(it) }
                        ?.let { assigned[Field.TABLE] = it }
                }
                tokens.size == 1 -> {
                    val only = tokens[0]
                    if (only.equals(SupabaseConfig.DEFAULT_TABLE, ignoreCase = true)) {
                        assigned[Field.TABLE] = only
                    } else {
                        assigned[Field.PASSWORD] = only
                    }
                }
                else -> {
                    val tableIndex = tokens.indexOfFirst {
                        isTableLine(it) && looksLikeExplicitTable(it) && it != tokens.last()
                    }
                    if (tableIndex >= 0) {
                        assigned[Field.TABLE] = tokens[tableIndex]
                        assigned[Field.PASSWORD] = tokens.filterIndexed { index, _ -> index != tableIndex }
                            .joinToString("")
                    } else {
                        assigned[Field.PASSWORD] = tokens.joinToString("")
                    }
                }
            }
        }

        private fun mergeWrappedLines(raw: String): List<String> {
            val output = mutableListOf<String>()
            var current: String? = null
            val lines = raw
                .replace(INVISIBLE, "")
                .replace("\u00a0", " ")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
            for (rawLine in lines) {
                val line = stripBullet(rawLine).trim()
                if (line.isEmpty()) {
                    if (current != null) {
                        output += current
                        current = null
                    }
                    continue
                }
                if (current == null) {
                    current = line
                    continue
                }
                if (shouldJoin(current, line)) {
                    current = current.trimEnd() + line.trim()
                } else {
                    output += current
                    current = line
                }
            }
            if (current != null) output += current
            return output
        }

        private fun shouldJoin(current: String, next: String): Boolean {
            if (matchLabel(current) != null || matchLabel(next) != null) return false
            if (bareLabel(current) != null || bareLabel(next) != null) return false
            if (classifyToken(collapse(next)) != null) return false
            val collapsed = collapse(current)
            val joined = collapsed + collapse(next)
            if (isUrlPrefix(collapsed)) {
                if (!isHostContinuation(next)) return false
                val currentOk = SupabaseConfig.normalizeProjectUrl(collapsed) != null
                val joinedOk = SupabaseConfig.normalizeProjectUrl(joined) != null
                if (!currentOk && (joinedOk || collapsed.endsWith("."))) return true
                if (currentOk && collapsed.endsWith(".")) return true
                return false
            }
            if (SupabaseConfig.normalizeEmail(joined) != null &&
                SupabaseConfig.normalizeEmail(collapsed) == null &&
                (collapsed.contains('@') || next.trim().startsWith("@"))
            ) {
                return true
            }
            if (collapsed.startsWith("eyJ") && isKeyFragment(next)) {
                val incomplete = !SupabaseConfig.isLegacyJwtApiKey(collapsed)
                if (incomplete || current.length >= 48) return true
            }
            if ((collapsed.startsWith("sb_publishable", ignoreCase = true) ||
                    collapsed.startsWith("sb_secret", ignoreCase = true)) &&
                isKeyFragment(next) &&
                current.length >= 48
            ) {
                return true
            }
            return false
        }

        private fun expandUnlabeled(line: String): List<String> {
            val parts = line.split(WHITESPACE).filter { it.isNotEmpty() }
            if (parts.size <= 1) return listOf(line.trim())
            if (parts.none { classifyToken(it) != null }) return listOf(line.trim())
            val tokens = mutableListOf<String>()
            var index = 0
            while (index < parts.size) {
                if (classifyToken(parts[index]) != null) {
                    tokens += parts[index]
                    index++
                    continue
                }
                val start = index
                while (index < parts.size && classifyToken(parts[index]) == null) index++
                val run = parts.subList(start, index)
                val splitTable = run.size >= 2 && matchesTable(run[0]) && looksLikeExplicitTable(run[0])
                if (splitTable) {
                    tokens += run[0]
                    tokens += run.subList(1, run.size).joinToString(" ")
                } else {
                    tokens += run.joinToString(" ")
                }
            }
            return tokens
        }

        private fun matchLabel(line: String): Labeled? {
            val match = ASSIGNMENT.matchEntire(line.trim()) ?: return null
            val field = fieldForLabel(match.groupValues[1]) ?: return null
            return Labeled(field, unquote(match.groupValues[2]).trim())
        }

        private fun bareLabel(line: String): Field? {
            val trimmed = line.trim()
            if (trimmed.any { it == ':' || it == '=' }) return null
            if (trimmed.any { it.isWhitespace() }) return null
            return fieldForLabel(trimmed)
        }

        private fun fieldForLabel(raw: String): Field? {
            val key = raw.trim()
                .removePrefix("export")
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
            if (key.isEmpty()) return null
            return when {
                key == "url" || key.endsWith("_url") -> Field.URL
                key == "key" || key == "apikey" || key == "publishable" || key == "anon" ||
                    key.endsWith("publishable_key") || key.endsWith("anon_key") ||
                    key.endsWith("api_key") -> Field.KEY
                key == "table" || key.endsWith("_table") || key.endsWith("table_name") -> Field.TABLE
                key == "email" || key == "e_mail" || key.endsWith("_email") -> Field.EMAIL
                key == "pass" || key == "password" || key.endsWith("_password") -> Field.PASSWORD
                else -> null
            }
        }

        private fun classifyToken(compact: String): Field? {
            if (compact.isEmpty()) return null
            if (isUrlPrefix(compact)) return Field.URL
            if (isKey(compact)) return Field.KEY
            if (SupabaseConfig.normalizeEmail(compact) != null) return Field.EMAIL
            return null
        }

        private fun clean(field: Field, token: String): String = joinChunks(field, listOf(token))

        private fun joinChunks(field: Field, chunks: List<String>): String {
            val cleaned = chunks.map { unquote(it).trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) return ""
            return when (field) {
                Field.PASSWORD -> cleaned.joinToString("")
                Field.URL -> {
                    val compact = collapse(cleaned.joinToString(""))
                    SupabaseConfig.normalizeProjectUrl(compact) ?: compact
                }
                Field.EMAIL -> {
                    val compact = collapse(cleaned.joinToString(""))
                    SupabaseConfig.normalizeEmail(compact) ?: compact
                }
                Field.KEY, Field.TABLE -> collapse(cleaned.joinToString(""))
            }
        }

        private fun unquote(raw: String): String {
            var value = raw.trim().trimEnd(',').trim()
            if (value.length >= 2) {
                val quote = value.first()
                if ((quote == '"' || quote == '\'') && value.last() == quote) {
                    value = value.substring(1, value.length - 1).trim()
                }
            }
            return value
        }

        private fun stripBullet(line: String): String {
            val trimmed = line.trim()
            return when {
                trimmed.startsWith("- ") -> trimmed.removePrefix("- ")
                trimmed.startsWith("* ") -> trimmed.removePrefix("* ")
                trimmed.startsWith("• ") -> trimmed.removePrefix("• ")
                else -> trimmed
            }
        }

        private fun collapse(value: String): String = value.replace(WHITESPACE, "")

        private fun isUrlPrefix(value: String): Boolean {
            return value.startsWith("https://", ignoreCase = true) ||
                value.startsWith("http://", ignoreCase = true)
        }

        private fun isKey(value: String): Boolean {
            return value.startsWith("sb_publishable_", ignoreCase = true) ||
                value.startsWith("sb_secret_", ignoreCase = true) ||
                SupabaseConfig.isLegacyJwtApiKey(value)
        }

        private fun isKeyFragment(line: String): Boolean {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
            return trimmed.all { char ->
                char.isLetterOrDigit() || char == '_' || char == '-' || char == '.' ||
                    char == '+' || char == '/' || char == '='
            }
        }

        private fun isHostContinuation(line: String): Boolean {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
            return trimmed.all { char -> char.isLetterOrDigit() || char == '.' || char == '-' }
        }

        private fun matchesTable(value: String): Boolean = TABLE_PATTERN.matches(value.trim())

        private fun isTableLine(value: String): Boolean {
            return value.none { it.isWhitespace() } && matchesTable(value)
        }

        private fun looksLikeExplicitTable(value: String): Boolean {
            return value.equals(SupabaseConfig.DEFAULT_TABLE, ignoreCase = true) || '_' in value
        }

        private fun joinLabels(labels: List<String>): String = when (labels.size) {
            0 -> ""
            1 -> labels[0]
            2 -> "${labels[0]} and ${labels[1]}"
            else -> labels.dropLast(1).joinToString(", ") + ", and " + labels.last()
        }

        private enum class Field { URL, KEY, TABLE, EMAIL, PASSWORD }

        private data class Labeled(val field: Field, val inline: String)

        private data class Consumed(val value: String, val nextIndex: Int)
    }
}
