package dev.balaji.moonrose.logtrain.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal expect fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>

// SQL mirrors createSql in schemas/.../10.json exactly; Room validates the
// migrated schema against it and falls back destructively on any mismatch.
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `body_metrics` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`profile_id` INTEGER NOT NULL, `weight` REAL NOT NULL, " +
                "`logged_at` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_body_metrics_profile_id_logged_at` " +
                "ON `body_metrics` (`profile_id`, `logged_at`)",
        )
    }
}

internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `workout_sessions` ADD COLUMN `test_data` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            "ALTER TABLE `body_metrics` ADD COLUMN `test_data` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_insight_cache` (" +
                "`card_id` TEXT NOT NULL, `stats_hash` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, `generated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`card_id`))",
        )
    }
}

// Up to v12 the gender picker persisted its localized (English) display
// string; v13 stores the language-neutral GENDER_KEYS values instead, so a
// later app-language change can't orphan the stored value. Unrecognized
// values are kept as-is and rendered verbatim by the shells' fallbacks.
internal val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "UPDATE profile SET gender = CASE gender " +
                "WHEN 'Male' THEN 'male' " +
                "WHEN 'Female' THEN 'female' " +
                "WHEN 'Other' THEN 'other' " +
                "WHEN 'Prefer not to say' THEN 'prefer_not_to_say' " +
                "ELSE gender END",
        )
    }
}

fun createDatabase(): AppDatabase =
    databaseBuilder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(ExerciseSeedCallback)
        .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
        // Pre-release safety net for schema history older than v9.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

object AppDb {
    val db: AppDatabase by lazy { createDatabase() }
}

@OptIn(ExperimentalTime::class)
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
