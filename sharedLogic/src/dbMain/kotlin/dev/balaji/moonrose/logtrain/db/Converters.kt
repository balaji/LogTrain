package dev.balaji.moonrose.logtrain.db

import androidx.room.TypeConverter

// Room/SQLite has no native array type; store as a comma-joined string.
internal object Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.joinToString(",")

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.takeIf { it.isNotEmpty() }?.split(",")
}
