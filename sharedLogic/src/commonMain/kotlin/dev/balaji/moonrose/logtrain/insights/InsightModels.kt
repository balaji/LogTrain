package dev.balaji.moonrose.logtrain.insights

import dev.balaji.moonrose.logtrain.model.WeightUnit

/*
 * Insight models are primitive-typed (Long/Int/Double/String) like the
 * calendar models: they cross the SKIE bridge to SwiftUI, where boxed
 * collection elements would surface as KotlinDouble. Weights are the raw
 * stored numbers with the user's unit preference applied as a label only,
 * matching the rest of the app.
 */

/** Display label for the stored (unconverted) weight values. */
val WeightUnit.label: String
    get() = when (this) {
        WeightUnit.METRIC -> "kg"
        WeightUnit.IMPERIAL -> "lb"
    }

/** One point of a sparkline series. */
data class SparkPoint(val epochDay: Long, val value: Double)

/**
 * A self-contained card for the Progress screen. All strings are
 * pre-formatted in Kotlin (unit labels included) so Compose, SwiftUI, and
 * the chat tools render identical numbers.
 */
data class InsightCard(
    /** Stable identity: "weekly_volume", "frequency", "progression:<exerciseId>", "body_weight". */
    val id: String,
    /** "volume" | "frequency" | "progression" | "plateau" | "body_weight". */
    val kind: String,
    val title: String,
    val headline: String,
    val detail: String,
    /** "up" | "down" | "flat" | "none". */
    val trend: String,
    /** Sparkline series, oldest first; empty when the card has no chart. */
    val spark: List<SparkPoint>,
)

data class WeeklyVolumePoint(
    val weekStartEpochDay: Long,
    /** Sum of weight x reps over completed working sets. */
    val totalVolume: Double,
    val setCount: Int,
    val sessionCount: Int,
)

data class ExercisePr(
    val exerciseId: Long,
    val exerciseName: String,
    val weight: Double,
    val reps: Int,
    val epochDay: Long,
    val est1Rm: Double,
)

data class ExerciseProgression(
    val exerciseId: Long,
    val exerciseName: String,
    /** Best estimated 1RM per week, oldest first; weeks without sets are omitted. */
    val weeklyBest1Rm: List<SparkPoint>,
    val slopePerWeek: Double,
    val plateau: Boolean,
)

data class TrainingFrequency(
    val currentStreakWeeks: Int,
    val longestStreakWeeks: Int,
    val sessionsLast7Days: Int,
    val sessionsLast30Days: Int,
    val avgPerWeek12w: Double,
)

data class BodyWeightTrend(
    /** One point per logged entry, oldest first. */
    val points: List<SparkPoint>,
    val latest: Double,
    val latestEpochDay: Long,
    /** Latest minus the value ~30 days ago; 0.0 when there is no older entry. */
    val change30Days: Double,
    val slopePerWeek: Double,
)

data class StreakResult(val currentWeeks: Int, val longestWeeks: Int)
