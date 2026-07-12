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

/** One measured quantity of a logged set. Weight is deliberately absent: it is shown for every type but never required. */
enum class SetField { REPS, DURATION, DISTANCE }

/**
 * The measured-value columns a set row shows for this exercise type, in
 * display order (weight always renders first and is not listed). Both the
 * Compose and SwiftUI set rows lay out from this list.
 */
fun ExerciseType.displaySetFields(): List<SetField> = when (this) {
    ExerciseType.STRENGTH -> listOf(SetField.REPS)
    ExerciseType.DURATION -> listOf(SetField.DURATION)
    ExerciseType.DISTANCE -> listOf(SetField.DISTANCE)
    ExerciseType.REPS_DURATION -> listOf(SetField.REPS, SetField.DURATION)
    ExerciseType.REPS_DISTANCE -> listOf(SetField.REPS, SetField.DISTANCE)
    ExerciseType.DISTANCE_DURATION -> listOf(SetField.DURATION, SetField.DISTANCE)
}

/** The fields a set of this exercise type must have to count as performed. */
fun ExerciseType.requiredSetFields(): Set<SetField> = displaySetFields().toSet()

/**
 * Whether a set holds every value its type requires: all of
 * [requiredSetFields] present and greater than zero. Gates both marking a
 * set completed during a session and which sets survive finishing one.
 */
fun ExerciseType.isSetComplete(reps: Int?, durationSeconds: Int?, distance: Double?): Boolean =
    requiredSetFields().all { field ->
        when (field) {
            SetField.REPS -> (reps ?: 0) > 0
            SetField.DURATION -> (durationSeconds ?: 0) > 0
            SetField.DISTANCE -> (distance ?: 0.0) > 0.0
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
