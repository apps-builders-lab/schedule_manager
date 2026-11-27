package io.github.sh_sunfaded.schedulemanager.domain

import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class JstTimeTest {
    @Test
    fun zoneIsAsiaTokyo() {
        assertEquals("Asia/Tokyo", JstTime.zone.id)
    }

    @Test
    fun formatUsesJstRegardlessOfDefaultTimeZone() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            // 2026-11-27T00:30:00Z は JST では 09:30
            val millis = Instant.parse("2026-11-27T00:30:00Z").toEpochMilli()
            assertEquals("2026-11-27 09:30", JstTime.format(millis))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun toEpochMillisInterpretsInputAsJst() {
        val millis = JstTime.toEpochMillis(LocalDateTime.of(2026, 11, 27, 9, 30))
        assertEquals(Instant.parse("2026-11-27T00:30:00Z").toEpochMilli(), millis)
    }

    @Test
    fun truncateToMinuteDropsSecondsAndMillis() {
        val millis = Instant.parse("2026-11-27T00:30:45.678Z").toEpochMilli()
        assertEquals(Instant.parse("2026-11-27T00:30:00Z").toEpochMilli(), JstTime.truncateToMinute(millis))
    }
}
