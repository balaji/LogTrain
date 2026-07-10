package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.insights.BodyWeightTrend
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone

/**
 * One logged body-weight entry. Primitive-typed for the SKIE bridge, like
 * the calendar models.
 */
data class BodyWeightEntry(
    val id: Long,
    val loggedAt: Long,
    val epochDay: Long,
    val weight: Double,
)

object BodyMetricsRepository {
    /** Logged entries, newest first. */
    val entries: Flow<List<BodyWeightEntry>> =
        AppDb.db.bodyMetricDao().observeAll(PROFILE_ID).map { rows ->
            val zone = TimeZone.currentSystemDefault()
            rows.map { it.toEntry(zone) }
        }

    /**
     * Logs a weight entry and keeps the profile's scalar weight (the app's
     * notion of "current weight") in sync with the newest entry.
     */
    suspend fun logWeight(weight: Double, loggedAt: Long = nowMillis()): Long {
        val now = nowMillis()
        val id = AppDb.db.bodyMetricDao().insert(
            BodyMetricEntity(profileId = PROFILE_ID, weight = weight, loggedAt = loggedAt, createdAt = now, updatedAt = now),
        )
        // The newest entry may not be the one just inserted (backdated logs).
        AppDb.db.bodyMetricDao().latest(PROFILE_ID)?.let {
            AppDb.db.profileDao().updateWeight(it.weight, now)
        }
        return id
    }

    suspend fun delete(id: Long) = AppDb.db.bodyMetricDao().delete(id)

    /** Trend over the last [days] days; null when nothing is logged in the window. */
    suspend fun trend(days: Int = 90): BodyWeightTrend? {
        val zone = TimeZone.currentSystemDefault()
        val today = WeekMath.todayEpochDay()
        val rows = AppDb.db.bodyMetricDao()
            .getInRange(PROFILE_ID, startOfDayMillis(today - days, zone))
        return trendFrom(rows.map { it.toEntry(zone) }, today)
    }
}

private fun BodyMetricEntity.toEntry(zone: TimeZone): BodyWeightEntry =
    BodyWeightEntry(
        id = id,
        loggedAt = loggedAt,
        epochDay = millisToEpochDay(loggedAt, zone),
        weight = weight,
    )

/** [entries] oldest first or newest first; both are handled. */
internal fun trendFrom(entries: List<BodyWeightEntry>, todayEpochDay: Long): BodyWeightTrend? {
    if (entries.isEmpty()) return null
    val points = entries
        .sortedBy { it.loggedAt }
        .map { SparkPoint(it.epochDay, it.weight) }
    val latest = points.last()
    // Baseline for the 30-day change: the entry closest to 30 days ago, only
    // considering entries older than the latest one.
    val target = todayEpochDay - 30
    val baseline = points.dropLast(1).minByOrNull { kotlin.math.abs(it.epochDay - target) }
    return BodyWeightTrend(
        points = points,
        latest = latest.value,
        latestEpochDay = latest.epochDay,
        change30Days = if (baseline != null) latest.value - baseline.value else 0.0,
        slopePerWeek = InsightMath.slopePerDay(points) * 7,
    )
}
