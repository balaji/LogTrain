package dev.balaji.moonrose.logtrain.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Projection for the workout-history list: one row per completed session. */
data class CompletedSessionRow(
    @ColumnInfo(name = "performed_at") val performedAt: Long,
    /** Null when the session had no routine or the routine was deleted. */
    @ColumnInfo(name = "routine_name") val routineName: String?,
)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY id")
    fun observeAll(): Flow<List<ProgramEntity>>

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
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun findById(id: Long): ExerciseEntity?

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>)

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

    @Insert
    suspend fun insert(detail: WorkSetDetailEntity): Long
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

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Query("UPDATE workout_sessions SET completed_at = :completedAt, updated_at = :now WHERE id = :id")
    suspend fun setCompletedAt(id: Long, completedAt: Long, now: Long)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WorkoutSessionDetailDao {
    @Query("SELECT * FROM workout_session_details WHERE workout_session_id = :sessionId ORDER BY position")
    fun observeForSession(sessionId: Long): Flow<List<WorkoutSessionDetailEntity>>

    @Query("SELECT * FROM workout_session_details WHERE workout_session_id = :sessionId ORDER BY position")
    suspend fun getForSession(sessionId: Long): List<WorkoutSessionDetailEntity>

    @Query("DELETE FROM workout_session_details WHERE workout_session_id = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Insert
    suspend fun insert(detail: WorkoutSessionDetailEntity): Long
}
