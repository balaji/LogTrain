package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.insights.BodyPartSlice
import dev.balaji.moonrose.logtrain.insights.BodyWeightTrend
import dev.balaji.moonrose.logtrain.insights.ExercisePr
import dev.balaji.moonrose.logtrain.insights.ExerciseProgression
import dev.balaji.moonrose.logtrain.insights.ExerciseSetCount
import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.insights.OneRmFormula
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import dev.balaji.moonrose.logtrain.insights.SparkSeries
import dev.balaji.moonrose.logtrain.insights.TrainingFrequency
import dev.balaji.moonrose.logtrain.insights.WeeklyVolumePoint
import dev.balaji.moonrose.logtrain.model.ProgressionCardConfig
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** Primary body part catalog key of the exercise; null when it has none. */
    val bodyPart: String?,
    val weight: Double,
    val reps: Int,
    val duration: Int,
    val distance: Double,
    val weightUnit: WeightUnit = WeightUnit.METRIC,
)

internal fun SetPoint.convertedTo(targetUnit: WeightUnit): SetPoint =
    if (weightUnit == targetUnit) {
        this
    } else {
        copy(
            weight = weightUnit.convertWeight(weight, targetUnit),
            distance = weightUnit.convertDistance(distance, targetUnit),
            weightUnit = targetUnit,
        )
    }

/**
 * Deterministic analytics over completed workout history. All numbers are
 * computed here (never by a language model); the Progress cards read from
 * this repository. Days are keyed on performed_at in the device timezone at
 * emission time, like CalendarRepository.
 */
object InsightsRepository {
    /**
     * Total volume per trailing 7-day window, oldest first, covering exactly
     * [weeks] windows up to the one ending today, converted to the user's
     * current display unit.
     */
    suspend fun weeklyVolume(weeks: Int = 8): List<WeeklyVolumePoint> {
        val settings = UserSettingsRepository.settings.first()
        val today = WeekMath.todayEpochDay()
        val firstWindowStart = today - 7L * weeks + 1
        val sets = completedSetsSince(startOfDayMillis(firstWindowStart))
            .map { it.convertedTo(settings.weightUnit) }
        return weeklyVolumeFrom(sets, weeks, today)
    }

    /** Best sets by estimated 1RM, one per exercise, best first; all-time. */
    suspend fun personalRecords(limit: Int = 5, exerciseName: String? = null): List<ExercisePr> {
        val settings = UserSettingsRepository.settings.first()
        val sets = completedSetsSince(0).map { it.convertedTo(settings.weightUnit) }
        return personalRecordsFrom(sets, limit, exerciseName, settings.oneRmFormula)
    }

    /** Weekly best estimated 1RM for one exercise over the last [weeks] weeks; null when it has no history. */
    suspend fun exerciseProgression(exerciseName: String, weeks: Int = 12): ExerciseProgression? {
        val settings = UserSettingsRepository.settings.first()
        val today = WeekMath.todayEpochDay()
        val firstWeekStart = WeekMath.weekStartOnOrBefore(today, settings.weekStart) - 7L * (weeks - 1)
        val sets = completedSetsSince(startOfDayMillis(firstWeekStart))
            .map { it.convertedTo(settings.weightUnit) }
        return progressionFrom(sets, exerciseName, today, settings.weekStart, settings.oneRmFormula)
    }

    suspend fun trainingFrequency(): TrainingFrequency {
        val settings = UserSettingsRepository.settings.first()
        val zone = TimeZone.currentSystemDefault()
        val sessionDays = AppDb.db.workoutSessionDao().getCompletedPerformedAt(PROFILE_ID)
            .map { millisToEpochDay(it, zone) }
        return frequencyFrom(sessionDays, WeekMath.todayEpochDay(), settings.weekStart)
    }

    /** All-time completed working-set count per exercise; used for the exercises list badge. */
    fun observeCompletedSetCountsByExercise(): Flow<List<ExerciseSetCount>> =
        AppDb.db.workoutSessionDetailDao()
            .observeCompletedSetCountsByExercise(PROFILE_ID)
            .map { rows -> rows.map { ExerciseSetCount(it.exerciseId, it.setCount) } }

