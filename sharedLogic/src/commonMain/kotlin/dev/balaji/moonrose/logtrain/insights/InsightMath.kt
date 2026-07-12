package dev.balaji.moonrose.logtrain.insights

import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import kotlin.math.abs
import kotlin.math.round

/**
 * Pure statistics behind the Progress insights. Everything here is
 * deterministic Kotlin so the numbers shown on cards and quoted by the chat
 * tools never come from a language model.
 */
object InsightMath {
    /** Least-squares slope in value units per day; 0.0 for fewer than two points. */
    fun slopePerDay(points: List<SparkPoint>): Double {
        if (points.size < 2) return 0.0
        val n = points.size
        val meanX = points.sumOf { it.epochDay.toDouble() } / n
        val meanY = points.sumOf { it.value } / n
        var sxx = 0.0
        var sxy = 0.0
        for (p in points) {
            val dx = p.epochDay.toDouble() - meanX
            sxx += dx * dx
            sxy += dx * (p.value - meanY)
        }
        return if (sxx == 0.0) 0.0 else sxy / sxx
    }

    /**
     * A series is a plateau when at least three weekly bests move by less
     * than 0.5% of their mean per week in either direction.
     */
    fun isPlateau(weeklyBests: List<SparkPoint>): Boolean {
        if (weeklyBests.size < 3) return false
        val mean = weeklyBests.sumOf { it.value } / weeklyBests.size
        if (mean <= 0.0) return false
        val slopePerWeek = slopePerDay(weeklyBests) * 7
        return abs(slopePerWeek) < 0.005 * mean
    }

    /**
     * Consecutive-week streaks over the days that had a completed session.
     * The current week counts toward the streak only once it has a session;
     * until then the streak is anchored at last week, so a Monday without a
     * workout yet doesn't read as a broken streak.
     */
    fun weeklyStreaks(sessionEpochDays: Set<Long>, todayEpochDay: Long, weekStart: WeekStart): StreakResult {
        val weekSet = sessionEpochDays.mapTo(mutableSetOf()) { WeekMath.weekStartOnOrBefore(it, weekStart) }
        if (weekSet.isEmpty()) return StreakResult(0, 0)

        var longest = 1
        var run = 1
        var prev = Long.MIN_VALUE
        for (ws in weekSet.sorted()) {
            run = if (ws - prev == 7L) run + 1 else 1
            if (run > longest) longest = run
            prev = ws
        }

        val currentWeek = WeekMath.weekStartOnOrBefore(todayEpochDay, weekStart)
        var anchor = when {
            currentWeek in weekSet -> currentWeek
            currentWeek - 7 in weekSet -> currentWeek - 7
            else -> return StreakResult(0, longest)
        }
        var current = 0
        while (anchor in weekSet) {
            current++
            anchor -= 7
        }
        return StreakResult(current, longest)
    }

    /**
     * Rounds to at most [maxDecimals] places and drops trailing zeros,
     * producing the same string on every target (Double.toString differs
     * between JVM and JS for whole numbers).
     */
    fun formatNumber(value: Double, maxDecimals: Int = 1): String {
        var factor = 1L
        repeat(maxDecimals) { factor *= 10 }
        val scaled = round(value * factor).toLong()
        val sign = if (scaled < 0) "-" else ""
        val magnitude = abs(scaled)
        val whole = magnitude / factor
        val frac = magnitude % factor
        if (frac == 0L) return "$sign$whole"
        val digits = frac.toString().padStart(maxDecimals, '0').trimEnd('0')
        return "$sign$whole.$digits"
    }
}
