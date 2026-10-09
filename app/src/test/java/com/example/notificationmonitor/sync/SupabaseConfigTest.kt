package com.example.notificationmonitor.sync

import com.example.notificationmonitor.settings.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {

    @Test
    fun acceptsHttpsProjectOrigin() {
        val config = SupabaseConfig(
            enabled = true,
            projectUrl = "https://abc.supabase.co/",
            publishableKey = " sb_publishable_abc ",
            table = "",
            email = "ada@example.com",
            accountEmail = "ada@example.com",
            userId = USER_ID,
            accessToken = "access",
            refreshToken = "refresh",
            accessTokenExpiresAt = 9_000L
        )
        assertEquals("https://abc.supabase.co", config.normalizedUrl())
        assertEquals("notifications", config.normalizedTable())
        assertEquals("sb_publishable_abc", config.normalizedPublishableKey())
        assertEquals("https://abc.supabase.co/rest/v1/notifications", config.destination())
        assertEquals(USER_ID, config.session()?.userId)
        assertTrue(config.isReady())
        assertNull(config.validationError())
    }

    @Test
    fun requiresASignedInUser() {
        val config = SupabaseConfig(
            enabled = true,
            projectUrl = "https://abc.supabase.co",
            publishableKey = "sb_publishable_x"
        )
        assertFalse(config.isReady())
        assertTrue(config.validationError()?.contains("Sign in with your Supabase email") == true)
    }

    @Test
    fun normalizesSignInPasswordFromPaste() {
        assertEquals("secret", SupabaseConfig.normalizeSignInPassword("secret\n"))
        assertNull(SupabaseConfig.normalizeSignInPassword("   "))
        assertNull(SupabaseConfig.normalizeSignInPassword("bad\npassword"))
    }

    @Test
    fun rejectsSecretKeys() {
        val config = SupabaseConfig(
            projectUrl = "https://abc.supabase.co",
            publishableKey = "sb_secret_abc"
        )
        assertNull(config.normalizedPublishableKey())
        assertEquals(
            "Use the publishable key (sb_publishable_...), not a secret key.",
            config.projectError()
        )
    }

    @Test
    fun rejectsNonHttpsOrPathedUrls() {
        assertNull(SupabaseConfig.normalizeProjectUrl("http://abc.supabase.co"))
        assertNull(SupabaseConfig.normalizeProjectUrl("https://abc.supabase.co/rest/v1"))
        assertNull(SupabaseConfig.normalizeProjectUrl("https://user:secret@abc.supabase.co"))
        assertNull(SupabaseConfig.normalizeProjectUrl("https://abc.supabase.co?x=1"))
        assertNull(SupabaseConfig.normalizeProjectUrl("not a url"))
    }

    @Test
    fun rejectsUnsafeTableNames() {
        val config = SupabaseConfig(
            projectUrl = "https://abc.supabase.co",
            publishableKey = "sb_publishable_x",
            table = "noti-fications"
        )
        assertNull(config.normalizedTable())
        assertFalse(config.isReady())
        assertEquals(
            "Table name can only use letters, numbers, and underscores.",
            config.validationError()
        )
    }

    @Test
    fun disabledConfigIsNotReady() {
        val config = SupabaseConfig(
            enabled = false,
            projectUrl = "https://abc.supabase.co",
            publishableKey = "sb_publishable_x",
            userId = USER_ID,
            accessToken = "access",
            refreshToken = "refresh"
        )
        assertNull(config.validationError())
        assertFalse(config.isReady())
    }

    companion object {
        private const val USER_ID = "11111111-1111-4111-8111-111111111111"
    }
}
