package dev.balaji.moonrose.logtrain.model

import dev.balaji.moonrose.logtrain.calendar.WeekStart

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
 * user_settings. Stored preference only for now; display conversion is not
 * wired up yet.
 */
enum class WeightUnit(val value: String) {
    METRIC("metric"),
    IMPERIAL("imperial"),
    ;

    companion object {
        fun fromValue(value: String?): WeightUnit = entries.firstOrNull { it.value == value } ?: METRIC
    }
}

/**
 * Typed view of the user_settings key-value table. Absent or unrecognized
 * rows yield these defaults, so no rows are ever seeded.
 */
data class UserSettings(
    val weekStart: WeekStart = WeekStart.SUNDAY,
    val appearance: Appearance = Appearance.SYSTEM,
    val weightUnit: WeightUnit = WeightUnit.METRIC,
)
