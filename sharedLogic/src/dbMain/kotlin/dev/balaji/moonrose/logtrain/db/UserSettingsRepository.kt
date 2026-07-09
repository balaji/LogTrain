package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.model.Appearance
import dev.balaji.moonrose.logtrain.model.UserSettings
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal const val SETTING_WEEK_START = "week_start"
internal const val SETTING_APPEARANCE = "appearance"
internal const val SETTING_WEIGHT_UNIT = "weight_unit"

object UserSettingsRepository {
    val settings: Flow<UserSettings> =
        AppDb.db.userSettingDao().observeAll().map { it.toUserSettings() }

    suspend fun setWeekStart(weekStart: WeekStart) = set(SETTING_WEEK_START, weekStart.value)

    suspend fun setAppearance(appearance: Appearance) = set(SETTING_APPEARANCE, appearance.value)

    suspend fun setWeightUnit(weightUnit: WeightUnit) = set(SETTING_WEIGHT_UNIT, weightUnit.value)

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
    )
}
