package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.model.ExerciseType
import dev.balaji.moonrose.logtrain.model.isSetComplete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Minimal snapshot of the in-progress session, for chrome that lives outside the session screen itself. */
data class ActiveWorkoutSession(
    val sessionId: Long,
    val routineId: Long,
    val title: String,
    val startedAt: Long,
)

object WorkoutSessionRepository {
    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _activeRestTimer = MutableStateFlow<RestTimerTarget?>(null)
    /**
     * Tracks the active 2-minute rest timer across the entire app so navigating
     * away from the workout screen (to view other tabs/pages) neither drops the
     * countdown nor misses its completion alert.
     */
    val activeRestTimer: StateFlow<RestTimerTarget?> = _activeRestTimer.asStateFlow()

    private var cachedFormState: WorkoutSessionFormState? = null

    /**
     * Returns the shared [WorkoutSessionFormState] for [sessionId], reusing it
     * across back/forward navigation so in-memory state (including the active
     * rest timer) survives leaving and re-entering the workout screen.
     */
    fun formState(sessionId: Long): WorkoutSessionFormState {
        val existing = cachedFormState
        if (existing != null && existing.sessionId == sessionId && !existing.isClosed) {
            return existing
        }
        existing?.closeQuietly()
        return WorkoutSessionFormState(sessionId).also { cachedFormState = it }
    }

    internal fun updateActiveRestTimer(timer: RestTimerTarget?) {
        _activeRestTimer.value = timer
    }

    /** Clears the rest timer once its countdown finishes. */
    fun clearActiveRestTimer() {
        _activeRestTimer.value = null
        cachedFormState?.clearRestTimer()
    }

    internal fun evictFormState(sessionId: Long) {
        if (cachedFormState?.sessionId == sessionId) {
            cachedFormState = null
        }
    }

    /**
     * Tracks whichever session is currently in progress, so a persistent mini
     * bar (bottom overlay on Android/Desktop, safe-area inset on iOS) can be
     * shown while the user navigates away from the session screen. Backed by
     * Room's `observeInProgress` query, so it updates automatically on
     * start/finish/discard without any manual bookkeeping here.
     */
    val activeSession: StateFlow<ActiveWorkoutSession?> =
        AppDb.db.workoutSessionDao().observeInProgress(PROFILE_ID)
            .map { entity ->
                entity?.let {
                    val routineId = it.routineId ?: 0L
                    val title = if (routineId > 0L) {
                        AppDb.db.routineDao().findById(routineId)?.name ?: "Workout"
                    } else {
                        "Empty workout"
                    }
                    ActiveWorkoutSession(
                        sessionId = it.id,
                        routineId = routineId,
                        title = title,
                        startedAt = it.performedAt,
                    )
                }
            }
            .stateIn(repositoryScope, SharingStarted.Eagerly, null)

    /**
     * Creates a new session snapshotting every set target from the routine. The
     * flat `position` ordering matches moonrose: sets belonging to the same
     * exercise are always adjacent in position order.
     */
    suspend fun startFromRoutine(routineId: Long): Long {
        cachedFormState?.closeQuietly()
        cachedFormState = null
        _activeRestTimer.value = null

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
                        targetDistance = null,
                        actualWeight = null,
                        actualReps = null,
                        actualDuration = null,
                        actualDistance = null,
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
                            targetDistance = detail.targetDistance,
                            actualWeight = null,
                            actualReps = null,
                            actualDuration = null,
                            actualDistance = null,
                            setType = detail.setType,
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
        cachedFormState?.closeQuietly()
        cachedFormState = null
        _activeRestTimer.value = null

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

    /**
     * Whether the persisted session has anything worth saving. Chrome outside
     * the session screen (the mini bar) uses this to decide between finishing
     * and offering to discard; the screen itself checks its own form state.
     */
    suspend fun hasCompletedSets(sessionId: Long): Boolean {
        val cached = cachedFormState?.takeIf { it.sessionId == sessionId && !it.uiState.value.loading }
        if (cached != null) return cached.uiState.value.hasCompletedSets
        return AppDb.db.workoutSessionDetailDao().hasCompletedSets(sessionId)
    }

    /**
     * Sets missing a value their exercise type requires (reps, duration or
     * distance) are dropped so the completed workout only records work that
     * was actually performed. Pruning here (rather than in the form) keeps
     * the session-screen finish and the mini-bar finish consistent.
     */
    suspend fun finish(sessionId: Long) {
        _activeRestTimer.value = null
        val cached = cachedFormState?.takeIf { it.sessionId == sessionId }
        if (cached != null) {
            cached.flushBeforeExternalFinish()
            cachedFormState = null
        }
        finishPersisted(sessionId)
    }

    internal suspend fun finishPersisted(sessionId: Long) {
        val now = nowMillis()
        val details = AppDb.db.workoutSessionDetailDao().getForSession(sessionId)
        val typeByExercise = details.map { it.exerciseId }.distinct().associateWith { id ->
            ExerciseType.fromValue(ExerciseRepository.findById(id)?.exerciseType) ?: ExerciseType.STRENGTH
        }
        val emptyIds = details.filter { d ->
            val type = typeByExercise.getValue(d.exerciseId)
            !type.isSetComplete(d.actualReps, d.actualDuration, d.actualDistance)
        }.map { it.id }
        if (emptyIds.isNotEmpty()) AppDb.db.workoutSessionDetailDao().deleteByIds(emptyIds)
        AppDb.db.workoutSessionDao().setCompletedAt(sessionId, now, now)
    }

    /** Cascades to `workout_session_details` via the FK. */
    suspend fun discard(sessionId: Long) {
        _activeRestTimer.value = null
        val cached = cachedFormState?.takeIf { it.sessionId == sessionId }
        if (cached != null) {
            cached.closeQuietly()
            cachedFormState = null
        }
        AppDb.db.workoutSessionDao().delete(sessionId)
    }
}
