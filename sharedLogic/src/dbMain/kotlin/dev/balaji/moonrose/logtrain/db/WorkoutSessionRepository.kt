package dev.balaji.moonrose.logtrain.db

object WorkoutSessionRepository {
    /**
     * Creates a new session snapshotting every set target from the routine. The
     * flat `position` ordering matches moonrose: sets belonging to the same
     * exercise are always adjacent in position order.
     */
    suspend fun startFromRoutine(routineId: Long): Long {
        val now = nowMillis()
        val sessionId = AppDb.db.workoutSessionDao().insert(
            WorkoutSessionEntity(
                profileId = PROFILE_ID,
                routineId = routineId,
                performedAt = now,
                completedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val workSets = AppDb.db.workSetDao().getForRoutine(routineId)
        var position = 1
        for (ws in workSets) {
            val details = AppDb.db.workSetDetailDao().getForWorkSet(ws.id)
            if (details.isEmpty()) {
                // An exercise with no set details still deserves at least one
                // logging row so the user has something to fill in.
                AppDb.db.workoutSessionDetailDao().insert(
                    WorkoutSessionDetailEntity(
                        workoutSessionId = sessionId,
                        workSetDetailId = null,
                        exerciseId = ws.exerciseId,
                        position = position++,
                        targetWeight = null,
                        targetReps = null,
                        targetDuration = null,
                        actualWeight = null,
                        actualReps = null,
                        actualDuration = null,
                        completed = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            } else {
                for (detail in details) {
                    AppDb.db.workoutSessionDetailDao().insert(
                        WorkoutSessionDetailEntity(
                            workoutSessionId = sessionId,
                            workSetDetailId = detail.id,
                            exerciseId = ws.exerciseId,
                            position = position++,
                            targetWeight = detail.targetWeight,
                            targetReps = detail.targetReps,
                            targetDuration = detail.targetDuration,
                            actualWeight = null,
                            actualReps = null,
                            actualDuration = null,
                            completed = false,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }
        return sessionId
    }

    suspend fun startEmpty(): Long {
        val now = nowMillis()
        return AppDb.db.workoutSessionDao().insert(
            WorkoutSessionEntity(
                profileId = PROFILE_ID,
                routineId = null,
                performedAt = now,
                completedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Resumes an in-progress session only when its context matches the caller's
     * (same routine, or both empty). Otherwise starts fresh, so tapping "Start
     * Workout" on a routine always populates the routine's exercises even if
     * an unrelated in-progress session exists.
     */
    suspend fun resumeOrStart(routineId: Long): Long {
        val existing = AppDb.db.workoutSessionDao().findInProgress(PROFILE_ID)
        if (existing != null && (existing.routineId ?: 0L) == routineId) return existing.id
        return if (routineId > 0L) startFromRoutine(routineId) else startEmpty()
    }

    suspend fun finish(sessionId: Long) {
        val now = nowMillis()
        AppDb.db.workoutSessionDao().setCompletedAt(sessionId, now, now)
    }

    /** Cascades to `workout_session_details` via the FK. */
    suspend fun discard(sessionId: Long) {
        AppDb.db.workoutSessionDao().delete(sessionId)
    }
}
