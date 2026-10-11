package com.barontech.paperplane.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseCredentialPasteTest {

    @Test
    fun blankPasteMatchesNothing() {
        val parsed = SupabaseCredentialPaste.parse("  \n\n  ")
        assertFalse(parsed.matchedAny())
        assertNull(parsed.projectUrl)
        assertNull(parsed.password)
    }

    @Test
    fun readsLabeledLinesAndTrimsEachValue() {
        val parsed = SupabaseCredentialPaste.parse(
            """
            Project URL: https://abc.supabase.co/
            Publishable key:  sb_publishable_abc
            Table: notifications
            Email: ada@example.com
            Password: my password
            
            """.trimIndent()
        )
        assertEquals("https://abc.supabase.co", parsed.projectUrl)
        assertEquals("sb_publishable_abc", parsed.publishableKey)
        assertEquals("notifications", parsed.table)
        assertEquals("ada@example.com", parsed.email)
        assertEquals("my password", parsed.password)
    }

    @Test
    fun readsEnvNamesAndQuotedValues() {
        val parsed = SupabaseCredentialPaste.parse(
            """
            NEXT_PUBLIC_SUPABASE_URL="https://abc.supabase.co"
            NEXT_PUBLIC_SUPABASE_ANON_KEY='sb_publishable_xyz'
            SUPABASE_EMAIL=ada@example.com
            SUPABASE_PASSWORD=secret
            """.trimIndent()
        )
        assertEquals("https://abc.supabase.co", parsed.projectUrl)
        assertEquals("sb_publishable_xyz", parsed.publishableKey)
        assertEquals("ada@example.com", parsed.email)
        assertEquals("secret", parsed.password)
    }

    @Test
    fun readsUnlabeledLinesInFieldOrder() {
        val parsed = SupabaseCredentialPaste.parse(
            """
            https://abc.supabase.co
            sb_publishable_xxx
            my_table
            ada@example.com
            hunter2
            """.trimIndent()
        )
        assertEquals("https://abc.supabase.co", parsed.projectUrl)
        assertEquals("sb_publishable_xxx", parsed.publishableKey)
        assertEquals("my_table", parsed.table)
        assertEquals("ada@example.com", parsed.email)
        assertEquals("hunter2", parsed.password)
    }

    @Test
    fun singleUnlabeledExtraLineIsThePassword() {
        val parsed = SupabaseCredentialPaste.parse(
            """
            https://abc.supabase.co
            sb_publishable_xxx
            ada@example.com
            correct horse
            """.trimIndent()
        )
        assertNull(parsed.table)
        assertEquals("correct horse", parsed.password)
    }

    @Test
    fun defaultTableNameIsNotTreatedAsThePassword() {
        val parsed = SupabaseCredentialPaste.parse(
            """
            https://abc.supabase.co
            notifications
            """.trimIndent()
        )
        assertEquals("notifications", parsed.table)
        assertNull(parsed.password)
    }

    @Test
    fun removesLineBreaksInsideUrlEmailAndKey() {
        val head = "sb_publishable_" + "a".repeat(40)
        val parsed = SupabaseCredentialPaste.parse(
            """
            https://abc.
            supabase.co
            $head
            bcdef
            ada@
            example.com
            s3cret!
            """.trimIndent()
        )
        assertEquals("https://abc.supabase.co", parsed.projectUrl)
        assertEquals(head + "bcdef", parsed.publishableKey)
        assertEquals("ada@example.com", parsed.email)
        assertEquals("s3cret!", parsed.password)
    }

    @Test
    fun joinsAWrappedLegacyJwtAndLeavesThePassword() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9." + "a".repeat(40) + ".signature"
        val parsed = SupabaseCredentialPaste.parse("$jwt\nmore\nada@example.com\np@ss")
        assertEquals(jwt + "more", parsed.publishableKey)
        assertEquals("ada@example.com", parsed.email)
        assertEquals("p@ss", parsed.password)
    }

    @Test
    fun readsASingleSpacedLine() {
        val parsed = SupabaseCredentialPaste.parse(
            "https://abc.supabase.co sb_publishable_xxx notifications ada@example.com hunter2"
        )
        assertEquals("https://abc.supabase.co", parsed.projectUrl)
        assertEquals("sb_publishable_xxx", parsed.publishableKey)
        assertEquals("notifications", parsed.table)
        assertEquals("ada@example.com", parsed.email)
        assertEquals("hunter2", parsed.password)
    }

    @Test
    fun stackedLabelsTakeTheFollowingLines() {
        val parsed = SupabaseCredentialPaste.parse(
            """
            Project URL
            https://abc.supabase.co
            Publishable key
            sb_publishable_abc
            Password
            secret
            """.trimIndent()
        )
        assertEquals("https://abc.supabase.co", parsed.projectUrl)
        assertEquals("sb_publishable_abc", parsed.publishableKey)
        assertEquals("secret", parsed.password)
    }

    @Test
    fun partialPasteLeavesUnmatchedFieldsEmpty() {
        val parsed = SupabaseCredentialPaste.parse("ada@example.com")
        assertEquals("ada@example.com", parsed.email)
        assertNull(parsed.projectUrl)
        assertNull(parsed.publishableKey)
        assertNull(parsed.password)
        assertTrue(parsed.summary().startsWith("Filled email."))
    }

    @Test
    fun removesLineBreaksInsideAPassword() {
        val parsed = SupabaseCredentialPaste.parse(
            """
            https://abc.supabase.co
            ada@example.com
            correct
            horse
            """.trimIndent()
        )
        assertNull(parsed.table)
        assertEquals("correcthorse", parsed.password)
    }

    @Test
    fun doesNotGlueAFollowingPasswordOntoTheKey() {
        val parsed = SupabaseCredentialPaste.parse(
            """
            Publishable key: sb_publishable_abc
            hunter2
            ada@example.com
            """.trimIndent()
        )
        assertEquals("sb_publishable_abc", parsed.publishableKey)
        assertEquals("ada@example.com", parsed.email)
        assertEquals("hunter2", parsed.password)
    }

    @Test
    fun identifiesASecretKeySoValidationCanRejectIt() {
        val parsed = SupabaseCredentialPaste.parse("sb_secret_do_not_use")
        assertEquals("sb_secret_do_not_use", parsed.publishableKey)
    }
}