    /** Snapshot of all-time completed working-set counts per exercise. */
    suspend fun completedSetCountsByExercise(): Map<Long, Int> =
        AppDb.db.workoutSessionDetailDao()
            .getCompletedSetCountsByExercise(PROFILE_ID)
            .associate { it.exerciseId to it.setCount }

    /**
     * Completed working sets per primary body part over the last [days] days,
     * most trained first. Sets whose exercise has no body part are omitted.
     */
    suspend fun bodyPartBreakdown(days: Int = 365): List<BodyPartSlice> {
        val startMillis = startOfDayMillis(WeekMath.todayEpochDay() - days.coerceAtLeast(1))
        return bodyPartBreakdownFrom(completedSetsSince(startMillis))
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
                sets = rows.map { it.toSetPoint(zone).convertedTo(settings.weightUnit) },
                sessionDays = performedAt.map { millisToEpochDay(it, zone) },
                bodyWeight = trendFrom(weightEntries.filter { it.epochDay >= today - 90 }, today, settings.weightUnit),
                todayEpochDay = today,
                weekStart = settings.weekStart,
                progressionConfigs = settings.progressionCards,
                formula = settings.oneRmFormula,
            )
        }
    }

    /**
     * Exercises eligible for a progression card (weighted sets in at least
     * two distinct weeks of the last 12), most 12-week volume first;
     * recomputed on any relevant DB change.
     */
    fun observeProgressionOptions(): Flow<List<String>> {
        val startMillis = startOfDayMillis(WeekMath.todayEpochDay() - 365)
        return combine(
            AppDb.db.workoutSessionDetailDao().observeCompletedSets(PROFILE_ID, startMillis),
            UserSettingsRepository.settings,
        ) { rows, settings ->
            val zone = TimeZone.currentSystemDefault()
            eligibleProgressionExercises(
                sets = rows.map { it.toSetPoint(zone).convertedTo(settings.weightUnit) },
                todayEpochDay = WeekMath.todayEpochDay(),
                weekStart = settings.weekStart,
            )
        }
    }

    /**
     * Completed working sets per primary body part over the last year, most
     * trained first; recomputed on any relevant DB change. Sets whose exercise
     * has no body part are omitted.
     */
    fun observeBodyPartBreakdown(): Flow<List<BodyPartSlice>> {
        val startMillis = startOfDayMillis(WeekMath.todayEpochDay() - 365)
        return AppDb.db.workoutSessionDetailDao()
            .observeCompletedSets(PROFILE_ID, startMillis)
            .map { rows ->
                val zone = TimeZone.currentSystemDefault()
                bodyPartBreakdownFrom(rows.map { it.toSetPoint(zone) })
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
        bodyPart = bodyPart,
        weight = actualWeight ?: 0.0,
        reps = actualReps ?: 0,
        duration = actualDuration ?: 0,
        distance = actualDistance ?: 0.0,
        weightUnit = weightUnit,
    )

@OptIn(ExperimentalTime::class)
internal fun millisToEpochDay(millis: Long, zone: TimeZone): Long =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date.toEpochDays()

@OptIn(ExperimentalTime::class)
internal fun startOfDayMillis(epochDay: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
    LocalDate.fromEpochDays(epochDay).atStartOfDayIn(zone).toEpochMilliseconds()

// Pure transforms below are internal (not private) so jvmTest can exercise
// them without a database, mirroring toUserSettings.

/**
 * Buckets [sets] into [weeks] trailing, non-overlapping 7-day windows, the
 * last one ending on [todayEpochDay] — e.g. today back 6 days — rather than a
 * calendar week. A calendar-week bucket would be partial for most of the
 * week (as little as a single day right after it rolls over) and read as a
 * huge, spurious drop when compared against complete prior weeks.
 */
internal fun weeklyVolumeFrom(
    sets: List<SetPoint>,
    weeks: Int,
    todayEpochDay: Long,
): List<WeeklyVolumePoint> {
    val firstWindowStart = todayEpochDay - 7L * weeks + 1
    val byDay = sets
        .filter { it.epochDay >= firstWindowStart }
        .groupBy { it.epochDay }
    return (0 until weeks).map { i ->
        val windowStart = firstWindowStart + 7L * i
        val windowSets = (windowStart..windowStart + 6).flatMap { byDay[it].orEmpty() }
        WeeklyVolumePoint(
            weekStartEpochDay = windowStart,
            totalVolume = windowSets.sumOf { it.weight * it.reps },
            setCount = windowSets.size,
            sessionCount = windowSets.distinctBy { it.sessionKey }.size,
        )
    }
}

internal fun bodyPartBreakdownFrom(sets: List<SetPoint>): List<BodyPartSlice> =
    sets
        .mapNotNull { it.bodyPart }
        .groupingBy { it }
        .eachCount()
        .map { (bodyPart, count) -> BodyPartSlice(bodyPart = bodyPart, setCount = count) }
        .sortedWith(compareByDescending<BodyPartSlice> { it.setCount }.thenBy { it.bodyPart })

internal fun personalRecordsFrom(
    sets: List<SetPoint>,
    limit: Int,
    exerciseName: String? = null,
    formula: OneRmFormula,
): List<ExercisePr> =
    sets
        .filter { it.weight > 0 && it.reps > 0 }
        .filter { exerciseName == null || it.exerciseName.equals(exerciseName, ignoreCase = true) }
        .groupBy { it.exerciseId }
        .map { (_, exerciseSets) ->
            val best = exerciseSets.maxBy { formula.estimate(it.weight, it.reps) }
            ExercisePr(
                exerciseId = best.exerciseId,
                exerciseName = best.exerciseName,
                weight = best.weight,
                reps = best.reps,
                epochDay = best.epochDay,
                est1Rm = formula.estimate(best.weight, best.reps),
            )
        }
        .sortedByDescending { it.est1Rm }
        .take(limit)

internal fun progressionFrom(
    sets: List<SetPoint>,
    exerciseName: String,
    todayEpochDay: Long,
    weekStart: WeekStart,
    formula: OneRmFormula,
): ExerciseProgression? {
    val relevant = sets.filter {
        it.exerciseName.equals(exerciseName, ignoreCase = true) && it.weight > 0 && it.reps > 0
    }
    if (relevant.isEmpty()) return null
    val weeklyBest = relevant
        .groupBy { WeekMath.weekStartOnOrBefore(it.epochDay, weekStart) }
        .map { (weekStartDay, weekSets) ->
            SparkPoint(weekStartDay, weekSets.maxOf { formula.estimate(it.weight, it.reps) })
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

/** Max estimated 1RM per calendar week for one exercise; 0.0 for weeks without eligible sets. */
internal fun singleExerciseWeeklyOneRm(
    sets: List<SetPoint>,
    weeks: Int,
    todayEpochDay: Long,
    weekStart: WeekStart,
    formula: OneRmFormula,
): List<SparkPoint> {
    val firstWeekStart = WeekMath.weekStartOnOrBefore(todayEpochDay, weekStart) - 7L * (weeks - 1)
    val eligible = sets.filter { it.weight > 0 && it.reps > 0 }
    val byWeek = eligible.groupBy { WeekMath.weekStartOnOrBefore(it.epochDay, weekStart) }
    return (0 until weeks).map { i ->
        val ws = firstWeekStart + 7L * i
        SparkPoint(ws, byWeek[ws]?.maxOfOrNull { formula.estimate(it.weight, it.reps) } ?: 0.0)
    }
}

/** Total volume (weight × reps) per calendar week for one exercise; 0.0 for empty weeks. */
internal fun singleExerciseWeeklyVolume(
    sets: List<SetPoint>,
    weeks: Int,
    todayEpochDay: Long,
    weekStart: WeekStart,
): List<SparkPoint> {
    val firstWeekStart = WeekMath.weekStartOnOrBefore(todayEpochDay, weekStart) - 7L * (weeks - 1)
    val eligible = sets.filter { it.weight > 0 && it.reps > 0 }
    val byWeek = eligible.groupBy { WeekMath.weekStartOnOrBefore(it.epochDay, weekStart) }
    return (0 until weeks).map { i ->
        val ws = firstWeekStart + 7L * i
        SparkPoint(ws, byWeek[ws]?.sumOf { it.weight * it.reps } ?: 0.0)
    }
}

/** Total duration in seconds per calendar week for one exercise; 0.0 for empty weeks. */
internal fun singleExerciseWeeklyDuration(
    sets: List<SetPoint>,
    weeks: Int,
    todayEpochDay: Long,
    weekStart: WeekStart,
): List<SparkPoint> {
    val firstWeekStart = WeekMath.weekStartOnOrBefore(todayEpochDay, weekStart) - 7L * (weeks - 1)
    val eligible = sets.filter { it.duration > 0 }
    val byWeek = eligible.groupBy { WeekMath.weekStartOnOrBefore(it.epochDay, weekStart) }
    return (0 until weeks).map { i ->
        val ws = firstWeekStart + 7L * i
        SparkPoint(ws, byWeek[ws]?.sumOf { it.duration.toDouble() } ?: 0.0)
    }
}

/** Total distance per calendar week for one exercise; 0.0 for empty weeks. */
internal fun singleExerciseWeeklyDistance(
    sets: List<SetPoint>,
    weeks: Int,
    todayEpochDay: Long,
    weekStart: WeekStart,
): List<SparkPoint> {
    val firstWeekStart = WeekMath.weekStartOnOrBefore(todayEpochDay, weekStart) - 7L * (weeks - 1)
    val eligible = sets.filter { it.distance > 0.0 }
    val byWeek = eligible.groupBy { WeekMath.weekStartOnOrBefore(it.epochDay, weekStart) }
    return (0 until weeks).map { i ->
        val ws = firstWeekStart + 7L * i
        SparkPoint(ws, byWeek[ws]?.sumOf { it.distance } ?: 0.0)
    }
}

/**
 * Completed sessions per week, oldest first, covering exactly [weeks] weeks up
 * to the current one; weeks without sessions carry a zero so the consistency
 * bars keep a fixed time axis.
 */
internal fun weeklySessionCountsFrom(
    sessionDays: List<Long>,
    weeks: Int,
    todayEpochDay: Long,
    weekStart: WeekStart,
): List<SparkPoint> {
    val firstWeekStart = WeekMath.weekStartOnOrBefore(todayEpochDay, weekStart) - 7L * (weeks - 1)
    val byWeek = sessionDays
        .filter { it >= firstWeekStart }
        .groupingBy { WeekMath.weekStartOnOrBefore(it, weekStart) }
        .eachCount()
    return (0 until weeks).map { i ->
        val weekStartDay = firstWeekStart + 7L * i
        SparkPoint(weekStartDay, (byWeek[weekStartDay] ?: 0).toDouble())
    }
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
    progressionConfigs: List<ProgressionCardConfig> = emptyList(),
    formula: OneRmFormula,
): List<InsightCard> {
    val cards = mutableListOf<InsightCard>()

    if (sessionDays.isNotEmpty()) {
        cards += volumeCard(sets, todayEpochDay)
        cards += frequencyCard(sessionDays, todayEpochDay, weekStart)
        val eligible = eligibleProgressionExercises(sets, todayEpochDay, weekStart)
        cards += effectiveProgressionConfigs(progressionConfigs, eligible)
            .mapIndexedNotNull { slot, config ->
                progressionCard(sets, config, slot, todayEpochDay, weekStart, formula)
            }
    }
    if (bodyWeight != null) cards += bodyWeightCard(bodyWeight)
    return cards
}

/**
 * Exercises eligible for a progression card: weighted sets in ≥2 distinct
 * weeks of the last 12; most 12-week volume first.
 */
internal fun eligibleProgressionExercises(
    sets: List<SetPoint>,
    todayEpochDay: Long,
    weekStart: WeekStart,
): List<String> =
    sets
        .filter { it.weight > 0 && it.reps > 0 && it.epochDay >= todayEpochDay - 7L * 12 }
        .groupBy { it.exerciseName }
        .filterValues { exerciseSets ->
            exerciseSets.distinctBy { WeekMath.weekStartOnOrBefore(it.epochDay, weekStart) }.size >= 2
        }
        .entries
        .sortedByDescending { (_, exerciseSets) -> exerciseSets.sumOf { it.weight * it.reps } }
        .map { it.key }

/**
 * The progression cards to show: the stored configs with ineligible
 * exercises dropped (canonicalizing case) and emptied cards removed, or —
 * when the user never configured any — a single card charting the
 * top-volume eligible exercise.
 */
internal fun effectiveProgressionConfigs(
    stored: List<ProgressionCardConfig>,
    eligible: List<String>,
): List<ProgressionCardConfig> {
    if (stored.isEmpty()) {
        return eligible.take(1).map { ProgressionCardConfig(listOf(it)) }
    }
    val byLowercase = eligible.associateBy { it.lowercase() }
    return stored
        .map { config -> config.copy(exercises = config.exercises.mapNotNull { byLowercase[it.lowercase()] }) }
        .filter { it.exercises.isNotEmpty() }
}

private fun volumeCard(
    sets: List<SetPoint>,
    todayEpochDay: Long,
): InsightCard {
    val weeklies = weeklyVolumeFrom(sets, weeks = 8, todayEpochDay)
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
        // Sessions per week for the consistency bars, unlike the other kinds'
        // line sparks; matches the 12-week window of avgPerWeek12w.
        spark = weeklySessionCountsFrom(sessionDays, weeks = 12, todayEpochDay, weekStart),
    )
}

/**
 * One card for a configured exercise selection. Exercises without two weeks
 * of history are dropped; null when none remain. Single-exercise cards keep
 * the progression/plateau kinds (and their narration); comparison cards are
 * "progression_multi", which has no headline, detail, or narration — the UI
 * renders one legend row per series.
 */
private fun progressionCard(
    sets: List<SetPoint>,
    config: ProgressionCardConfig,
    slot: Int,
    todayEpochDay: Long,
    weekStart: WeekStart,
    formula: OneRmFormula,
): InsightCard? {
    val window = todayEpochDay - 7L * 12
    val recent = sets.filter { it.weight > 0 && it.reps > 0 && it.epochDay >= window }
    val progressions = config.exercises
        .mapNotNull { progressionFrom(recent, it, todayEpochDay, weekStart, formula) }
        .filter { it.weeklyBest1Rm.size >= 2 }
    val series = progressions.map { p ->
        SparkSeries(
            name = p.exerciseName,
            latest = p.weeklyBest1Rm.last().value,
            slopePerWeek = p.slopePerWeek,
            trend = when {
                p.plateau -> "flat"
                p.slopePerWeek >= 0 -> "up"
                else -> "down"
            },
            points = p.weeklyBest1Rm,
        )
    }
    val id = "progression:slot$slot"
    val single = progressions.singleOrNull()
        ?: return if (series.isEmpty()) {
            null
        } else {
            InsightCard(
                id = id,
                kind = "progression_multi",
                exerciseName = null,
                headlineKey = "",
                headlineArgs = emptyList(),
                detailKey = "",
                detailArgs = emptyList(),
                trend = "none",
                spark = series.first().points,
                series = series,
                showPctChange = config.showPctChange,
            )
        }
    val latest = single.weeklyBest1Rm.last().value
    val weeksSpanned = single.weeklyBest1Rm.size
    return if (single.plateau) {
        InsightCard(
            id = id,
            kind = "plateau",
            exerciseName = single.exerciseName,
            headlineKey = InsightCard.HEADLINE_PLATEAU_AT,
            headlineArgs = listOf(InsightMath.formatNumber(latest)),
            detailKey = InsightCard.DETAIL_FLAT_ACROSS_WEEKS,
            detailArgs = listOf("$weeksSpanned"),
            trend = "flat",
            spark = single.weeklyBest1Rm,
            series = series,
            showPctChange = config.showPctChange,
        )
    } else {
        val sign = if (single.slopePerWeek >= 0) "+" else ""
        InsightCard(
            id = id,
            kind = "progression",
            exerciseName = single.exerciseName,
            headlineKey = InsightCard.HEADLINE_EST_1RM,
            headlineArgs = listOf(InsightMath.formatNumber(latest)),
            detailKey = InsightCard.DETAIL_SLOPE_PER_WEEK,
            detailArgs = listOf("$sign${InsightMath.formatNumber(single.slopePerWeek)}", "$weeksSpanned"),
            trend = if (single.slopePerWeek >= 0) "up" else "down",
            spark = single.weeklyBest1Rm,
            series = series,
            showPctChange = config.showPctChange,
        )
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
