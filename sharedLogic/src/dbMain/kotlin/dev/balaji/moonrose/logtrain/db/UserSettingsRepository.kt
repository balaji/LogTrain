package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.insights.OneRmFormula
import dev.balaji.moonrose.logtrain.model.Appearance
import dev.balaji.moonrose.logtrain.model.ProgressionCardConfig
import dev.balaji.moonrose.logtrain.model.UserSettings
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal const val SETTING_WEEK_START = "week_start"
internal const val SETTING_APPEARANCE = "appearance"
internal const val SETTING_WEIGHT_UNIT = "weight_unit"
internal const val SETTING_NOTIFICATIONS_ENABLED = "notifications_enabled"
internal const val SETTING_ONE_RM_FORMULA = "one_rm_formula"
internal const val SETTING_PROGRESSION_CARDS = "progression_cards"
internal const val SETTING_BARBELL_BAR_WEIGHTS = "barbell_bar_weights"

internal const val MAX_PROGRESSION_CARDS = 3
internal const val MAX_PROGRESSION_EXERCISES = 3

/** Separators for the progression-cards value: untypeable, so safe in exercise names. */
private const val CARD_SEPARATOR = '\u001E'
private const val FIELD_SEPARATOR = '\u001F'
private const val AXIS_PCT = "pct"
private const val AXIS_ABS = "abs"

object UserSettingsRepository {
    val settings: Flow<UserSettings> =
        AppDb.db.userSettingDao().observeAll().map { it.toUserSettings() }

    suspend fun setWeekStart(weekStart: WeekStart) = set(SETTING_WEEK_START, weekStart.value)

    suspend fun setAppearance(appearance: Appearance) = set(SETTING_APPEARANCE, appearance.value)

    suspend fun setWeightUnit(weightUnit: WeightUnit) = set(SETTING_WEIGHT_UNIT, weightUnit.value)

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        set(SETTING_NOTIFICATIONS_ENABLED, enabled.toString())

    suspend fun setOneRmFormula(formula: OneRmFormula) = set(SETTING_ONE_RM_FORMULA, formula.value)

    suspend fun setProgressionCards(configs: List<ProgressionCardConfig>) =
        set(SETTING_PROGRESSION_CARDS, encodeProgressionCards(configs))

    /**
     * Last bar weight the user picked on the plate keyboard for [exerciseId],
     * or null if never chosen. Keyed by unit too, so a remembered 20 kg is
     * never read back as 20 lb.
     */
    suspend fun barWeightFor(exerciseId: Long, unit: WeightUnit): Double? =
        decodeBarWeights(AppDb.db.userSettingDao().find(SETTING_BARBELL_BAR_WEIGHTS)?.value)[
            BarWeightKey(exerciseId, unit),
        ]

    suspend fun setBarWeight(exerciseId: Long, unit: WeightUnit, weight: Double) {
        val current = decodeBarWeights(AppDb.db.userSettingDao().find(SETTING_BARBELL_BAR_WEIGHTS)?.value)
        val updated = current + (BarWeightKey(exerciseId, unit) to weight)
        set(SETTING_BARBELL_BAR_WEIGHTS, encodeBarWeights(updated))
    }

    private suspend fun set(key: String, value: String) {
        val now = nowMillis()
        val existing = AppDb.db.userSettingDao().find(key)
        AppDb.db.userSettingDao().upsert(
            UserSettingEntity(
                key = key,
                value = value,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }
}

// Internal (not private) so AppDatabaseTest can exercise the mapping.
internal fun List<UserSettingEntity>.toUserSettings(): UserSettings {
    val byKey = associate { it.key to it.value }
    return UserSettings(
        weekStart = WeekStart.fromValue(byKey[SETTING_WEEK_START]),
        appearance = Appearance.fromValue(byKey[SETTING_APPEARANCE]),
        weightUnit = WeightUnit.fromValue(byKey[SETTING_WEIGHT_UNIT]),
        oneRmFormula = OneRmFormula.fromValue(byKey[SETTING_ONE_RM_FORMULA]),
        notificationsEnabled = byKey[SETTING_NOTIFICATIONS_ENABLED].toBoolean(),
        progressionCards = decodeProgressionCards(byKey[SETTING_PROGRESSION_CARDS]),
    )
}

internal fun encodeProgressionCards(configs: List<ProgressionCardConfig>): String =
    configs
        .filter { it.exercises.isNotEmpty() }
        .take(MAX_PROGRESSION_CARDS)
        .joinToString(CARD_SEPARATOR.toString()) { config ->
            val axis = if (config.showPctChange) AXIS_PCT else AXIS_ABS
            (listOf(axis) + config.exercises.take(MAX_PROGRESSION_EXERCISES))
                .joinToString(FIELD_SEPARATOR.toString())
        }

internal data class BarWeightKey(val exerciseId: Long, val unit: WeightUnit)

internal fun encodeBarWeights(weights: Map<BarWeightKey, Double>): String =
    weights.entries.joinToString(CARD_SEPARATOR.toString()) { (key, weight) ->
        listOf(key.exerciseId.toString(), key.unit.value, weight.toString())
            .joinToString(FIELD_SEPARATOR.toString())
    }

/** Lenient decode: malformed records are dropped. */
internal fun decodeBarWeights(value: String?): Map<BarWeightKey, Double> {
    if (value.isNullOrEmpty()) return emptyMap()
    return value.split(CARD_SEPARATOR)
        .mapNotNull { encoded ->
            val fields = encoded.split(FIELD_SEPARATOR)
            if (fields.size != 3) return@mapNotNull null
            val exerciseId = fields[0].toLongOrNull() ?: return@mapNotNull null
            val unit = WeightUnit.entries.firstOrNull { it.value == fields[1] } ?: return@mapNotNull null
            val weight = fields[2].toDoubleOrNull() ?: return@mapNotNull null
            BarWeightKey(exerciseId, unit) to weight
        }
        .toMap()
}

/** Lenient decode: cards without exercises are dropped, caps enforced. */
internal fun decodeProgressionCards(value: String?): List<ProgressionCardConfig> {
    if (value.isNullOrEmpty()) return emptyList()
    return value.split(CARD_SEPARATOR)
        .mapNotNull { encoded ->
            val fields = encoded.split(FIELD_SEPARATOR)
            val exercises = fields.drop(1).filter { it.isNotBlank() }.take(MAX_PROGRESSION_EXERCISES)
            if (exercises.isEmpty()) return@mapNotNull null
            ProgressionCardConfig(exercises = exercises, showPctChange = fields.first() == AXIS_PCT)
        }
        .take(MAX_PROGRESSION_CARDS)
}
