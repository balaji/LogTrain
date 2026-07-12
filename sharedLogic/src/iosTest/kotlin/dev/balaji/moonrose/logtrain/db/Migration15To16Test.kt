package dev.balaji.moonrose.logtrain.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory

/**
 * v16 adds nullable rest-timer columns: rest_seconds on routines and
 * workout_sessions, rest_timer_ends_at on workout_sessions. This opens a real
 * v15 database — built from the committed schemas/15.json, so the fixture
 * can't drift from what actually shipped — and migrates it with destructive
 * fallback OFF, which is the point: with the fallback on, a broken migration
 * silently drops the user's history and every assertion below would still
 * pass on empty tables.
 */
class Migration15To16Test {
    private val dbPath = NSTemporaryDirectory() + "migration_15_16_test.db"

    @BeforeTest
    fun setUp() = deleteDb()

    @AfterTest
    fun tearDown() = deleteDb()

    @OptIn(ExperimentalForeignApi::class)
    private fun deleteDb() {
        listOf(dbPath, "$dbPath-wal", "$dbPath-shm").forEach {
            NSFileManager.defaultManager.removeItemAtPath(it, null)
        }
    }

    @Test
    fun addsRestTimerColumnsAndKeepsHistory() {
        seedV15Database()

        val db = Room.databaseBuilder<AppDatabase>(name = dbPath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .addMigrations(
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
            )
            // Deliberately NO fallbackToDestructiveMigration: Room validates the
            // migrated schema against 16.json and throws on any mismatch.
            .build()

        runBlocking {
            // Pre-migration rows survive intact and read null for the new
            // columns, i.e. "inherit from the next level up".
            val routine = assertNotNull(db.routineDao().findById(1L))
            assertEquals("Day A", routine.name)
            assertEquals(null, routine.restSeconds)

            val session = assertNotNull(db.workoutSessionDao().findById(1L))
            assertEquals(100L, session.performedAt)
            assertEquals(null, session.restSeconds)
            assertEquals(null, session.restTimerEndsAt)

            // Non-null values round-trip through the new columns.
            db.routineDao().setRestSeconds(1L, 120, now = 2L)
            assertEquals(120, db.routineDao().findById(1L)?.restSeconds)

            db.workoutSessionDao().setRestSeconds(1L, 0, now = 2L)
            db.workoutSessionDao().setRestTimerEndsAt(1L, 1_234_567L, now = 2L)
            val updated = assertNotNull(db.workoutSessionDao().findById(1L))
            assertEquals(0, updated.restSeconds)
            assertEquals(1_234_567L, updated.restTimerEndsAt)
        }
        db.close()
    }

    /** DDL copied verbatim from schemas/.../15.json, plus fixture rows. */
    private fun seedV15Database() {
        val connection = BundledSQLiteDriver().open(dbPath)
        V15_SCHEMA.forEach { connection.execSQL(it) }
        V15_FIXTURES.forEach { connection.execSQL(it) }
        connection.close()
    }
}

private val V15_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `age` INTEGER, `gender` TEXT, `weight` REAL, `height` REAL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `exercise_type` TEXT, `body_part` TEXT, `other_body_parts` TEXT, `equipment` TEXT, `builtin` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` ON `exercises` (`name`)",
    "CREATE TABLE IF NOT EXISTS `programs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profile_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_programs_profile_id` ON `programs` (`profile_id`)",
    "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `program_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`program_id`) REFERENCES `programs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_routines_program_id` ON `routines` (`program_id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_routines_program_id_position` ON `routines` (`program_id`, `position`)",
    "CREATE TABLE IF NOT EXISTS `work_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routine_id` INTEGER NOT NULL, `exercise_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_work_sets_routine_id` ON `work_sets` (`routine_id`)",
    "CREATE INDEX IF NOT EXISTS `index_work_sets_exercise_id` ON `work_sets` (`exercise_id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_sets_routine_id_position` ON `work_sets` (`routine_id`, `position`)",
    "CREATE TABLE IF NOT EXISTS `work_set_details` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `work_set_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `target_weight` REAL, `target_reps` INTEGER, `target_duration` INTEGER, `target_distance` REAL, `set_type` TEXT NOT NULL DEFAULT 'normal', `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`work_set_id`) REFERENCES `work_sets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_work_set_details_work_set_id` ON `work_set_details` (`work_set_id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_work_set_details_work_set_id_position` ON `work_set_details` (`work_set_id`, `position`)",
    "CREATE TABLE IF NOT EXISTS `workout_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profile_id` INTEGER NOT NULL, `routine_id` INTEGER, `performed_at` INTEGER NOT NULL, `completed_at` INTEGER, `test_data` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`routine_id`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_workout_sessions_routine_id` ON `workout_sessions` (`routine_id`)",
    "CREATE INDEX IF NOT EXISTS `index_workout_sessions_profile_id_performed_at` ON `workout_sessions` (`profile_id`, `performed_at`)",
    "CREATE TABLE IF NOT EXISTS `workout_session_details` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workout_session_id` INTEGER NOT NULL, `work_set_detail_id` INTEGER, `exercise_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `target_weight` REAL, `target_reps` INTEGER, `target_duration` INTEGER, `target_distance` REAL, `actual_weight` REAL, `actual_reps` INTEGER, `actual_duration` INTEGER, `actual_distance` REAL, `set_type` TEXT NOT NULL DEFAULT 'normal', `completed` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`workout_session_id`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`work_set_detail_id`) REFERENCES `work_set_details`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_workout_session_details_work_set_detail_id` ON `workout_session_details` (`work_set_detail_id`)",
    "CREATE INDEX IF NOT EXISTS `index_workout_session_details_exercise_id` ON `workout_session_details` (`exercise_id`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_session_details_workout_session_id_position` ON `workout_session_details` (`workout_session_id`, `position`)",
    "CREATE TABLE IF NOT EXISTS `user_settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`key`))",
    "CREATE TABLE IF NOT EXISTS `body_metrics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profile_id` INTEGER NOT NULL, `weight` REAL NOT NULL, `logged_at` INTEGER NOT NULL, `test_data` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`profile_id`) REFERENCES `profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_body_metrics_profile_id_logged_at` ON `body_metrics` (`profile_id`, `logged_at`)",
    "CREATE TABLE IF NOT EXISTS `ai_insight_cache` (`card_id` TEXT NOT NULL, `stats_hash` TEXT NOT NULL, `text` TEXT NOT NULL, `generated_at` INTEGER NOT NULL, PRIMARY KEY(`card_id`))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, 'fc074393dc2d014021e8560eb5bc7ebb')",
    "PRAGMA user_version = 15",
)

