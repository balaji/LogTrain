package dev.balaji.moonrose.logtrain.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans ORDER BY id")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun findById(id: Long): PlanEntity?

    @Insert
    suspend fun insert(plan: PlanEntity): Long

    @Query("UPDATE plans SET name = :name, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM plans WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY plan_id, position")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM routines WHERE plan_id = :planId")
    suspend fun nextPosition(planId: Long): Int

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

    @Insert
    suspend fun insert(session: WorkoutSessionEntity): Long
}

@Dao
interface WorkoutSessionDetailDao {
    @Query("SELECT * FROM workout_session_details WHERE workout_session_id = :sessionId ORDER BY position")
    fun observeForSession(sessionId: Long): Flow<List<WorkoutSessionDetailEntity>>

    @Insert
    suspend fun insert(detail: WorkoutSessionDetailEntity): Long
}
