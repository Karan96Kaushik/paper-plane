package com.paperplane.semver

/**
 * Semantic version calculated from a stable git tag and Conventional Commits.
 *
 * Untagged commits stay a prerelease (`1.3.0-dev.4`) until that exact commit is tagged.
 */
data class Semver(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<Semver> {
    fun bump(level: BumpLevel): Semver = when (level) {
        BumpLevel.NONE -> this
        BumpLevel.PATCH -> copy(patch = patch + 1)
        BumpLevel.MINOR -> copy(minor = minor + 1, patch = 0)
        BumpLevel.MAJOR -> Semver(major + 1, 0, 0)
    }

    override fun compareTo(other: Semver): Int {
        return compareValuesBy(this, other, Semver::major, Semver::minor, Semver::patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val stableTag = Regex("""^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")

        val initial = Semver(1, 0, 0)

        fun parseTag(name: String): Semver? {
            val match = stableTag.matchEntire(name.trim()) ?: return null
            return Semver(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt()
            )
        }
    }
}

enum class BumpLevel {
    NONE,
    PATCH,
    MINOR,
    MAJOR
}

data class ResolvedVersion(
    val name: String,
    val code: Int
)

object SemverResolver {

    fun highestStableTag(tagNames: List<String>): String? {
        return tagNames
            .mapNotNull { name -> Semver.parseTag(name)?.let { version -> name to version } }
            .maxByOrNull { it.second }
            ?.first
    }

    fun bumpLevel(message: String): BumpLevel {
        val header = message.lineSequence().firstOrNull().orEmpty().trim()
        val headerMatch = conventionalHeader.matchEntire(header)
        if (headerMatch != null && headerMatch.groupValues[2] == "!") {
            return BumpLevel.MAJOR
        }
        if (breakingFooter.containsMatchIn(message)) {
            return BumpLevel.MAJOR
        }
        return when (headerMatch?.groupValues?.get(1)?.lowercase()) {
            "feat" -> BumpLevel.MINOR
            "fix" -> BumpLevel.PATCH
            else -> BumpLevel.NONE
        }
    }

    fun highestBump(messages: List<String>): BumpLevel {
        return messages.fold(BumpLevel.NONE) { current, message ->
            val next = bumpLevel(message)
            if (next > current) next else current
        }
    }

    fun resolve(
        base: Semver?,
        headIsRelease: Boolean,
        commitMessages: List<String>,
        distance: Int,
        commitCount: Int,
        versionNameOverride: String? = null,
        versionCodeOverride: Int? = null
    ): ResolvedVersion {
        val nameOverride = versionNameOverride?.trim()?.takeIf { it.isNotEmpty() }
        val codeOverride = versionCodeOverride?.takeIf { it > 0 }
        val name = nameOverride ?: versionName(
            base = base,
            headIsRelease = headIsRelease,
            commitMessages = commitMessages,
            distance = distance.coerceAtLeast(0)
        )
        val code = codeOverride ?: versionCode(commitCount)
        return ResolvedVersion(name = name, code = code)
    }

    fun versionName(
        base: Semver?,
        headIsRelease: Boolean,
        commitMessages: List<String>,
        distance: Int
    ): String {
        if (headIsRelease && base != null) {
            return base.toString()
        }
        val starting = base ?: Semver.initial
        val level = highestBump(commitMessages)
        val next = if (base == null && level == BumpLevel.NONE) {
            starting
        } else {
            starting.bump(level)
        }
        val commitsAhead = distance.coerceAtLeast(0)
        if (commitsAhead == 0) {
            return next.toString()
        }
        val released = if (level == BumpLevel.NONE) starting else next
        return "$released-dev.$commitsAhead"
    }

    fun versionCode(commitCount: Int): Int = commitCount.coerceAtLeast(1)

    private val conventionalHeader = Regex("""^([a-zA-Z]+)(?:\([^)\n]*\))?(!)?:.*$""")
    private val breakingFooter = Regex("""(?m)^BREAKING[ -]CHANGE\s*:""")
}