// One routine and an in-progress session referencing it — the rows the new
// rest-timer columns land on.
private val V15_FIXTURES = listOf(
    "INSERT INTO profile (id, name, created_at, updated_at) VALUES (1, 'Tester', 1, 1)",
    "INSERT INTO exercises (id, name, created_at, updated_at) VALUES (1, 'Barbell Back Squat', 1, 1)",
    "INSERT INTO programs (id, profile_id, name, created_at, updated_at) VALUES (1, 1, 'Program', 1, 1)",
    "INSERT INTO routines (id, program_id, name, position, created_at, updated_at) VALUES (1, 1, 'Day A', 1, 1, 1)",
    "INSERT INTO work_sets (id, routine_id, exercise_id, position, created_at, updated_at) VALUES (1, 1, 1, 1, 1, 1)",
    "INSERT INTO work_set_details (id, work_set_id, position, target_weight, target_reps, created_at, updated_at) VALUES (1, 1, 1, 100.0, 5, 1, 1)",
    "INSERT INTO workout_sessions (id, profile_id, routine_id, performed_at, completed_at, created_at, updated_at) VALUES (1, 1, 1, 100, NULL, 1, 1)",
    "INSERT INTO workout_session_details (id, workout_session_id, work_set_detail_id, exercise_id, position, actual_weight, actual_reps, completed, created_at, updated_at) VALUES (1, 1, 1, 1, 1, 100.0, 5, 1, 1, 1)",
)
