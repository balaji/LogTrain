package dev.balaji.moonrose.logtrain.db

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
    ],
    // v6: seed exercises reclassified into the new exercise types.
    // v7: warmup flag on work_set_details and workout_session_details.
    version = 7,
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
}

// Room's KSP processor generates the per-platform actuals.
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
