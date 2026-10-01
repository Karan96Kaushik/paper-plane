package com.paperplane.semver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemverTest {

    @Test
    fun parsesStableTagsAndRejectsPrereleases() {
        assertEquals(Semver(1, 2, 3), Semver.parseTag("v1.2.3"))
        assertEquals(Semver(1, 2, 3), Semver.parseTag("1.2.3"))
        assertNull(Semver.parseTag("v1.2"))
        assertNull(Semver.parseTag("v1.2.3-rc.1"))
        assertNull(Semver.parseTag("release"))
    }

    @Test
    fun choosesHighestStableTag() {
        assertEquals(
            "v1.10.0",
            SemverResolver.highestStableTag(listOf("v1.2.0", "v1.10.0", "v1.9.9-rc.1", "note"))
        )
    }

    @Test
    fun conventionalCommitsSelectTheHighestBump() {
        assertEquals(BumpLevel.PATCH, SemverResolver.bumpLevel("fix: crash on empty title"))
        assertEquals(BumpLevel.MINOR, SemverResolver.bumpLevel("feat(republish): select types"))
        assertEquals(BumpLevel.MAJOR, SemverResolver.bumpLevel("feat!: change storage"))
        assertEquals(
            BumpLevel.MAJOR,
            SemverResolver.bumpLevel("refactor: api\n\nBREAKING CHANGE: removed the old key")
        )
        assertEquals(BumpLevel.NONE, SemverResolver.bumpLevel("chore: tidy gradle"))
        assertEquals(
            BumpLevel.MINOR,
            SemverResolver.highestBump(
                listOf("fix: one", "chore: two", "feat: three")
            )
        )
    }

    @Test
    fun taggedCommitIsTheReleaseVersion() {
        val resolved = SemverResolver.resolve(
            base = Semver(1, 2, 3),
            headIsRelease = true,
            commitMessages = listOf("feat: ignored because this commit is the tag"),
            distance = 0,
            commitCount = 40
        )
        assertEquals("1.2.3", resolved.name)
        assertEquals(40, resolved.code)
    }

    @Test
    fun fixesAfterATagBecomeAPatchPrerelease() {
        val resolved = SemverResolver.resolve(
            base = Semver(1, 2, 3),
            headIsRelease = false,
            commitMessages = listOf("fix: notification channel"),
            distance = 1,
            commitCount = 41
        )
        assertEquals("1.2.4-dev.1", resolved.name)
        assertEquals(41, resolved.code)
    }

    @Test
    fun featuresWinOverFixes() {
        assertEquals(
            "1.3.0-dev.2",
            SemverResolver.versionName(
                base = Semver(1, 2, 3),
                headIsRelease = false,
                commitMessages = listOf("fix: a", "feat: b"),
                distance = 2
            )
        )
    }

    @Test
    fun nonReleasingCommitsKeepTheCurrentVersion() {
        assertEquals(
            "1.2.3-dev.4",
            SemverResolver.versionName(
                base = Semver(1, 2, 3),
                headIsRelease = false,
                commitMessages = listOf("chore: docs", "ci: workflow"),
                distance = 4
            )
        )
    }

    @Test
    fun repositoryWithoutTagsStartsAtOneZeroZero() {
        assertEquals(
            "1.0.0-dev.3",
            SemverResolver.versionName(
                base = null,
                headIsRelease = false,
                commitMessages = listOf("chore: init"),
                distance = 3
            )
        )
        assertEquals(
            "1.1.0-dev.3",
            SemverResolver.versionName(
                base = null,
                headIsRelease = false,
                commitMessages = listOf("feat: first feature"),
                distance = 3
            )
        )
    }

    @Test
    fun overridesReplaceCalculatedValues() {
        val resolved = SemverResolver.resolve(
            base = Semver(1, 0, 0),
            headIsRelease = true,
            commitMessages = emptyList(),
            distance = 0,
            commitCount = 4,
            versionNameOverride = "9.9.9-rc.1",
            versionCodeOverride = 900
        )
        assertEquals("9.9.9-rc.1", resolved.name)
        assertEquals(900, resolved.code)
    }

    @Test
    fun versionCodeNeverDropsToZero() {
        assertEquals(1, SemverResolver.versionCode(0))
        assertEquals(1, SemverResolver.versionCode(-5))
    }
}
