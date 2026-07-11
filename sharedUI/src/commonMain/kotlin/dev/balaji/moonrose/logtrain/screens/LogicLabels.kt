package dev.balaji.moonrose.logtrain.screens

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.calendar.WeekMath
import dev.balaji.moonrose.logtrain.db.MAX_AGE
import dev.balaji.moonrose.logtrain.db.MAX_NAME_LENGTH
import dev.balaji.moonrose.logtrain.db.MIN_AGE
import dev.balaji.moonrose.logtrain.db.ProfileNameError
import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.OneRmFormula
import dev.balaji.moonrose.logtrain.model.SampleProgram
import dev.balaji.moonrose.logtrain.model.SampleRoutine
import dev.balaji.moonrose.logtrain.model.WeightUnit
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

/*
 * Localized rendering of the language-neutral keys, enums, and structured
 * data that sharedLogic produces. sharedLogic never emits display text (see
 * e.g. InsightCard, SampleProgram, ProfileNameError); this file is where the
 * Compose UI turns those keys into strings. The webApp and the iOS SwiftUI
 * shell keep their own equivalents of these mappings.
 */

@Composable
internal fun weightUnitLabel(unit: WeightUnit): String = stringResource(
    when (unit) {
        WeightUnit.METRIC -> Res.string.unit_kg
        WeightUnit.IMPERIAL -> Res.string.unit_lb
    },
)

@Composable
internal fun oneRmFormulaLabel(formula: OneRmFormula): String = stringResource(
    when (formula) {
        OneRmFormula.EPLEY -> Res.string.one_rm_formula_epley
        OneRmFormula.BRZYCKI -> Res.string.one_rm_formula_brzycki
        OneRmFormula.LOMBARDI -> Res.string.one_rm_formula_lombardi
        OneRmFormula.OCONNER -> Res.string.one_rm_formula_oconner
    },
)

@Composable
internal fun oneRmInfoText(formula: OneRmFormula): String = stringResource(
    when (formula) {
        OneRmFormula.EPLEY -> Res.string.one_rm_info_epley
        OneRmFormula.BRZYCKI -> Res.string.one_rm_info_brzycki
        OneRmFormula.LOMBARDI -> Res.string.one_rm_info_lombardi
        OneRmFormula.OCONNER -> Res.string.one_rm_info_oconner
    },
)

@Composable
internal fun monthAbbreviation(monthOfYear: Int): String = stringResource(
    when (monthOfYear) {
        1 -> Res.string.month_jan_short
        2 -> Res.string.month_feb_short
        3 -> Res.string.month_mar_short
        4 -> Res.string.month_apr_short
        5 -> Res.string.month_may_short
        6 -> Res.string.month_jun_short
        7 -> Res.string.month_jul_short
        8 -> Res.string.month_aug_short
        9 -> Res.string.month_sep_short
        10 -> Res.string.month_oct_short
        11 -> Res.string.month_nov_short
        else -> Res.string.month_dec_short
    },
)

/** e.g. "Jul 7, 2026". */
@Composable
internal fun formatDayTitle(epochDay: Long): String {
    val parts = WeekMath.dayParts(epochDay)
    return stringResource(
        Res.string.day_title,
        monthAbbreviation(parts.monthOfYear),
        parts.dayOfMonth.toString(),
        parts.year.toString(),
    )
}

@Composable
internal fun insightCardTitle(card: InsightCard): String = when (card.kind) {
    "volume" -> stringResource(Res.string.insight_title_volume)
    "frequency" -> stringResource(Res.string.insight_title_frequency)
    "body_weight" -> stringResource(Res.string.body_weight)
    // progression/plateau cards are titled by the exercise name, which is
    // user/catalog data shown verbatim.
    else -> card.exerciseName.orEmpty()
}

@Composable
internal fun insightHeadline(card: InsightCard, unitLabel: String): String {
    val args = card.headlineArgs
    return when (card.headlineKey) {
        InsightCard.HEADLINE_VOLUME_THIS_WEEK ->
            stringResource(Res.string.insight_volume_this_week, args[0], unitLabel)
        InsightCard.HEADLINE_STREAK_WEEKS -> stringResource(Res.string.insight_streak_weeks, args[0])
        InsightCard.HEADLINE_NO_STREAK -> stringResource(Res.string.insight_no_streak)
        InsightCard.HEADLINE_PLATEAU_AT -> stringResource(Res.string.insight_plateau_at, args[0], unitLabel)
        InsightCard.HEADLINE_EST_1RM -> stringResource(Res.string.insight_est_1rm, args[0], unitLabel)
        InsightCard.HEADLINE_LATEST_BODY_WEIGHT ->
            stringResource(Res.string.insight_latest_body_weight, args[0], unitLabel)
        // An unknown key means sharedLogic is newer than this UI; show the
        // bare values rather than nothing.
        else -> args.joinToString(" ")
    }
}

