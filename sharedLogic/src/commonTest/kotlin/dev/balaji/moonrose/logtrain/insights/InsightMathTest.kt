package dev.balaji.moonrose.logtrain.insights

import dev.balaji.moonrose.logtrain.calendar.WeekStart
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsightMathTest {
    @Test
    fun `slope is zero for fewer than two points and for constant series`() {
        assertEquals(0.0, InsightMath.slopePerDay(emptyList()))
        assertEquals(0.0, InsightMath.slopePerDay(listOf(SparkPoint(0, 5.0))))
        assertEquals(0.0, InsightMath.slopePerDay(listOf(SparkPoint(0, 5.0), SparkPoint(7, 5.0))))
        // All points on the same day: no x-variance, no slope.
        assertEquals(0.0, InsightMath.slopePerDay(listOf(SparkPoint(3, 1.0), SparkPoint(3, 9.0))))
    }

    @Test
    fun `slope recovers a perfect line`() {
        val points = listOf(SparkPoint(0, 10.0), SparkPoint(7, 17.0), SparkPoint(14, 24.0))
        assertTrue(abs(InsightMath.slopePerDay(points) - 1.0) < 1e-9)
    }

    @Test
    fun `plateau needs at least three points`() {
        assertFalse(InsightMath.isPlateau(listOf(SparkPoint(0, 100.0), SparkPoint(7, 100.0))))
    }

    @Test
    fun `flat series is a plateau and a rising one is not`() {
        val flat = listOf(SparkPoint(0, 100.0), SparkPoint(7, 100.2), SparkPoint(14, 99.9), SparkPoint(21, 100.1))
        assertTrue(InsightMath.isPlateau(flat))

        val rising = listOf(SparkPoint(0, 100.0), SparkPoint(7, 102.5), SparkPoint(14, 105.0))
        assertFalse(InsightMath.isPlateau(rising))
    }

    @Test
    fun `streaks count consecutive weeks and reset on gaps`() {
        // Sundays (epoch day 0 = Thu 1970-01-01; day 3 = Sunday).
        val weekA = 3L // week starts day 3
        val days = setOf(weekA + 1, weekA + 7, weekA + 14, /* gap */ weekA + 28, weekA + 35)
        val result = InsightMath.weeklyStreaks(days, todayEpochDay = weekA + 36, WeekStart.SUNDAY)
        assertEquals(2, result.currentWeeks)
        assertEquals(3, result.longestWeeks)
    }

    @Test
    fun `a current week without a session yet does not break the streak`() {
        val weekA = 3L
        val days = setOf(weekA, weekA + 7)
        // Today is in the third week; no session logged there yet.
        val result = InsightMath.weeklyStreaks(days, todayEpochDay = weekA + 15, WeekStart.SUNDAY)
        assertEquals(2, result.currentWeeks)
    }

    @Test
    fun `streak is zero after more than a week off`() {
        val weekA = 3L
        val days = setOf(weekA, weekA + 7)
        val result = InsightMath.weeklyStreaks(days, todayEpochDay = weekA + 25, WeekStart.SUNDAY)
        assertEquals(0, result.currentWeeks)
        assertEquals(2, result.longestWeeks)
    }

    @Test
    fun `empty history has no streaks`() {
        assertEquals(StreakResult(0, 0), InsightMath.weeklyStreaks(emptySet(), 100, WeekStart.SUNDAY))
    }

    @Test
    fun `formatNumber trims trailing zeros consistently across targets`() {
        assertEquals("12450", InsightMath.formatNumber(12450.0, 0))
        assertEquals("12450", InsightMath.formatNumber(12450.04))
        assertEquals("82.5", InsightMath.formatNumber(82.5))
        assertEquals("-0.5", InsightMath.formatNumber(-0.5))
        assertEquals("0", InsightMath.formatNumber(0.04))
        assertEquals("117", InsightMath.formatNumber(116.99))
    }
}
