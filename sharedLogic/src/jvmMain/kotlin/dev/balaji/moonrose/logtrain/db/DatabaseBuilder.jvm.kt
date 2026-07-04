package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

internal actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dir = File(System.getProperty("user.home"), ".logtrain").apply { mkdirs() }
    return Room.databaseBuilder<AppDatabase>(name = File(dir, "logtrain.db").absolutePath)
}
