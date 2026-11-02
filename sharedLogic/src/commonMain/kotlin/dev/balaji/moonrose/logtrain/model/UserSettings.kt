package dev.balaji.moonrose.logtrain.model

import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.insights.BmiClassification
import dev.balaji.moonrose.logtrain.insights.BmiFormula
import dev.balaji.moonrose.logtrain.insights.OneRmFormula

/**
 * App color scheme. Persisted as the lowercase [value] string in
 * user_settings.
 */
enum class Appearance(val value: String) {
    LIGHT("light"),
    DARK("dark"),

    /** Follow the OS appearance. */
    SYSTEM("system"),
    ;

    companion object {
        fun fromValue(value: String?): Appearance = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

/**
 * Weight display units. Persisted as the lowercase [value] string in
 * user_settings. Logged set weights are unit-agnostic raw numbers that this
 * setting only relabels; the conversions below exist for the BMI calculator,
 * which needs true kilograms regardless of the display unit.
 */
enum class WeightUnit(val value: String) {
    METRIC("metric"),
    IMPERIAL("imperial"),
    ;

    /** A weight the user typed in this unit, expressed in kilograms. */
    fun toKilograms(value: Double): Double = if (this == IMPERIAL) value * KG_PER_LB else value

    /** A weight in kilograms, expressed in this unit for display. */
    fun fromKilograms(kg: Double): Double = if (this == IMPERIAL) kg / KG_PER_LB else kg

    companion object {
        private const val KG_PER_LB = 0.45359237

        fun fromValue(value: String?): WeightUnit = entries.firstOrNull { it.value == value } ?: METRIC
    }
}

/**
 * One user-configured progression card on the Progress tab: which exercises
 * its chart overlays and how its y-axis scales. Persisted in user_settings.
 */
data class ProgressionCardConfig(
    /** 1–3 exercise names, chart order. */
    val exercises: List<String>,
    /** Chart y-axis mode: % change from each series' first week vs raw weight. */
    val showPctChange: Boolean = false,
)

/**
 * Typed view of the user_settings key-value table. Absent or unrecognized
 * rows yield these defaults, so no rows are ever seeded.
 */
data class UserSettings(
    val weekStart: WeekStart = WeekStart.SUNDAY,
    val appearance: Appearance = Appearance.SYSTEM,
    val weightUnit: WeightUnit = WeightUnit.METRIC,
    /** Formula used everywhere an estimated 1RM is shown or ranked. */
    val oneRmFormula: OneRmFormula = OneRmFormula.EPLEY,
    /** Formula used everywhere a BMI is computed or shown. */
    val bmiFormula: BmiFormula = BmiFormula.STANDARD,
    /** Cutoffs used everywhere a BMI is categorized or banded. */
    val bmiClassification: BmiClassification = BmiClassification.WHO,
    /**
     * Whether the user opted in to notifications. Off by default; enabling
     * it triggers the OS permission prompt. Controls whether a background
     * push notification is sent when the between-set rest timer finishes.
     */
    val notificationsEnabled: Boolean = false,
    /**
     * Progression cards the user configured on the Progress tab, display
     * order. Empty means "never configured" — the Progress tab then defaults
     * to one card charting the top-volume exercise.
     */
    val progressionCards: List<ProgressionCardConfig> = emptyList(),
)
