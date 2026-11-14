package dev.balaji.moonrose.trainfree.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import dev.balaji.moonrose.trainfree.model.WeightUnit
import kotlinx.coroutines.flow.Flow

/** Projection for the workout-history list: one row per completed session. */
data class CompletedSessionRow(
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    /** Null when the session had no routine or the routine was deleted. */
    @ColumnInfo(name = "routine_name") val routineName: String?,
)

/** Projection for the session screen's "Previous" column: one logged set. */
data class PreviousSetRow(
    @ColumnInfo(name = "actual_weight") val actualWeight: Double?,
    @ColumnInfo(name = "actual_reps") val actualReps: Int?,
    @ColumnInfo(name = "actual_duration") val actualDuration: Int?,
    @ColumnInfo(name = "actual_distance") val actualDistance: Double?,
    @ColumnInfo(name = "weight_unit") val weightUnit: WeightUnit,
)

/** Projection for insights: one row per completed working set in a completed session. */
data class CompletedSetRow(
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    /** Primary body part of the exercise; null for exercises without one. */
    @ColumnInfo(name = "body_part") val bodyPart: String?,
    @ColumnInfo(name = "actual_weight") val actualWeight: Double?,
    @ColumnInfo(name = "actual_reps") val actualReps: Int?,
    @ColumnInfo(name = "actual_duration") val actualDuration: Int?,
    @ColumnInfo(name = "actual_distance") val actualDistance: Double?,
    @ColumnInfo(name = "weight_unit") val weightUnit: WeightUnit,
)

/** Projection for the exercises list badge: completed set count per exercise. */
data class ExerciseSetCountRow(
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "set_count") val setCount: Int,
)

/** Projection for chat workout-history recall: one row per completed session. */
data class RecentCompletedSessionRow(
    val id: Long,
    @ColumnInfo(name = "routine_id") val routineId: Long?,
    @ColumnInfo(name = "routine_name") val routineName: String?,
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
)

/** Projection for routine recency: latest completed timestamp per routine. */
data class RoutineLastPerformedRow(
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "last_performed_at") val lastPerformedAt: Long,
)

/** Projection for chat session summaries: one completed working set joined with exercise metadata. */
data class SessionDetailWithExerciseRow(
    @ColumnInfo(name = "workout_session_id") val workoutSessionId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    @ColumnInfo(name = "exercise_type") val exerciseType: String?,
    @ColumnInfo(name = "actual_weight") val actualWeight: Double?,
    @ColumnInfo(name = "actual_reps") val actualReps: Int?,
    @ColumnInfo(name = "actual_duration") val actualDuration: Int?,
    @ColumnInfo(name = "actual_distance") val actualDistance: Double?,
    @ColumnInfo(name = "set_type") val setType: dev.balaji.moonrose.trainfree.model.SetType,
    @ColumnInfo(name = "weight_unit") val weightUnit: WeightUnit,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    /** No-op when no profile row exists yet. */
    @Query("UPDATE profile SET weight = :weight, updated_at = :now WHERE id = 1")
    suspend fun updateWeight(weight: Double, now: Long)
}

@Dao
interface UserSettingDao {
    @Query("SELECT * FROM user_settings")
    fun observeAll(): Flow<List<UserSettingEntity>>

    @Query("SELECT * FROM user_settings WHERE `key` = :key")
    suspend fun find(key: String): UserSettingEntity?

