package dev.balaji.moonrose.logtrain.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Local schema mirroring the moonrose backend (see ~/projects/moonrose/db/schema.rb),
 * with the users table replaced by a single-row profile table. Table and column
 * names keep moonrose's snake_case so a future sync feature can map rows 1:1.
 * Timestamps are epoch milliseconds; Rails decimal(6,2) columns map to Double.
 */

@Entity(tableName = "profile")
data class ProfileEntity(
    // Single-row table by convention: the one local user always has id = 1.
    @PrimaryKey val id: Long = 1,
    val name: String,
    val age: Int?,
    val gender: String?,
    val weight: Double?,
    val height: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "exercise_type") val exerciseType: String?,
    @ColumnInfo(name = "body_part") val bodyPart: String?,
    val equipment: String?,
    @ColumnInfo(name = "target_muscle_group") val targetMuscleGroup: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "plans",
    foreignKeys = [
        ForeignKey(ProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profile_id")],
)
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(PlanEntity::class, ["id"], ["plan_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("plan_id"),
        Index(value = ["plan_id", "position"], unique = true),
    ],
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: Long,
    val name: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "work_sets",
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routine_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [
        Index("routine_id"),
        Index("exercise_id"),
        Index(value = ["routine_id", "position"], unique = true),
    ],
)
data class WorkSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "work_set_details",
    foreignKeys = [
        ForeignKey(WorkSetEntity::class, ["id"], ["work_set_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("work_set_id"),
        Index(value = ["work_set_id", "position"], unique = true),
    ],
)
data class WorkSetDetailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "work_set_id") val workSetId: Long,
    val position: Int,
    @ColumnInfo(name = "target_weight") val targetWeight: Double?,
    @ColumnInfo(name = "target_reps") val targetReps: Int?,
    // Seconds, matching moonrose.
    @ColumnInfo(name = "target_duration") val targetDuration: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(ProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE),
        // Deleting a routine keeps the historical session, matching moonrose's nullify.
        ForeignKey(RoutineEntity::class, ["id"], ["routine_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("routine_id"),
        Index(value = ["profile_id", "performed_at"]),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "routine_id") val routineId: Long?,
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "workout_session_details",
    foreignKeys = [
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["workout_session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(WorkSetDetailEntity::class, ["id"], ["work_set_detail_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [
        Index("work_set_detail_id"),
        Index("exercise_id"),
        Index(value = ["workout_session_id", "position"], unique = true),
    ],
)
data class WorkoutSessionDetailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_session_id") val workoutSessionId: Long,
    @ColumnInfo(name = "work_set_detail_id") val workSetDetailId: Long?,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "actual_weight") val actualWeight: Double?,
    @ColumnInfo(name = "actual_reps") val actualReps: Int?,
    @ColumnInfo(name = "actual_duration") val actualDuration: Int?,
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
