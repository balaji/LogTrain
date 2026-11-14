package dev.balaji.moonrose.trainfree.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

@Database(
    entities = [
        ProfileEntity::class,
        ExerciseEntity::class,
        ProgramEntity::class,
        RoutineEntity::class,
        WorkSetEntity::class,
        WorkSetDetailEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSessionDetailEntity::class,
        UserSettingEntity::class,
        BodyMetricEntity::class,
        AiInsightCacheEntity::class,
    ],
    // v6: seed exercises reclassified into the new exercise types.
    // v7: warmup flag on work_set_details and workout_session_details.
    // v8: builtin flag on exercises so seed exercises are read-only.
    // v9: user_settings KV table; week_starts_monday moved off profile.
    // v10: body_metrics weight time-series for Progress insights.
    // v11: test_data flag on workout_sessions and body_metrics for generated demo data.
    // v12: ai_insight_cache table for LLM-narrated insight card text.
    // v13: profile.gender rewritten from English display text to language-neutral keys.
    // v14: warmup flag replaced by the set_type enum ('normal'/'warmup'/'drop').
    // v15: distance columns on work_set_details/workout_session_details for
    //      distance-measured exercise types.
    // v16: weight_unit column on work_set_details, workout_session_details,
    //      and body_metrics so recorded weights/distances retain their unit.
    version = 16,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun programDao(): ProgramDao
    abstract fun routineDao(): RoutineDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workSetDao(): WorkSetDao
    abstract fun workSetDetailDao(): WorkSetDetailDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSessionDetailDao(): WorkoutSessionDetailDao
    abstract fun userSettingDao(): UserSettingDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun aiInsightCacheDao(): AiInsightCacheDao
}

// Room's KSP processor generates the per-platform actuals.
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
