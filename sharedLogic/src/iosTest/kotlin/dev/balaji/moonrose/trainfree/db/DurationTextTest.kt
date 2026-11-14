package dev.balaji.moonrose.trainfree.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurationTextTest {
    @Test
    fun filterAutoInsertsColonBeforeLastTwoDigits() {
        assertEquals("", "".filterDurationText())
        assertEquals("3", "3".filterDurationText())
        assertEquals("30", "30".filterDurationText())
        assertEquals("1:30", "130".filterDurationText())
        assertEquals("23:59", "2359".filterDurationText())
        // Typing a digit after "1:30" rolls the grouping.
        assertEquals("13:05", "1:305".filterDurationText())
        // Deleting from "13:05" rolls back.
        assertEquals("1:30", "13:0".filterDurationText())
        // Non-digits are dropped; minutes and hours are clamped.
        assertEquals("30", "3a0".filterDurationText())
        assertEquals("1:59", "175".filterDurationText())
        assertEquals("23:59", "9999".filterDurationText())
    }

    @Test
    fun parseTreatsBareDigitsAsMinutes() {
        assertNull(parseDurationSeconds(""))
        assertEquals(0, parseDurationSeconds("0"))
        assertEquals(1800, parseDurationSeconds("30"))
        assertEquals(5400, parseDurationSeconds("90"))
        assertEquals(5400, parseDurationSeconds("1:30"))
        assertEquals(3600, parseDurationSeconds("1:"))
    }

    @Test
    fun formatRoundTripsThroughParse() {
        assertEquals("", formatDurationText(null))
        assertEquals("", formatDurationText(0))
        assertEquals("30", formatDurationText(1800))
        assertEquals("1:30", formatDurationText(5400))
        assertEquals("1:00", formatDurationText(3600))
        // Sub-minute remainders are dropped (nothing writes them today).
        assertEquals("30", formatDurationText(1830))
        for (seconds in listOf(60, 1800, 3600, 5400, 86340 / 60 * 60)) {
            assertEquals(
                seconds / 60 * 60,
                parseDurationSeconds(formatDurationText(seconds)),
                "round trip for $seconds",
            )
        }
    }

    @Test
    fun readOnlySummaryAlwaysShowsHoursAndMinutes() {
        assertEquals("0:30", formatDurationHhMm(1800))
        assertEquals("1:05", formatDurationHhMm(3900))
    }
}
