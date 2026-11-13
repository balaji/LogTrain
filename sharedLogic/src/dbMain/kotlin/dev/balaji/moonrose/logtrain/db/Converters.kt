package dev.balaji.moonrose.logtrain.db

import androidx.room.TypeConverter
import dev.balaji.moonrose.logtrain.model.SetType
import dev.balaji.moonrose.logtrain.model.WeightUnit

// Room/SQLite has no native array type; store as a comma-joined string.
internal object Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.takeIf { it.isNotEmpty() }?.split(",")

    // Stored as its language-neutral key so the SQL in Daos and the migration
    // can match on it directly.
    @TypeConverter
    fun fromSetType(value: SetType): String = value.key

    @TypeConverter
    fun toSetType(value: String): SetType = SetType.fromKey(value)

    @TypeConverter
    fun fromWeightUnit(value: WeightUnit): String = value.value

    @TypeConverter
    fun toWeightUnit(value: String): WeightUnit = WeightUnit.fromValue(value)
}
