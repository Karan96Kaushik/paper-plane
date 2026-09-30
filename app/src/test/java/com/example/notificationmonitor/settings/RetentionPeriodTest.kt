package com.example.notificationmonitor.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetentionPeriodTest {

    @Test
    fun cutoffForSevenDays() {
        val now = 1_700_000_000_000L
        val cutoff = RetentionPeriod.SEVEN_DAYS.cutoffMillis(now)!!
        assertEquals(now - 7L * 24 * 60 * 60 * 1000, cutoff)
    }

    @Test
    fun foreverHasNoCutoff() {
        assertNull(RetentionPeriod.FOREVER.cutoffMillis(System.currentTimeMillis()))
    }

    @Test
    fun fromStorageDefaultsToSevenDays() {
        assertEquals(RetentionPeriod.SEVEN_DAYS, RetentionPeriod.fromStorage(null))
        assertEquals(RetentionPeriod.ONE_DAY, RetentionPeriod.fromStorage("ONE_DAY"))
    }
}
