package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.insights.BodyWeightTrend
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val weightUnit: WeightUnit = WeightUnit.METRIC,
)

object BodyMetricsRepository {
    /** Logged entries, newest first. */
    val entries: Flow<List<BodyWeightEntry>> =
        AppDb.db.bodyMetricDao().observeAll(PROFILE_ID).map { rows ->
            val zone = TimeZone.currentSystemDefault()
            rows.map { it.toEntry(zone) }
        }

    /**
     * Logs a weight entry in the user's current display unit and keeps the
     * profile's scalar weight (stored canonically in kg) in sync with the
     * newest entry.
     */
    suspend fun logWeight(weight: Double, loggedAt: Long = nowMillis()): Long {
        val now = nowMillis()
        val unit = UserSettingsRepository.settings.first().weightUnit
        val id = AppDb.db.bodyMetricDao().insert(
            BodyMetricEntity(
                profileId = PROFILE_ID,
                weight = weight,
                weightUnit = unit,
                loggedAt = loggedAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        // The newest entry may not be the one just inserted (backdated logs).
        AppDb.db.bodyMetricDao().latest(PROFILE_ID)?.let {
            AppDb.db.profileDao().updateWeight(it.weightUnit.toKilograms(it.weight), now)
        }
        return id
    }

    suspend fun delete(id: Long) = AppDb.db.bodyMetricDao().delete(id)

    /** Trend over the last [days] days in the user's current display unit; null when empty. */
    suspend fun trend(days: Int = 90): BodyWeightTrend? {
        val zone = TimeZone.currentSystemDefault()
        val today = WeekMath.todayEpochDay()
        val targetUnit = UserSettingsRepository.settings.first().weightUnit
        val rows = AppDb.db.bodyMetricDao()
            .getInRange(PROFILE_ID, startOfDayMillis(today - days, zone))
        return trendFrom(rows.map { it.toEntry(zone) }, today, targetUnit)
    }
}

private fun BodyMetricEntity.toEntry(zone: TimeZone): BodyWeightEntry =
    BodyWeightEntry(
        id = id,
        loggedAt = loggedAt,
        epochDay = millisToEpochDay(loggedAt, zone),
        weight = weight,
        weightUnit = weightUnit,
    )

/** [entries] oldest first or newest first; both are handled, converted to [targetUnit]. */
internal fun trendFrom(
    entries: List<BodyWeightEntry>,
    todayEpochDay: Long,
    targetUnit: WeightUnit = WeightUnit.METRIC,
): BodyWeightTrend? {
    if (entries.isEmpty()) return null
    val points = entries
        .sortedBy { it.loggedAt }
        .map { SparkPoint(it.epochDay, it.weightUnit.convertWeight(it.weight, targetUnit)) }
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
