package dev.balaji.moonrose.logtrain.insights

/*
 * Insight models are primitive-typed (Long/Int/Double/String) like the
 * calendar models: they cross the SKIE bridge to SwiftUI, where boxed
 * collection elements would surface as KotlinDouble. Weights are the raw
 * stored numbers; the user's unit preference is rendered as a label by each
 * UI layer (the logic layer never produces display text for it).
 */

/** One point of a sparkline series. */
data class SparkPoint(val epochDay: Long, val value: Double)

/**
 * One named line of a progression chart, with the stats each UI renders in
 * the legend. [points] are weekly best estimated 1RMs, oldest first.
 */
data class SparkSeries(
    /** Exercise name — user data, shown verbatim. */
    val name: String,
    /** Latest weekly best estimated 1RM. */
    val latest: Double,
    val slopePerWeek: Double,
    /** "up" | "down" | "flat". */
    val trend: String,
    val points: List<SparkPoint>,
)

/**
 * A self-contained card for the Progress screen. Numbers are pre-formatted
 * in Kotlin so Compose, SwiftUI, and the webApp render identical values, but
 * every sentence is a language-neutral key that each UI maps to its own
 * localized template (Compose string resources, the iOS shell's English
 * literals, the webApp's i18n). Templates also interpolate the user's weight
 * unit where noted; the unit label itself is localized by the UI.
 */
data class InsightCard(
    /** Stable identity: "weekly_volume", "frequency", "progression:slot<index>", "body_weight". */
    val id: String,
    /** "volume" | "frequency" | "progression" | "plateau" | "progression_multi" | "body_weight". */
    val kind: String,
    /**
     * Card title for progression/plateau cards (an exercise name — user data,
     * shown verbatim); null for the other kinds, which the UI titles from [kind].
     */
    val exerciseName: String?,
    /** One of the HEADLINE_* keys. */
    val headlineKey: String,
    /** Pre-formatted template arguments, in order. */
    val headlineArgs: List<String>,
    /** One of the DETAIL_* keys. */
    val detailKey: String,
    val detailArgs: List<String>,
    /** "up" | "down" | "flat" | "none". */
    val trend: String,
    /** Sparkline series, oldest first; empty when the card has no chart. */
    val spark: List<SparkPoint>,
    /**
     * Named lines for progression cards (1–3 entries; [spark] duplicates the
     * first one); empty for the other kinds. "progression_multi" cards have
     * no headline/detail — the UI renders one legend row per series instead.
     */
    val series: List<SparkSeries> = emptyList(),
    /** Chart y-axis mode: % change from each series' first week vs raw weight. */
    val showPctChange: Boolean = false,
) {
    companion object {
        /** args: [volume] + unit — "1,234 kg this week". */
        const val HEADLINE_VOLUME_THIS_WEEK = "volume_this_week"

        /** args: [weeks] — "2-week streak". */
        const val HEADLINE_STREAK_WEEKS = "streak_weeks"

        /** no args — "No active streak". */
        const val HEADLINE_NO_STREAK = "no_streak"

        /** args: [est1Rm] + unit — "Plateau at ~100 kg". */
        const val HEADLINE_PLATEAU_AT = "plateau_at"

        /** args: [est1Rm] + unit — "Est. 1RM 102.5 kg". */
        const val HEADLINE_EST_1RM = "est_1rm"

        /** args: [weight] + unit — "78.5 kg". */
        const val HEADLINE_LATEST_BODY_WEIGHT = "latest_body_weight"

        /** args: [signedPct] — "+12% vs 4-week average". */
        const val DETAIL_PCT_VS_4_WEEK_AVG = "pct_vs_4_week_avg"

        /** args: [sets, sessions] — "12 sets in 3 sessions". */
        const val DETAIL_SETS_IN_SESSIONS = "sets_in_sessions"

        /** args: [workouts] — "9 workouts in the last 30 days". */
        const val DETAIL_WORKOUTS_30_DAYS = "workouts_30_days"

        /** args: [workouts, longestWeeks] — "... · longest streak 5 weeks". */
        const val DETAIL_WORKOUTS_30_DAYS_LONGEST = "workouts_30_days_longest"

        /** args: [weeks] — "Est. 1RM flat across 4 weeks". */
        const val DETAIL_FLAT_ACROSS_WEEKS = "flat_across_weeks"

        /** args: [signedSlope, weeks] + unit — "+1.3 kg/week over 6 weeks". */
        const val DETAIL_SLOPE_PER_WEEK = "slope_per_week"

        /** args: [signedChange] + unit — "-1.5 kg in 30 days". */
        const val DETAIL_CHANGE_30_DAYS = "change_30_days"

        /** no args — "Log regularly to see your trend". */
        const val DETAIL_LOG_REGULARLY = "log_regularly"
    }
}

/**
 * Completed working sets attributed to one primary body part. [bodyPart] is
 * the lowercase catalog key stored on the exercise ("chest", "full body", …);
 * each UI maps it to a localized label.
 */
data class BodyPartSlice(
    val bodyPart: String,
    val setCount: Int,
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
