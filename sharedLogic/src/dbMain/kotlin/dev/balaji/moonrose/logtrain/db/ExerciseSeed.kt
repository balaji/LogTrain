package dev.balaji.moonrose.logtrain.db

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Seeds the exercise catalog when the database is first created. There is no
 * exercise-creation UI yet, and workout logging is unusable with an empty catalog.
 */
internal object ExerciseSeedCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        val now = nowMillis()
        SEED_EXERCISES.forEach { (name, type, bodyPart, equipment, muscle) ->
            connection.execSQL(
                "INSERT INTO exercises " +
                    "(name, exercise_type, body_part, equipment, target_muscle_group, created_at, updated_at) " +
                    "VALUES ('$name', '$type', '$bodyPart', '$equipment', '$muscle', $now, $now)",
            )
        }
    }
}

private data class Seed(
    val name: String,
    val type: String,
    val bodyPart: String,
    val equipment: String,
    val muscle: String,
)

private val SEED_EXERCISES = listOf(
    Seed("Bench Press", "strength", "chest", "barbell", "pectorals"),
    Seed("Incline Dumbbell Press", "strength", "chest", "dumbbell", "upper pectorals"),
    Seed("Squat", "strength", "legs", "barbell", "quadriceps"),
    Seed("Deadlift", "strength", "back", "barbell", "erector spinae"),
    Seed("Romanian Deadlift", "strength", "legs", "barbell", "hamstrings"),
    Seed("Overhead Press", "strength", "shoulders", "barbell", "deltoids"),
    Seed("Barbell Row", "strength", "back", "barbell", "latissimus dorsi"),
    Seed("Pull-up", "strength", "back", "bodyweight", "latissimus dorsi"),
    Seed("Dip", "strength", "chest", "bodyweight", "triceps"),
    Seed("Lunge", "strength", "legs", "bodyweight", "quadriceps"),
    Seed("Leg Press", "strength", "legs", "machine", "quadriceps"),
    Seed("Bicep Curl", "strength", "arms", "dumbbell", "biceps"),
    Seed("Tricep Pushdown", "strength", "arms", "cable", "triceps"),
    Seed("Lat Pulldown", "strength", "back", "cable", "latissimus dorsi"),
    Seed("Plank", "duration", "core", "bodyweight", "abdominals"),
)
