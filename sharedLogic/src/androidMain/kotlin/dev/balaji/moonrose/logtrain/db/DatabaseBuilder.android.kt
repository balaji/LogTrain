package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.room.RoomDatabase

internal actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val context = DbContextHolder.appContext
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = context.getDatabasePath("logtrain.db").absolutePath,
    )
}
