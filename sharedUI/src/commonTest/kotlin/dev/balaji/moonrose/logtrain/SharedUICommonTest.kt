package dev.balaji.moonrose.logtrain

import dev.balaji.moonrose.logtrain.screens.formatCountdown
import dev.balaji.moonrose.logtrain.screens.formatMonthDay
import dev.balaji.moonrose.logtrain.screens.formatMonthYear
import dev.balaji.moonrose.logtrain.screens.remainingSecondsUntil
import dev.balaji.moonrose.logtrain.screens.yAxisBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedUICommonTest {

    @Test
    fun formatCountdownFormatsMinutesAndPaddedSeconds() {
        assertEquals("2:00", formatCountdown(120L))
        assertEquals("1:05", formatCountdown(65L))
        assertEquals("0:09", formatCountdown(9L))
        assertEquals("0:00", formatCountdown(0L))
        assertEquals("0:00", formatCountdown(-5L))
    }

    @Test
    fun remainingSecondsUntilCeilsSubSecondRemainders() {
        val endsAt = 200_000L
        assertEquals(120L, remainingSecondsUntil(endsAtMillis = endsAt, nowMillis = 80_000L))
        assertEquals(120L, remainingSecondsUntil(endsAtMillis = endsAt, nowMillis = 80_001L))
        assertEquals(119L, remainingSecondsUntil(endsAtMillis = endsAt, nowMillis = 81_000L))
        assertEquals(1L, remainingSecondsUntil(endsAtMillis = endsAt, nowMillis = 199_999L))
        assertEquals(0L, remainingSecondsUntil(endsAtMillis = endsAt, nowMillis = 200_000L))
        assertEquals(0L, remainingSecondsUntil(endsAtMillis = endsAt, nowMillis = 205_000L))
    }

    @Test
    fun formatMonthDayAndYearUseDayPartsAndLocalizedMonths() {
        val nov14 = 20771L // 2026-11-14
        val may03 = 20211L // 2025-05-03
        assertEquals("Nov-14", formatMonthDay(nov14))
        assertEquals("Nov 26", formatMonthYear(nov14))

        val swedishMonths = listOf("Jan", "Feb", "Mar", "Apr", "Maj", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dec")
        assertEquals("Maj-03", formatMonthDay(may03, swedishMonths))
        assertEquals("Maj 25", formatMonthYear(may03, swedishMonths))
    }

    @Test
    fun yAxisBoundsExpandsFlatAndNarrowSeriesToAtLeastTwoUnits() {
        assertEquals(99.0 to 101.0, yAxisBounds(100.0, 100.0))
        assertEquals(99.0 to 101.0, yAxisBounds(100.0, 101.0))
        val (lo, hi) = yAxisBounds(5.6, 7.2)
        assertTrue(lo <= 5.6 && hi >= 7.2 && hi - lo >= 2.0)
        assertEquals(80.0 to 120.0, yAxisBounds(80.0, 120.0))
    }
}
