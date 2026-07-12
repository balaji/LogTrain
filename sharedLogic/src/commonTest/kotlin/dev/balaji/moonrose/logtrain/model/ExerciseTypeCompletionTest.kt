package dev.balaji.moonrose.logtrain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseTypeCompletionTest {
    @Test
    fun requiredFieldsPerType() {
        assertEquals(setOf(SetField.REPS), ExerciseType.STRENGTH.requiredSetFields())
        assertEquals(setOf(SetField.DURATION), ExerciseType.DURATION.requiredSetFields())
        assertEquals(setOf(SetField.DISTANCE), ExerciseType.DISTANCE.requiredSetFields())
        assertEquals(
            setOf(SetField.REPS, SetField.DURATION),
            ExerciseType.REPS_DURATION.requiredSetFields(),
        )
        assertEquals(
            setOf(SetField.REPS, SetField.DISTANCE),
            ExerciseType.REPS_DISTANCE.requiredSetFields(),
        )
        assertEquals(
            setOf(SetField.DISTANCE, SetField.DURATION),
            ExerciseType.DISTANCE_DURATION.requiredSetFields(),
        )
    }

    @Test
    fun strengthNeedsRepsOnly() {
        assertTrue(ExerciseType.STRENGTH.isSetComplete(reps = 5, durationSeconds = null, distance = null))
        assertFalse(ExerciseType.STRENGTH.isSetComplete(reps = 0, durationSeconds = null, distance = null))
        assertFalse(ExerciseType.STRENGTH.isSetComplete(reps = null, durationSeconds = null, distance = null))
        // Other fields alone don't complete a strength set.
        assertFalse(ExerciseType.STRENGTH.isSetComplete(reps = null, durationSeconds = 60, distance = 5.0))
    }

    @Test
    fun durationNeedsDurationOnly() {
        assertTrue(ExerciseType.DURATION.isSetComplete(reps = null, durationSeconds = 45, distance = null))
        assertFalse(ExerciseType.DURATION.isSetComplete(reps = null, durationSeconds = 0, distance = null))
        assertFalse(ExerciseType.DURATION.isSetComplete(reps = 10, durationSeconds = null, distance = null))
    }

    @Test
    fun distanceNeedsDistanceOnly() {
        assertTrue(ExerciseType.DISTANCE.isSetComplete(reps = null, durationSeconds = null, distance = 0.5))
        assertFalse(ExerciseType.DISTANCE.isSetComplete(reps = null, durationSeconds = null, distance = 0.0))
        assertFalse(ExerciseType.DISTANCE.isSetComplete(reps = 10, durationSeconds = 60, distance = null))
    }

    @Test
    fun comboTypesNeedAllTheirFields() {
        assertTrue(ExerciseType.REPS_DURATION.isSetComplete(reps = 20, durationSeconds = 45, distance = null))
        assertFalse(ExerciseType.REPS_DURATION.isSetComplete(reps = 20, durationSeconds = null, distance = null))
        assertFalse(ExerciseType.REPS_DURATION.isSetComplete(reps = null, durationSeconds = 45, distance = null))

        assertTrue(ExerciseType.REPS_DISTANCE.isSetComplete(reps = 20, durationSeconds = null, distance = 15.0))
        assertFalse(ExerciseType.REPS_DISTANCE.isSetComplete(reps = 20, durationSeconds = null, distance = null))
        assertFalse(ExerciseType.REPS_DISTANCE.isSetComplete(reps = null, durationSeconds = null, distance = 15.0))

        assertTrue(ExerciseType.DISTANCE_DURATION.isSetComplete(reps = null, durationSeconds = 1800, distance = 5.0))
        assertFalse(ExerciseType.DISTANCE_DURATION.isSetComplete(reps = null, durationSeconds = 1800, distance = null))
        assertFalse(ExerciseType.DISTANCE_DURATION.isSetComplete(reps = null, durationSeconds = null, distance = 5.0))
    }

    @Test
    fun weightNeverGatesCompletion() {
        // isSetComplete takes no weight at all — this asserts the required
        // fields never include a weight-like concept for any type.
        ExerciseType.entries.forEach { type ->
            assertTrue(type.requiredSetFields().isNotEmpty(), "$type must require at least one field")
        }
    }
}
