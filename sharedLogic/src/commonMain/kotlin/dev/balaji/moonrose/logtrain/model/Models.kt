package dev.balaji.moonrose.logtrain.model

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Domain models exposed to the UI layer. These are plain data classes (no Room
 * types) so they remain usable from the js target, which has no database.
 */
data class Profile(
    val id: Long,
    val name: String,
    val age: Int?,
    val gender: String?,
    val weight: Double?,
    val height: Double?,
    /** true = weeks start on Monday in the calendar view, false = Sunday. */
    val weekStartsMonday: Boolean,
)

data class Program(
    val id: Long,
    val name: String,
)

data class Routine(
    val id: Long,
    val name: String,
    val programId: Long,
    val position: Int,
)

/**
 * How an exercise is measured when logging a set. Persisted as the lowercase
 * [value] string in exercises.exercise_type.
 */
enum class ExerciseType(val value: String) {
    /** Load and reps, e.g. Barbell Bench Press (8 reps at 60 kg). */
    STRENGTH("strength"),

    /** Timed / endurance, e.g. Plank (60 seconds). */
    DURATION("duration"),

    /** Distance only, e.g. Open Swim (1000 meters). */
    DISTANCE("distance"),

    /** Pacing / interval, e.g. Kettlebell Swings (20 reps in 45s). */
    REPS_DURATION("reps_duration"),

    /** Cardio / time-distance, e.g. Treadmill Run (5 km timed). */
    DISTANCE_DURATION("distance_duration"),

    /** Carry / travel, e.g. Walking Lunges (20 steps over 15m). */
    REPS_DISTANCE("reps_distance"),
    ;

    companion object {
        fun fromValue(value: String?): ExerciseType? = entries.firstOrNull { it.value == value }
    }
}

@OptIn(ExperimentalObjCName::class)
data class Exercise(
    val id: Long,
    val name: String,
    // "description" collides with NSObject's auto-generated `description` (toString())
    // on the Swift side; see ExerciseFormUiState for the same fix.
    @ObjCName("descriptionText")
    val description: String?,
    val exerciseType: String?,
    val bodyPart: String?,
    val equipment: String?,
    /** Seeded catalog entry: cannot be edited or deleted. */
    val builtin: Boolean,
)
