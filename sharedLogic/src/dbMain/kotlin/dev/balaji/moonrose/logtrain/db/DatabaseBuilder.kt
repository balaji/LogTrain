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

// The boolean `warmup` becomes the `set_type` enum, so the column has to go
// rather than just gain a sibling — hence the full table rebuild (SQLite can
// drop a column, but Room compares the table against the schema it expects and
// a leftover `warmup` would fail validation). SQL mirrors createSql in
// schemas/.../14.json exactly. work_set_details is rebuilt first because
// workout_session_details carries a foreign key into it.
internal val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_work_set_details` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`work_set_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`target_weight` REAL, `target_reps` INTEGER, `target_duration` INTEGER, " +
                "`set_type` TEXT NOT NULL DEFAULT 'normal', " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`work_set_id`) REFERENCES `work_sets`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "INSERT INTO `_new_work_set_details` (" +
                "`id`, `work_set_id`, `position`, `target_weight`, `target_reps`, " +
                "`target_duration`, `set_type`, `created_at`, `updated_at`) " +
                "SELECT `id`, `work_set_id`, `position`, `target_weight`, `target_reps`, " +
                "`target_duration`, CASE WHEN `warmup` != 0 THEN 'warmup' ELSE 'normal' END, " +
                "`created_at`, `updated_at` FROM `work_set_details`",
        )
        connection.execSQL("DROP TABLE `work_set_details`")
        connection.execSQL("ALTER TABLE `_new_work_set_details` RENAME TO `work_set_details`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_work_set_details_work_set_id` " +
                "ON `work_set_details` (`work_set_id`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_set_details_work_set_id_position` " +
                "ON `work_set_details` (`work_set_id`, `position`)",
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_workout_session_details` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`workout_session_id` INTEGER NOT NULL, `work_set_detail_id` INTEGER, " +
                "`exercise_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`target_weight` REAL, `target_reps` INTEGER, `target_duration` INTEGER, " +
                "`actual_weight` REAL, `actual_reps` INTEGER, `actual_duration` INTEGER, " +
                "`set_type` TEXT NOT NULL DEFAULT 'normal', " +
                "`completed` INTEGER NOT NULL DEFAULT 0, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`workout_session_id`) REFERENCES `workout_sessions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`work_set_detail_id`) REFERENCES `work_set_details`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        connection.execSQL(
            "INSERT INTO `_new_workout_session_details` (" +
                "`id`, `workout_session_id`, `work_set_detail_id`, `exercise_id`, `position`, " +
                "`target_weight`, `target_reps`, `target_duration`, `actual_weight`, " +
                "`actual_reps`, `actual_duration`, `set_type`, `completed`, " +
                "`created_at`, `updated_at`) " +
                "SELECT `id`, `workout_session_id`, `work_set_detail_id`, `exercise_id`, `position`, " +
                "`target_weight`, `target_reps`, `target_duration`, `actual_weight`, " +
                "`actual_reps`, `actual_duration`, " +
                "CASE WHEN `warmup` != 0 THEN 'warmup' ELSE 'normal' END, `completed`, " +
                "`created_at`, `updated_at` FROM `workout_session_details`",
        )
        connection.execSQL("DROP TABLE `workout_session_details`")
        connection.execSQL(
            "ALTER TABLE `_new_workout_session_details` RENAME TO `workout_session_details`",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_session_details_work_set_detail_id` " +
                "ON `workout_session_details` (`work_set_detail_id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_workout_session_details_exercise_id` " +
                "ON `workout_session_details` (`exercise_id`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_workout_session_details_workout_session_id_position` " +
                "ON `workout_session_details` (`workout_session_id`, `position`)",
        )
    }
}

// Additive only, so no table rebuild: nullable distance columns for the
// distance-measured exercise types. SQL mirrors createSql in
// schemas/.../15.json exactly.
internal val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `work_set_details` ADD COLUMN `target_distance` REAL",
        )
        connection.execSQL(
            "ALTER TABLE `workout_session_details` ADD COLUMN `target_distance` REAL",
        )
        connection.execSQL(
            "ALTER TABLE `workout_session_details` ADD COLUMN `actual_distance` REAL",
        )
    }
}

// Additive only: records the WeightUnit ("metric"/"imperial") each set target,
// logged set, and body-weight entry was entered in, backfilling existing rows
// from the user's current setting (or 'metric' when unset).
internal val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `work_set_details` ADD COLUMN `weight_unit` TEXT NOT NULL DEFAULT 'metric'",
        )
        connection.execSQL(
            "ALTER TABLE `workout_session_details` ADD COLUMN `weight_unit` TEXT NOT NULL DEFAULT 'metric'",
        )
        connection.execSQL(
            "ALTER TABLE `body_metrics` ADD COLUMN `weight_unit` TEXT NOT NULL DEFAULT 'metric'",
        )
        val backfill =
            "CASE (SELECT `value` FROM `user_settings` WHERE `key` = 'weight_unit') " +
                "WHEN 'imperial' THEN 'imperial' ELSE 'metric' END"
        connection.execSQL("UPDATE `work_set_details` SET `weight_unit` = $backfill")
        connection.execSQL("UPDATE `workout_session_details` SET `weight_unit` = $backfill")
        connection.execSQL("UPDATE `body_metrics` SET `weight_unit` = $backfill")
    }
}

fun createDatabase(): AppDatabase =
    databaseBuilder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(ExerciseSeedCallback)
        .addMigrations(
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
        )
        // Pre-release safety net for schema history older than v9.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

object AppDb {
    val db: AppDatabase by lazy { createDatabase() }
}

@OptIn(ExperimentalTime::class)
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
