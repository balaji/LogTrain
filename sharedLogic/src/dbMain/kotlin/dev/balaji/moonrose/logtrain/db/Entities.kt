package dev.balaji.moonrose.logtrain.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.balaji.moonrose.logtrain.model.SetType
import dev.balaji.moonrose.logtrain.model.WeightUnit

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

/**
 * Key-value app settings ("week_start", "appearance", "weight_unit"). Values
 * are language-neutral keys (e.g. "monday", "dark", "metric") — never display
 * strings — so UI labels can be regionalized later. Missing rows fall back to
 * defaults in UserSettingsRepository; nothing is seeded.
 */
@Entity(tableName = "user_settings")
data class UserSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
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
    val description: String?,
    @ColumnInfo(name = "exercise_type") val exerciseType: String?,
    @ColumnInfo(name = "body_part") val bodyPart: String?,
    @ColumnInfo(name = "other_body_parts") val otherBodyParts: List<String>?,
    val equipment: String?,
    // Seeded catalog entries, which the sample programs reference by name. Read-only
    // so those references can never be broken by a rename.
    @ColumnInfo(defaultValue = "0") val builtin: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "programs",
    foreignKeys = [
        ForeignKey(ProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profile_id")],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(ProgramEntity::class, ["id"], ["program_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("program_id"),
        Index(value = ["program_id", "position"], unique = true),
    ],
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "program_id") val programId: Long,
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
    // Kilometers or miles as entered in [weightUnit].
    @ColumnInfo(name = "target_distance") val targetDistance: Double?,
    // Warm-up sets are part of the routine but don't count as working sets.
    // Drop sets do count as work, but like warm-ups don't take a set number.
    @ColumnInfo(name = "set_type", defaultValue = "'normal'") val setType: SetType = SetType.NORMAL,
    // Unit in which targetWeight/targetDistance were entered.
    @ColumnInfo(name = "weight_unit", defaultValue = "'metric'") val weightUnit: WeightUnit = WeightUnit.METRIC,
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
    // Null while the session is in progress; set when the user taps Finish.
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    // Rows created by the developer test-data generator, so they can be
    // deleted in one statement without touching real history.
    @ColumnInfo(name = "test_data", defaultValue = "0") val testData: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "body_metrics",
    foreignKeys = [
        ForeignKey(ProfileEntity::class, ["id"], ["profile_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["profile_id", "logged_at"])],
)
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    // Raw number in [weightUnit] as logged by the user.
    val weight: Double,
    @ColumnInfo(name = "weight_unit", defaultValue = "'metric'") val weightUnit: WeightUnit = WeightUnit.METRIC,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
    // See WorkoutSessionEntity.testData.
    @ColumnInfo(name = "test_data", defaultValue = "0") val testData: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Cached LLM narration for one insight card. card_id is the card's logical
 * id ("weekly_volume", "progression:<exerciseId>", ...), not a row reference,
 * so there is no foreign key; stale rows are harmless and get overwritten.
 * stats_hash fingerprints the prompt (and thus the underlying numbers) the
 * text was generated from — a mismatch means the narration must regenerate.
 */
@Entity(tableName = "ai_insight_cache")
data class AiInsightCacheEntity(
    @PrimaryKey @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "stats_hash") val statsHash: String,
    val text: String,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
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
    // Snapshotted from the source WorkSetDetail at session start so the session
    // stays intact even if the routine is later edited or deleted. Null for
    // exercises added ad-hoc during the session and for empty workouts.
    @ColumnInfo(name = "target_weight") val targetWeight: Double?,
    @ColumnInfo(name = "target_reps") val targetReps: Int?,
    @ColumnInfo(name = "target_duration") val targetDuration: Int?,
    @ColumnInfo(name = "target_distance") val targetDistance: Double?,
    @ColumnInfo(name = "actual_weight") val actualWeight: Double?,
    @ColumnInfo(name = "actual_reps") val actualReps: Int?,
    @ColumnInfo(name = "actual_duration") val actualDuration: Int?,
    @ColumnInfo(name = "actual_distance") val actualDistance: Double?,
    // Snapshotted from the source WorkSetDetail like the targets above, and
    // changeable on the fly for sets added during the session.
    @ColumnInfo(name = "set_type", defaultValue = "'normal'") val setType: SetType = SetType.NORMAL,
    // Unit in which the row's weights and distances are stored.
    @ColumnInfo(name = "weight_unit", defaultValue = "'metric'") val weightUnit: WeightUnit = WeightUnit.METRIC,
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