    @Upsert
    suspend fun upsert(setting: UserSettingEntity)
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY id")
    fun observeAll(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs ORDER BY id")
    suspend fun getAll(): List<ProgramEntity>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun findById(id: Long): ProgramEntity?

    @Insert
    suspend fun insert(program: ProgramEntity): Long

    @Query("UPDATE programs SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY program_id, position")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY program_id, position")
    suspend fun getAll(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun findById(id: Long): RoutineEntity?

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM routines WHERE program_id = :programId")
    suspend fun nextPosition(programId: Long): Int

    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    @Query("UPDATE routines SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun findById(id: Long): ExerciseEntity?

    /**
     * Exact match, not COLLATE NOCASE: the unique index on name is case-sensitive,
     * so a case-insensitive lookup could match a user exercise that shadows a seed one.
     */
    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    @Query(
        "UPDATE exercises SET name = :name, description = :description, " +
            "exercise_type = :exerciseType, body_part = :bodyPart, equipment = :equipment, " +
            "updated_at = :now WHERE id = :id",
    )
    suspend fun update(
        id: Long,
        name: String,
        description: String?,
        exerciseType: String?,
        bodyPart: String?,
        equipment: String?,
        now: Long,
    )

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WorkSetDao {
    @Query("SELECT * FROM work_sets WHERE routine_id = :routineId ORDER BY position")
    fun observeForRoutine(routineId: Long): Flow<List<WorkSetEntity>>

    @Query("SELECT * FROM work_sets WHERE routine_id = :routineId ORDER BY position")
    suspend fun getForRoutine(routineId: Long): List<WorkSetEntity>

    @Query("DELETE FROM work_sets WHERE routine_id = :routineId")
    suspend fun deleteForRoutine(routineId: Long)

    @Insert
    suspend fun insert(workSet: WorkSetEntity): Long
}

@Dao
interface WorkSetDetailDao {
    @Query("SELECT * FROM work_set_details WHERE work_set_id = :workSetId ORDER BY position")
    fun observeForWorkSet(workSetId: Long): Flow<List<WorkSetDetailEntity>>

    @Query("SELECT * FROM work_set_details WHERE work_set_id = :workSetId ORDER BY position")
    suspend fun getForWorkSet(workSetId: Long): List<WorkSetDetailEntity>

    @Query("SELECT * FROM work_set_details WHERE work_set_id IN (:workSetIds) ORDER BY work_set_id, position")
    suspend fun getForWorkSets(workSetIds: List<Long>): List<WorkSetDetailEntity>

    @Insert
    suspend fun insert(detail: WorkSetDetailEntity): Long
}

@Dao
interface BodyMetricDao {
    @Query("SELECT * FROM body_metrics WHERE profile_id = :profileId ORDER BY logged_at DESC")
    fun observeAll(profileId: Long): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics WHERE profile_id = :profileId ORDER BY logged_at DESC LIMIT 1")
    suspend fun latest(profileId: Long): BodyMetricEntity?

    @Query(
        "SELECT * FROM body_metrics WHERE profile_id = :profileId " +
            "AND logged_at >= :startMillis ORDER BY logged_at",
    )
    suspend fun getInRange(profileId: Long, startMillis: Long): List<BodyMetricEntity>

    @Insert
    suspend fun insert(metric: BodyMetricEntity): Long

    @Query("DELETE FROM body_metrics WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM body_metrics WHERE test_data != 0")
    suspend fun deleteTestData()
}

@Dao
interface AiInsightCacheDao {
    @Query("SELECT * FROM ai_insight_cache WHERE card_id = :cardId")
    suspend fun find(cardId: String): AiInsightCacheEntity?

    @Upsert
    suspend fun upsert(entry: AiInsightCacheEntity)
}

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY performed_at DESC")
    fun observeAll(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun findById(id: Long): WorkoutSessionEntity?

    @Query(
        "SELECT * FROM workout_sessions " +
            "WHERE profile_id = :profileId AND completed_at IS NULL " +
            "ORDER BY performed_at DESC LIMIT 1",
    )
    fun observeInProgress(profileId: Long): Flow<WorkoutSessionEntity?>

    @Query(
        "SELECT * FROM workout_sessions " +
            "WHERE profile_id = :profileId AND completed_at IS NULL " +
            "ORDER BY performed_at DESC LIMIT 1",
    )
    suspend fun findInProgress(profileId: Long): WorkoutSessionEntity?

    @Query(
        "SELECT performed_at FROM workout_sessions " +
            "WHERE profile_id = :profileId AND completed_at IS NOT NULL",
    )
    fun observeCompletedPerformedAt(profileId: Long): Flow<List<Long>>

    @Query(
        "SELECT performed_at FROM workout_sessions " +
            "WHERE profile_id = :profileId AND completed_at IS NOT NULL",
    )
    suspend fun getCompletedPerformedAt(profileId: Long): List<Long>

    @Query(
        "SELECT s.performed_at AS performed_at, r.name AS routine_name " +
            "FROM workout_sessions s LEFT JOIN routines r ON r.id = s.routine_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "ORDER BY s.performed_at DESC",
    )
    fun observeCompletedWithRoutineName(profileId: Long): Flow<List<CompletedSessionRow>>

    @Query(
        "SELECT * FROM workout_sessions " +
            "WHERE profile_id = :profileId AND completed_at IS NOT NULL " +
            "AND performed_at >= :startMillis AND performed_at < :endMillis " +
            "ORDER BY performed_at",
    )
    suspend fun getCompletedInRange(profileId: Long, startMillis: Long, endMillis: Long): List<WorkoutSessionEntity>

    @Query(
        "SELECT * FROM workout_sessions " +
            "WHERE profile_id = :profileId AND completed_at IS NOT NULL " +
            "AND performed_at >= :startMillis AND performed_at < :endMillis " +
            "ORDER BY performed_at",
    )
    fun observeCompletedInRange(profileId: Long, startMillis: Long, endMillis: Long): Flow<List<WorkoutSessionEntity>>

    @Query(
        "SELECT s.id AS id, s.routine_id AS routine_id, r.name AS routine_name, " +
            "s.performed_at AS performed_at, s.completed_at AS completed_at " +
            "FROM workout_sessions s LEFT JOIN routines r ON r.id = s.routine_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "AND s.performed_at >= :sinceMillis " +
            "ORDER BY s.performed_at DESC LIMIT :limit",
    )
    suspend fun getRecentCompletedWithRoutine(
        profileId: Long,
        sinceMillis: Long,
        limit: Int,
    ): List<RecentCompletedSessionRow>

    @Query(
        "SELECT s.id AS id, s.routine_id AS routine_id, r.name AS routine_name, " +
            "s.performed_at AS performed_at, s.completed_at AS completed_at " +
            "FROM workout_sessions s LEFT JOIN routines r ON r.id = s.routine_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "AND s.performed_at >= :startMillis AND s.performed_at < :endMillis " +
            "ORDER BY s.performed_at",
    )
    suspend fun getCompletedWithRoutineInRange(
        profileId: Long,
        startMillis: Long,
        endMillis: Long,
    ): List<RecentCompletedSessionRow>

    @Query(
        "SELECT routine_id AS routine_id, MAX(performed_at) AS last_performed_at " +
            "FROM workout_sessions " +
            "WHERE profile_id = :profileId AND completed_at IS NOT NULL AND routine_id IS NOT NULL " +
            "GROUP BY routine_id",
    )
    suspend fun getLastPerformedByRoutine(profileId: Long): List<RoutineLastPerformedRow>

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Query("UPDATE workout_sessions SET completed_at = :completedAt, updated_at = :now WHERE id = :id")
    suspend fun setCompletedAt(id: Long, completedAt: Long, now: Long)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE test_data != 0")
    fun observeTestDataCount(): Flow<Int>

    /** Cascades to `workout_session_details` via the FK. */
    @Query("DELETE FROM workout_sessions WHERE test_data != 0")
    suspend fun deleteTestData()
}

@Dao
interface WorkoutSessionDetailDao {
    @Query("SELECT * FROM workout_session_details WHERE workout_session_id = :sessionId ORDER BY position")
    fun observeForSession(sessionId: Long): Flow<List<WorkoutSessionDetailEntity>>

    @Query("SELECT * FROM workout_session_details WHERE workout_session_id = :sessionId ORDER BY position")
    suspend fun getForSession(sessionId: Long): List<WorkoutSessionDetailEntity>

    @Query("DELETE FROM workout_session_details WHERE workout_session_id = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM workout_session_details WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM workout_session_details " +
            "WHERE workout_session_id = :sessionId AND completed != 0)",
    )
    suspend fun hasCompletedSets(sessionId: Long): Boolean

    /**
     * The source for every volume, PR and est-1RM number. Warm-ups are excluded
     * here rather than downstream — CompletedSetRow deliberately carries no set
     * type, so nothing can forget to filter. Drop sets are work at a reduced
     * load and stay in.
     */
    @Query(
        "SELECT s.performed_at AS performed_at, d.exercise_id AS exercise_id, " +
            "e.name AS exercise_name, e.body_part AS body_part, " +
            "d.actual_weight AS actual_weight, d.actual_reps AS actual_reps, " +
            "d.actual_duration AS actual_duration, d.actual_distance AS actual_distance, " +
            "d.weight_unit AS weight_unit " +
            "FROM workout_session_details d " +
            "JOIN workout_sessions s ON s.id = d.workout_session_id " +
            "JOIN exercises e ON e.id = d.exercise_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "AND d.completed != 0 AND d.set_type != 'warmup' AND s.performed_at >= :startMillis " +
            "ORDER BY s.performed_at",
    )
    fun observeCompletedSets(profileId: Long, startMillis: Long): Flow<List<CompletedSetRow>>

    @Query(
        "SELECT s.performed_at AS performed_at, d.exercise_id AS exercise_id, " +
            "e.name AS exercise_name, e.body_part AS body_part, " +
            "d.actual_weight AS actual_weight, d.actual_reps AS actual_reps, " +
            "d.actual_duration AS actual_duration, d.actual_distance AS actual_distance, " +
            "d.weight_unit AS weight_unit " +
            "FROM workout_session_details d " +
            "JOIN workout_sessions s ON s.id = d.workout_session_id " +
            "JOIN exercises e ON e.id = d.exercise_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "AND d.completed != 0 AND d.set_type != 'warmup' AND s.performed_at >= :startMillis " +
            "ORDER BY s.performed_at",
    )
    suspend fun getCompletedSets(profileId: Long, startMillis: Long): List<CompletedSetRow>

    @Query(
        "SELECT s.performed_at AS performed_at, d.exercise_id AS exercise_id, " +
            "e.name AS exercise_name, e.body_part AS body_part, " +
            "d.actual_weight AS actual_weight, d.actual_reps AS actual_reps, " +
            "d.actual_duration AS actual_duration, d.actual_distance AS actual_distance, " +
            "d.weight_unit AS weight_unit " +
            "FROM workout_session_details d " +
            "JOIN workout_sessions s ON s.id = d.workout_session_id " +
            "JOIN exercises e ON e.id = d.exercise_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "AND d.completed != 0 AND d.set_type != 'warmup' " +
            "AND d.exercise_id = :exerciseId AND s.performed_at >= :startMillis " +
            "ORDER BY s.performed_at",
    )
    fun observeCompletedSetsForExercise(
        profileId: Long,
        exerciseId: Long,
        startMillis: Long,
    ): Flow<List<CompletedSetRow>>

    @Query(
        "SELECT s.performed_at AS performed_at, d.exercise_id AS exercise_id, " +
            "e.name AS exercise_name, e.body_part AS body_part, " +
            "d.actual_weight AS actual_weight, d.actual_reps AS actual_reps, " +
            "d.actual_duration AS actual_duration, d.actual_distance AS actual_distance, " +
            "d.weight_unit AS weight_unit " +
            "FROM workout_session_details d " +
            "JOIN workout_sessions s ON s.id = d.workout_session_id " +
            "JOIN exercises e ON e.id = d.exercise_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "AND d.completed != 0 AND d.set_type != 'warmup' " +
            "AND d.exercise_id = :exerciseId AND s.performed_at >= :startMillis " +
            "ORDER BY s.performed_at",
    )
    suspend fun getCompletedSetsForExercise(
        profileId: Long,
        exerciseId: Long,
        startMillis: Long,
    ): List<CompletedSetRow>

    @Query(
        "SELECT d.exercise_id AS exercise_id, COUNT(*) AS set_count " +
            "FROM workout_session_details d " +
            "JOIN workout_sessions s ON s.id = d.workout_session_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "AND d.completed != 0 AND d.set_type != 'warmup' " +
            "GROUP BY d.exercise_id",
    )
    fun observeCompletedSetCountsByExercise(profileId: Long): Flow<List<ExerciseSetCountRow>>

    @Query(
        "SELECT d.exercise_id AS exercise_id, COUNT(*) AS set_count " +
            "FROM workout_session_details d " +
            "JOIN workout_sessions s ON s.id = d.workout_session_id " +
            "WHERE s.profile_id = :profileId AND s.completed_at IS NOT NULL " +
            "AND d.completed != 0 AND d.set_type != 'warmup' " +
            "GROUP BY d.exercise_id",
    )
    suspend fun getCompletedSetCountsByExercise(profileId: Long): List<ExerciseSetCountRow>

    @Query(
        "SELECT d.workout_session_id AS workout_session_id, d.exercise_id AS exercise_id, " +
            "e.name AS exercise_name, e.exercise_type AS exercise_type, " +
            "d.actual_weight AS actual_weight, d.actual_reps AS actual_reps, " +
            "d.actual_duration AS actual_duration, d.actual_distance AS actual_distance, " +
            "d.set_type AS set_type, d.weight_unit AS weight_unit " +
            "FROM workout_session_details d " +
            "JOIN exercises e ON e.id = d.exercise_id " +
            "WHERE d.workout_session_id IN (:sessionIds) " +
            "AND d.completed != 0 AND d.set_type != 'warmup' " +
            "ORDER BY d.workout_session_id, d.position",
    )
    suspend fun getCompletedForSessions(sessionIds: List<Long>): List<SessionDetailWithExerciseRow>

    /**
     * Sets of the most recent completed session that includes the exercise, in
     * position order — the source for the session screen's "Previous" column.
     */
    @Query(
        "SELECT d.actual_weight AS actual_weight, d.actual_reps AS actual_reps, " +
            "d.actual_duration AS actual_duration, d.actual_distance AS actual_distance, " +
            "d.weight_unit AS weight_unit " +
            "FROM workout_session_details d " +
            "JOIN workout_sessions s ON s.id = d.workout_session_id " +
            "WHERE d.exercise_id = :exerciseId AND s.id = (" +
            "SELECT s2.id FROM workout_sessions s2 " +
            "JOIN workout_session_details d2 ON d2.workout_session_id = s2.id " +
            "WHERE s2.profile_id = :profileId AND s2.completed_at IS NOT NULL " +
            "AND d2.exercise_id = :exerciseId " +
            "ORDER BY s2.performed_at DESC LIMIT 1) " +
            "ORDER BY d.position",
    )
    suspend fun getLatestCompletedSets(profileId: Long, exerciseId: Long): List<PreviousSetRow>

    @Insert
    suspend fun insert(detail: WorkoutSessionDetailEntity): Long
}
