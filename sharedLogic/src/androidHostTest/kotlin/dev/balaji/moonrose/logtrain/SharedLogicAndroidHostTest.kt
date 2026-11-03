package dev.balaji.moonrose.logtrain

import dev.balaji.moonrose.logtrain.db.DraftSessionExercise
import dev.balaji.moonrose.logtrain.db.DraftSessionSet
import dev.balaji.moonrose.logtrain.db.REST_TIMER_DURATION_MS
import dev.balaji.moonrose.logtrain.db.RestTimerTarget
import dev.balaji.moonrose.logtrain.db.SETTING_REST_TIMER_DURATION
import dev.balaji.moonrose.logtrain.db.SETTING_REST_TIMER_SOUND
import dev.balaji.moonrose.logtrain.db.UserSettingEntity
import dev.balaji.moonrose.logtrain.db.WorkoutSessionUiState
import dev.balaji.moonrose.logtrain.db.toUserSettings
import dev.balaji.moonrose.logtrain.model.DEFAULT_REST_TIMER_SECONDS
import dev.balaji.moonrose.logtrain.model.Exercise
import dev.balaji.moonrose.logtrain.model.RestTimerSound
import dev.balaji.moonrose.logtrain.model.clampRestTimerSeconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedLogicAndroidHostTest {

    private val bench = Exercise(
        id = 1L,
        name = "Bench Press",
        description = null,
        exerciseType = "strength",
        bodyPart = "chest",
        equipment = "barbell",
        builtin = true,
    )

    private val squat = Exercise(
        id = 2L,
        name = "Squat",
        description = null,
        exerciseType = "strength",
        bodyPart = "legs",
        equipment = "barbell",
        builtin = true,
    )

    private fun initialState(): WorkoutSessionUiState = WorkoutSessionUiState(
        loading = false,
        exercises = listOf(
            DraftSessionExercise(
                id = 10L,
                exercise = bench,
                sets = listOf(
                    DraftSessionSet(actualWeight = "60", actualReps = "8"),
                    DraftSessionSet(actualWeight = "60", actualReps = "8"),
                    DraftSessionSet(actualWeight = "60", actualReps = "8"),
                ),
            ),
            DraftSessionExercise(
                id = 20L,
                exercise = squat,
                sets = listOf(
                    DraftSessionSet(actualWeight = "100", actualReps = "5"),
                ),
            ),
        ),
    )

    @Test
    fun completingSetStartsTwoMinuteRestTimer() {
        val state = initialState().toggleCompletedAt(exerciseIndex = 0, setIndex = 1, now = 1_000L)

        assertTrue(state.exercises[0].sets[1].completed)
        assertEquals(
            RestTimerTarget(
                exerciseId = 10L,
                setIndex = 1,
                startedAtMillis = 1_000L,
                endsAtMillis = 1_000L + REST_TIMER_DURATION_MS,
            ),
            state.restTimer,
        )
        assertTrue(state.isRestTimerOn(exerciseId = 10L, setIndex = 1))
        assertFalse(state.isRestTimerOn(exerciseId = 10L, setIndex = 0))
    }

    @Test
    fun completingAnotherSetMovesAndRestartsTimer() {
        val afterFirst = initialState().toggleCompletedAt(exerciseIndex = 0, setIndex = 0, now = 1_000L)
        val afterSecond = afterFirst.toggleCompletedAt(exerciseIndex = 0, setIndex = 1, now = 45_000L)

        assertEquals(
            RestTimerTarget(
                exerciseId = 10L,
                setIndex = 1,
                startedAtMillis = 45_000L,
                endsAtMillis = 45_000L + REST_TIMER_DURATION_MS,
            ),
            afterSecond.restTimer,
        )
        assertFalse(afterSecond.isRestTimerOn(exerciseId = 10L, setIndex = 0))
        assertTrue(afterSecond.isRestTimerOn(exerciseId = 10L, setIndex = 1))
    }

    @Test
    fun uncheckingOwningSetCancelsTimerWhileOtherSetUncheckKeepsIt() {
        val s1 = initialState().toggleCompletedAt(exerciseIndex = 0, setIndex = 0, now = 1_000L)
        val s2 = s1.toggleCompletedAt(exerciseIndex = 0, setIndex = 1, now = 2_000L)

        // Unchecking set 0 (not owning the timer) keeps timer on set 1.
        val s3 = s2.toggleCompletedAt(exerciseIndex = 0, setIndex = 0, now = 3_000L)
        assertTrue(s3.isRestTimerOn(exerciseId = 10L, setIndex = 1))

        // Unchecking set 1 (owning the timer) cancels the timer.
        val s4 = s3.toggleCompletedAt(exerciseIndex = 0, setIndex = 1, now = 4_000L)
        assertNull(s4.restTimer)
    }

    @Test
    fun removingSetsUpdatesOrCancelsRestTimerIndex() {
        val state = initialState().toggleCompletedAt(exerciseIndex = 0, setIndex = 1, now = 1_000L)

        // Removing set 0 shifts the timer from index 1 -> 0.
        val shifted = state.removeSetAt(exerciseIndex = 0, setIndex = 0)
        assertEquals(0, shifted.restTimer?.setIndex)
        assertTrue(shifted.isRestTimerOn(exerciseId = 10L, setIndex = 0))

        // Removing the set that owns the timer cancels it.
        val cancelled = shifted.removeSetAt(exerciseIndex = 0, setIndex = 0)
        assertNull(cancelled.restTimer)
    }

    @Test
    fun removingExerciseCancelsTimerOnlyIfOwnedByThatExercise() {
        val state = initialState().toggleCompletedAt(exerciseIndex = 0, setIndex = 0, now = 1_000L)

        // Removing exercise 1 (squat) keeps timer on exercise 0 (bench).
        val kept = state.removeExerciseAt(exerciseIndex = 1)
        assertTrue(kept.isRestTimerOn(exerciseId = 10L, setIndex = 0))

        // Removing exercise 0 (bench) cancels the timer.
        val cancelled = kept.removeExerciseAt(exerciseIndex = 0)
        assertNull(cancelled.restTimer)
    }

    @Test
    fun clearingRequiredFieldOnTimerSetUncompletesAndCancelsTimer() {
        val state = initialState().toggleCompletedAt(exerciseIndex = 0, setIndex = 0, now = 1_000L)
        val invalidated = state.updateActualFieldAt(exerciseIndex = 0, setIndex = 0) {
            it.copy(actualReps = "")
        }

        assertFalse(invalidated.exercises[0].sets[0].completed)
        assertNull(invalidated.restTimer)
    }

    @Test
    fun completingSetUsesCustomRestTimerDuration() {
        val state = initialState().toggleCompletedAt(
            exerciseIndex = 0,
            setIndex = 1,
            now = 5_000L,
            durationSeconds = 90L,
        )

        assertEquals(
            RestTimerTarget(
                exerciseId = 10L,
                setIndex = 1,
                startedAtMillis = 5_000L,
                endsAtMillis = 95_000L,
            ),
            state.restTimer,
        )
        assertEquals(90L, state.restTimer?.durationSeconds)
    }

    @Test
    fun clampRestTimerSecondsSnapsToThirtySecondStepsInRange() {
        assertEquals(60, clampRestTimerSeconds(10))
        assertEquals(60, clampRestTimerSeconds(60))
        assertEquals(60, clampRestTimerSeconds(74))
        assertEquals(90, clampRestTimerSeconds(75))
        assertEquals(180, clampRestTimerSeconds(180))
        assertEquals(300, clampRestTimerSeconds(300))
        assertEquals(300, clampRestTimerSeconds(500))
    }

    @Test
    fun userSettingsDecodesRestTimerDurationAndSoundWithFallbacks() {
        val defaults = emptyList<UserSettingEntity>().toUserSettings()
        assertEquals(DEFAULT_REST_TIMER_SECONDS, defaults.restTimerDurationSeconds)
        assertEquals(RestTimerSound.DEFAULT, defaults.restTimerSound)

        val configured = listOf(
            UserSettingEntity(SETTING_REST_TIMER_DURATION, "210", 0L, 0L),
            UserSettingEntity(SETTING_REST_TIMER_SOUND, "antares", 0L, 0L),
        ).toUserSettings()
        assertEquals(210, configured.restTimerDurationSeconds)
        assertEquals(RestTimerSound.ANTARES, configured.restTimerSound)

        val silent = listOf(
            UserSettingEntity(SETTING_REST_TIMER_SOUND, "none", 0L, 0L),
        ).toUserSettings()
        assertEquals(RestTimerSound.NONE, silent.restTimerSound)
    }
}
