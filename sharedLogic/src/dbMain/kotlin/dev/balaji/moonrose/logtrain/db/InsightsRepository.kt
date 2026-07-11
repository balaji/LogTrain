package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.insights.BodyWeightTrend
import dev.balaji.moonrose.logtrain.insights.ExercisePr
import dev.balaji.moonrose.logtrain.insights.ExerciseProgression
import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import dev.balaji.moonrose.logtrain.insights.TrainingFrequency
import dev.balaji.moonrose.logtrain.insights.WeeklyVolumePoint
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** One completed working set, keyed to the local calendar day it was performed. */
internal data class SetPoint(
    val epochDay: Long,
    /** performed_at millis of the owning session; distinct values = distinct sessions. */
    val sessionKey: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
)

/**
 * Deterministic analytics over completed workout history. All numbers are
 * computed here (never by a language model); the chat tools and the Progress
 * cards both read from this repository. Days are keyed on performed_at in the
 * device timezone at emission time, like CalendarRepository.
 */
object InsightsRepository {
    /** Total volume per week, oldest first, covering exactly [weeks] weeks up to the current one. */
    suspend fun weeklyVolume(weeks: Int = 8): List<WeeklyVolumePoint> {
        val settings = UserSettingsRepository.settings.first()
        val today = WeekMath.todayEpochDay()
        val firstWeekStart = WeekMath.weekStartOnOrBefore(today, settings.weekStart) - 7L * (weeks - 1)
        val sets = completedSetsSince(startOfDayMillis(firstWeekStart))
        return weeklyVolumeFrom(sets, weeks, today, settings.weekStart)
    }

    /** Best sets by estimated 1RM, one per exercise, best first; all-time. */
    suspend fun personalRecords(limit: Int = 5, exerciseName: String? = null): List<ExercisePr> =
        personalRecordsFrom(completedSetsSince(0), limit, exerciseName)

    /** Weekly best estimated 1RM for one exercise over the last [weeks] weeks; null when it has no history. */
    suspend fun exerciseProgression(exerciseName: String, weeks: Int = 12): ExerciseProgression? {
        val settings = UserSettingsRepository.settings.first()
        val today = WeekMath.todayEpochDay()
        val firstWeekStart = WeekMath.weekStartOnOrBefore(today, settings.weekStart) - 7L * (weeks - 1)
        val sets = completedSetsSince(startOfDayMillis(firstWeekStart))
        return progressionFrom(sets, exerciseName, today, settings.weekStart)
    }

    suspend fun trainingFrequency(): TrainingFrequency {
        val settings = UserSettingsRepository.settings.first()
        val zone = TimeZone.currentSystemDefault()
        val sessionDays = AppDb.db.workoutSessionDao().getCompletedPerformedAt(PROFILE_ID)
            .map { millisToEpochDay(it, zone) }
        return frequencyFrom(sessionDays, WeekMath.todayEpochDay(), settings.weekStart)
    }

    /** Exercises that have at least one completed working set, most trained first. */
    suspend fun trainedExerciseNames(limit: Int = 10): List<String> =
        completedSetsSince(0)
            .filter { it.weight > 0 && it.reps > 0 }
            .groupBy { it.exerciseName }
            .entries
            .sortedByDescending { it.value.size }
            .take(limit)
            .map { it.key }

    /** Everything the Progress cards need, recomputed on any relevant DB change. */
    fun observeCards(): Flow<List<InsightCard>> {
        val startMillis = startOfDayMillis(WeekMath.todayEpochDay() - 365)
        return combine(
            AppDb.db.workoutSessionDetailDao().observeCompletedSets(PROFILE_ID, startMillis),
            AppDb.db.workoutSessionDao().observeCompletedPerformedAt(PROFILE_ID),
            BodyMetricsRepository.entries,
            UserSettingsRepository.settings,
        ) { rows, performedAt, weightEntries, settings ->
            val zone = TimeZone.currentSystemDefault()
            val today = WeekMath.todayEpochDay()
            buildCards(
                sets = rows.map { it.toSetPoint(zone) },
                sessionDays = performedAt.map { millisToEpochDay(it, zone) },
                bodyWeight = trendFrom(weightEntries.filter { it.epochDay >= today - 90 }, today),
                todayEpochDay = today,
                weekStart = settings.weekStart,
            )
        }
    }

