package dev.balaji.moonrose.logtrain.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal expect fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>

fun createDatabase(): AppDatabase =
    databaseBuilder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(ExerciseSeedCallback)
        // Pre-release: no shipped schema history to migrate yet.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

object AppDb {
    val db: AppDatabase by lazy { createDatabase() }
}

@OptIn(ExperimentalTime::class)
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
