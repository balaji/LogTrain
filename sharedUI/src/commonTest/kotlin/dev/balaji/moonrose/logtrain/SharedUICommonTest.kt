package dev.balaji.moonrose.logtrain

import dev.balaji.moonrose.logtrain.screens.formatCountdown
import dev.balaji.moonrose.logtrain.screens.remainingSecondsUntil
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