    private suspend fun completedSetsSince(startMillis: Long): List<SetPoint> {
        val zone = TimeZone.currentSystemDefault()
        return AppDb.db.workoutSessionDetailDao()
            .getCompletedSets(PROFILE_ID, startMillis)
            .map { it.toSetPoint(zone) }
    }
}

internal fun CompletedSetRow.toSetPoint(zone: TimeZone): SetPoint =
    SetPoint(
        epochDay = millisToEpochDay(performedAt, zone),
        sessionKey = performedAt,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        weight = actualWeight ?: 0.0,
        reps = actualReps ?: 0,
    )

@OptIn(ExperimentalTime::class)
internal fun millisToEpochDay(millis: Long, zone: TimeZone): Long =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date.toEpochDays()

@OptIn(ExperimentalTime::class)
internal fun startOfDayMillis(epochDay: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
    LocalDate.fromEpochDays(epochDay).atStartOfDayIn(zone).toEpochMilliseconds()

// Pure transforms below are internal (not private) so jvmTest can exercise
// them without a database, mirroring toUserSettings.

internal fun weeklyVolumeFrom(
    sets: List<SetPoint>,
    weeks: Int,
    todayEpochDay: Long,
    weekStart: WeekStart,
): List<WeeklyVolumePoint> {
    val firstWeekStart = WeekMath.weekStartOnOrBefore(todayEpochDay, weekStart) - 7L * (weeks - 1)
    val byWeek = sets
        .filter { it.epochDay >= firstWeekStart }
        .groupBy { WeekMath.weekStartOnOrBefore(it.epochDay, weekStart) }
    return (0 until weeks).map { i ->
        val weekStartDay = firstWeekStart + 7L * i
        val weekSets = byWeek[weekStartDay].orEmpty()
        WeeklyVolumePoint(
            weekStartEpochDay = weekStartDay,
            totalVolume = weekSets.sumOf { it.weight * it.reps },
            setCount = weekSets.size,
            sessionCount = weekSets.distinctBy { it.sessionKey }.size,
        )
    }
}

internal fun personalRecordsFrom(
    sets: List<SetPoint>,
    limit: Int,
    exerciseName: String? = null,
): List<ExercisePr> =
    sets
        .filter { it.weight > 0 && it.reps > 0 }
        .filter { exerciseName == null || it.exerciseName.equals(exerciseName, ignoreCase = true) }
        .groupBy { it.exerciseId }
        .map { (_, exerciseSets) ->
            val best = exerciseSets.maxBy { InsightMath.epley1Rm(it.weight, it.reps) }
            ExercisePr(
                exerciseId = best.exerciseId,
                exerciseName = best.exerciseName,
                weight = best.weight,
                reps = best.reps,
                epochDay = best.epochDay,
                est1Rm = InsightMath.epley1Rm(best.weight, best.reps),
            )
        }
        .sortedByDescending { it.est1Rm }
        .take(limit)

internal fun progressionFrom(
    sets: List<SetPoint>,
    exerciseName: String,
    todayEpochDay: Long,
    weekStart: WeekStart,
): ExerciseProgression? {
    val relevant = sets.filter {
        it.exerciseName.equals(exerciseName, ignoreCase = true) && it.weight > 0 && it.reps > 0
    }
    if (relevant.isEmpty()) return null
    val weeklyBest = relevant
        .groupBy { WeekMath.weekStartOnOrBefore(it.epochDay, weekStart) }
        .map { (weekStartDay, weekSets) ->
            SparkPoint(weekStartDay, weekSets.maxOf { InsightMath.epley1Rm(it.weight, it.reps) })
        }
        .sortedBy { it.epochDay }
    return ExerciseProgression(
        exerciseId = relevant.first().exerciseId,
        exerciseName = relevant.first().exerciseName,
        weeklyBest1Rm = weeklyBest,
        slopePerWeek = InsightMath.slopePerDay(weeklyBest) * 7,
        plateau = InsightMath.isPlateau(weeklyBest),
    )
}

internal fun frequencyFrom(
    sessionDays: List<Long>,
    todayEpochDay: Long,
    weekStart: WeekStart,
): TrainingFrequency {
    val streaks = InsightMath.weeklyStreaks(sessionDays.toSet(), todayEpochDay, weekStart)
    val last84 = sessionDays.count { it > todayEpochDay - 84 }
    return TrainingFrequency(
        currentStreakWeeks = streaks.currentWeeks,
        longestStreakWeeks = streaks.longestWeeks,
        sessionsLast7Days = sessionDays.count { it > todayEpochDay - 7 },
        sessionsLast30Days = sessionDays.count { it > todayEpochDay - 30 },
        avgPerWeek12w = last84 / 12.0,
    )
}

internal fun buildCards(
    sets: List<SetPoint>,
    sessionDays: List<Long>,
    bodyWeight: BodyWeightTrend?,
    todayEpochDay: Long,
    weekStart: WeekStart,
): List<InsightCard> {
    val cards = mutableListOf<InsightCard>()

    if (sessionDays.isNotEmpty()) {
        cards += volumeCard(sets, todayEpochDay, weekStart)
        cards += frequencyCard(sessionDays, todayEpochDay, weekStart)
        cards += progressionCards(sets, todayEpochDay, weekStart)
    }
    if (bodyWeight != null) cards += bodyWeightCard(bodyWeight)
    return cards
}

private fun volumeCard(
    sets: List<SetPoint>,
    todayEpochDay: Long,
    weekStart: WeekStart,
): InsightCard {
    val weeklies = weeklyVolumeFrom(sets, weeks = 8, todayEpochDay, weekStart)
    val thisWeek = weeklies.last()
    val priorAvg = weeklies.dropLast(1).takeLast(4).map { it.totalVolume }.average()
    val detailKey: String
    val detailArgs: List<String>
    val trend: String
    if (priorAvg > 0) {
        val pct = (thisWeek.totalVolume - priorAvg) / priorAvg * 100
        val sign = if (pct >= 0) "+" else ""
        detailKey = InsightCard.DETAIL_PCT_VS_4_WEEK_AVG
        detailArgs = listOf("$sign${InsightMath.formatNumber(pct, 0)}")
        trend = if (pct >= 5) "up" else if (pct <= -5) "down" else "flat"
    } else {
        detailKey = InsightCard.DETAIL_SETS_IN_SESSIONS
        detailArgs = listOf("${thisWeek.setCount}", "${thisWeek.sessionCount}")
        trend = "none"
    }
    return InsightCard(
        id = "weekly_volume",
        kind = "volume",
        exerciseName = null,
        headlineKey = InsightCard.HEADLINE_VOLUME_THIS_WEEK,
        headlineArgs = listOf(InsightMath.formatNumber(thisWeek.totalVolume, 0)),
        detailKey = detailKey,
        detailArgs = detailArgs,
        trend = trend,
        spark = weeklies.map { SparkPoint(it.weekStartEpochDay, it.totalVolume) },
    )
}

private fun frequencyCard(sessionDays: List<Long>, todayEpochDay: Long, weekStart: WeekStart): InsightCard {
    val freq = frequencyFrom(sessionDays, todayEpochDay, weekStart)
    val streaking = freq.currentStreakWeeks >= 1
    return InsightCard(
        id = "frequency",
        kind = "frequency",
        exerciseName = null,
        headlineKey = if (streaking) InsightCard.HEADLINE_STREAK_WEEKS else InsightCard.HEADLINE_NO_STREAK,
        headlineArgs = if (streaking) listOf("${freq.currentStreakWeeks}") else emptyList(),
        detailKey = if (freq.longestStreakWeeks > 1) {
            InsightCard.DETAIL_WORKOUTS_30_DAYS_LONGEST
        } else {
            InsightCard.DETAIL_WORKOUTS_30_DAYS
        },
        detailArgs = if (freq.longestStreakWeeks > 1) {
            listOf("${freq.sessionsLast30Days}", "${freq.longestStreakWeeks}")
        } else {
            listOf("${freq.sessionsLast30Days}")
        },
        trend = "none",
        spark = emptyList(),
    )
}

/** One card per top-volume exercise (max 3) that has at least two weeks of history. */
private fun progressionCards(
    sets: List<SetPoint>,
    todayEpochDay: Long,
    weekStart: WeekStart,
): List<InsightCard> {
    val window = todayEpochDay - 7L * 12
    val recent = sets.filter { it.weight > 0 && it.reps > 0 && it.epochDay >= window }
    return recent
        .groupBy { it.exerciseName }
        .entries
        .sortedByDescending { (_, s) -> s.sumOf { it.weight * it.reps } }
        .take(3)
        .mapNotNull { (name, _) -> progressionFrom(recent, name, todayEpochDay, weekStart) }
        .filter { it.weeklyBest1Rm.size >= 2 }
        .map { p ->
            val latest = p.weeklyBest1Rm.last().value
            val weeksSpanned = p.weeklyBest1Rm.size
            if (p.plateau) {
                InsightCard(
                    id = "progression:${p.exerciseId}",
                    kind = "plateau",
                    exerciseName = p.exerciseName,
                    headlineKey = InsightCard.HEADLINE_PLATEAU_AT,
                    headlineArgs = listOf(InsightMath.formatNumber(latest)),
                    detailKey = InsightCard.DETAIL_FLAT_ACROSS_WEEKS,
                    detailArgs = listOf("$weeksSpanned"),
                    trend = "flat",
                    spark = p.weeklyBest1Rm,
                )
            } else {
                val sign = if (p.slopePerWeek >= 0) "+" else ""
                InsightCard(
                    id = "progression:${p.exerciseId}",
                    kind = "progression",
                    exerciseName = p.exerciseName,
                    headlineKey = InsightCard.HEADLINE_EST_1RM,
                    headlineArgs = listOf(InsightMath.formatNumber(latest)),
                    detailKey = InsightCard.DETAIL_SLOPE_PER_WEEK,
                    detailArgs = listOf("$sign${InsightMath.formatNumber(p.slopePerWeek)}", "$weeksSpanned"),
                    trend = if (p.slopePerWeek >= 0) "up" else "down",
                    spark = p.weeklyBest1Rm,
                )
            }
        }
}

private fun bodyWeightCard(trend: BodyWeightTrend): InsightCard {
    val sign = if (trend.change30Days >= 0) "+" else ""
    val hasHistory = trend.points.size > 1
    return InsightCard(
        id = "body_weight",
        kind = "body_weight",
        exerciseName = null,
        headlineKey = InsightCard.HEADLINE_LATEST_BODY_WEIGHT,
        headlineArgs = listOf(InsightMath.formatNumber(trend.latest)),
        detailKey = if (hasHistory) InsightCard.DETAIL_CHANGE_30_DAYS else InsightCard.DETAIL_LOG_REGULARLY,
        detailArgs = if (hasHistory) listOf("$sign${InsightMath.formatNumber(trend.change30Days)}") else emptyList(),
        trend = when {
            trend.points.size < 2 -> "none"
            trend.slopePerWeek > 0.05 -> "up"
            trend.slopePerWeek < -0.05 -> "down"
            else -> "flat"
        },
        spark = trend.points,
    )
}