@Composable
internal fun insightDetail(card: InsightCard, unitLabel: String): String {
    val args = card.detailArgs
    return when (card.detailKey) {
        InsightCard.DETAIL_PCT_VS_4_WEEK_AVG -> stringResource(Res.string.insight_pct_vs_4_week_avg, args[0])
        InsightCard.DETAIL_SETS_IN_SESSIONS ->
            stringResource(Res.string.insight_sets_in_sessions, args[0], args[1])
        InsightCard.DETAIL_WORKOUTS_30_DAYS -> stringResource(Res.string.insight_workouts_30_days, args[0])
        InsightCard.DETAIL_WORKOUTS_30_DAYS_LONGEST ->
            stringResource(Res.string.insight_workouts_30_days_longest, args[0], args[1])
        InsightCard.DETAIL_FLAT_ACROSS_WEEKS -> stringResource(Res.string.insight_flat_across_weeks, args[0])
        InsightCard.DETAIL_SLOPE_PER_WEEK ->
            stringResource(Res.string.insight_slope_per_week, args[0], unitLabel, args[1])
        InsightCard.DETAIL_CHANGE_30_DAYS -> stringResource(Res.string.insight_change_30_days, args[0], unitLabel)
        InsightCard.DETAIL_LOG_REGULARLY -> stringResource(Res.string.insight_log_regularly)
        else -> args.joinToString(" ")
    }
}

@Composable
internal fun profileNameErrorText(error: ProfileNameError): String = when (error) {
    ProfileNameError.REQUIRED -> stringResource(Res.string.name_required)
    ProfileNameError.TOO_LONG -> stringResource(Res.string.profile_name_too_long, MAX_NAME_LENGTH.toString())
    ProfileNameError.INVALID_CHARS -> stringResource(Res.string.profile_name_letters_only)
}

@Composable
internal fun profileAgeErrorText(): String =
    stringResource(Res.string.profile_age_range, MIN_AGE.toString(), MAX_AGE.toString())

// The sample mappings fall back to the raw id so a sample added to
// sharedLogic without a matching resource is visibly unlocalized, not
// silently mislabeled.

@Composable
internal fun sampleProgramName(sample: SampleProgram): String = when (sample.id) {
    "full-body-3day" -> stringResource(Res.string.sample_full_body_3day_name)
    "upper-lower-4day" -> stringResource(Res.string.sample_upper_lower_4day_name)
    "push-pull-legs-5day" -> stringResource(Res.string.sample_push_pull_legs_5day_name)
    else -> sample.id
}

@Composable
internal fun sampleProgramSummary(sample: SampleProgram): String = when (sample.id) {
    "full-body-3day" -> stringResource(Res.string.sample_full_body_3day_summary)
    "upper-lower-4day" -> stringResource(Res.string.sample_upper_lower_4day_summary)
    "push-pull-legs-5day" -> stringResource(Res.string.sample_push_pull_legs_5day_summary)
    else -> sample.id
}

@Composable
internal fun sampleRoutineName(routine: SampleRoutine): String = when (routine.id) {
    "full-body-a" -> stringResource(Res.string.sample_routine_full_body_a)
    "full-body-b" -> stringResource(Res.string.sample_routine_full_body_b)
    "full-body-c" -> stringResource(Res.string.sample_routine_full_body_c)
    "upper-a" -> stringResource(Res.string.sample_routine_upper_a)
    "lower-a" -> stringResource(Res.string.sample_routine_lower_a)
    "upper-b" -> stringResource(Res.string.sample_routine_upper_b)
    "lower-b" -> stringResource(Res.string.sample_routine_lower_b)
    "push" -> stringResource(Res.string.sample_routine_push)
    "pull" -> stringResource(Res.string.sample_routine_pull)
    "legs" -> stringResource(Res.string.sample_routine_legs)
    "upper" -> stringResource(Res.string.sample_routine_upper)
    "lower" -> stringResource(Res.string.sample_routine_lower)
    else -> routine.id
}

/**
 * Display label for a lowercase exercise-catalog key (bodyPart, equipment, or
 * exerciseType value stored in the DB). Unknown keys — e.g. from a newer
 * catalog — fall back to the old capitalize-the-key rendering.
 */
@Composable
internal fun exerciseCatalogLabel(key: String): String = when (key) {
    "back" -> stringResource(Res.string.body_part_back)
    "biceps" -> stringResource(Res.string.body_part_biceps)
    "calves" -> stringResource(Res.string.body_part_calves)
    "chest" -> stringResource(Res.string.body_part_chest)
    "core" -> stringResource(Res.string.body_part_core)
    "forearms" -> stringResource(Res.string.body_part_forearms)
    "full body" -> stringResource(Res.string.body_part_full_body)
    "glutes" -> stringResource(Res.string.body_part_glutes)
    "hamstrings" -> stringResource(Res.string.body_part_hamstrings)
    "shoulders" -> stringResource(Res.string.body_part_shoulders)
    "thighs" -> stringResource(Res.string.body_part_thighs)
    "triceps" -> stringResource(Res.string.body_part_triceps)
    "machine" -> stringResource(Res.string.equipment_machine)
    "body weight" -> stringResource(Res.string.equipment_body_weight)
    "cable" -> stringResource(Res.string.equipment_cable)
    "barbell" -> stringResource(Res.string.equipment_barbell)
    "smith machine" -> stringResource(Res.string.equipment_smith_machine)
    "dumbbell" -> stringResource(Res.string.equipment_dumbbell)
    "kettlebell" -> stringResource(Res.string.equipment_kettlebell)
    "other" -> stringResource(Res.string.equipment_other)
    "band" -> stringResource(Res.string.equipment_band)
    "strength" -> stringResource(Res.string.exercise_type_strength)
    "duration" -> stringResource(Res.string.exercise_type_duration)
    "distance" -> stringResource(Res.string.exercise_type_distance)
    "reps_duration" -> stringResource(Res.string.exercise_type_reps_duration)
    "distance_duration" -> stringResource(Res.string.exercise_type_distance_duration)
    "reps_distance" -> stringResource(Res.string.exercise_type_reps_distance)
    else -> key.replaceFirstChar(Char::uppercase)
}
