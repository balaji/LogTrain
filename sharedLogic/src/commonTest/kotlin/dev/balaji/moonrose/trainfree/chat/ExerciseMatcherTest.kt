package dev.balaji.moonrose.trainfree.chat

import dev.balaji.moonrose.trainfree.model.Exercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExerciseMatcherTest {
    private fun exercise(
        id: Long,
        name: String,
        bodyPart: String = "chest",
        otherBodyParts: List<String> = emptyList(),
        equipment: String = "barbell",
        exerciseType: String = "strength",
    ) = Exercise(
        id = id,
        name = name,
        description = "How to perform $name.",
        exerciseType = exerciseType,
        bodyPart = bodyPart,
        equipment = equipment,
        builtin = true,
        otherBodyParts = otherBodyParts,
    )

    private val catalog = listOf(
        exercise(1L, "Barbell Bench Press", "chest", listOf("triceps", "shoulders"), "barbell"),
        exercise(2L, "Dumbbell Bench Press", "chest", listOf("triceps", "shoulders"), "dumbbell"),
        exercise(3L, "Incline Dumbbell Press", "chest", listOf("shoulders", "triceps"), "dumbbell"),
        exercise(4L, "Barbell Overhead Press", "shoulders", listOf("triceps", "core"), "barbell"),
        exercise(5L, "Romanian Deadlift", "back", listOf("hamstrings", "glutes"), "barbell"),
        exercise(6L, "Barbell Back Squat", "thighs", listOf("glutes", "hamstrings"), "barbell"),
        exercise(7L, "Plank", "core", listOf("shoulders"), "body weight", "duration"),
        exercise(8L, "Lat Pulldown", "back", listOf("biceps"), "cable"),
        exercise(9L, "Leg Press", "thighs", listOf("glutes"), "machine"),
        exercise(10L, "Standing Calf Raise", "calves", emptyList(), "machine"),
    )

    @Test
    fun `exact case insensitive match wins immediately`() {
        val result = ExerciseMatcher.resolve("barbell bench press", catalog)
        val matched = assertIs<ExerciseMatchResult.Matched>(result)
        assertEquals(1L, matched.exercise.id)
    }

    @Test
    fun `expands common gym abbreviations`() {
        val ohp = assertIs<ExerciseMatchResult.Matched>(ExerciseMatcher.resolve("OHP", catalog))
        assertEquals(4L, ohp.exercise.id)

        val rdl = assertIs<ExerciseMatchResult.Matched>(ExerciseMatcher.resolve("rdl", catalog))
        assertEquals(5L, rdl.exercise.id)

        val dbBench = assertIs<ExerciseMatchResult.Matched>(ExerciseMatcher.resolve("db bench press", catalog))
        assertEquals(2L, dbBench.exercise.id)
    }

    @Test
    fun `trained history disambiguates bench press variants`() {
        val ambiguous = assertIs<ExerciseMatchResult.Ambiguous>(
            ExerciseMatcher.resolve("bench press", catalog),
        )
        assertEquals(setOf(1L, 2L), ambiguous.candidates.map { it.id }.toSet())

        val disambiguated = assertIs<ExerciseMatchResult.Matched>(
            ExerciseMatcher.resolve("bench press", catalog, trainedSetCounts = mapOf(1L to 24)),
        )
        assertEquals(1L, disambiguated.exercise.id)
    }

    @Test
    fun `multi word query requires all tokens so bench press never matches leg press`() {
        val missing = ExerciseMatcher.resolve("bench press", listOf(catalog.first { it.id == 9L }))
        assertIs<ExerciseMatchResult.NotFound>(missing)
    }

    @Test
    fun `blank or unknown query returns not found`() {
        assertIs<ExerciseMatchResult.NotFound>(ExerciseMatcher.resolve("   ", catalog))
        assertIs<ExerciseMatchResult.NotFound>(ExerciseMatcher.resolve("nordic ski erg", catalog))
    }

    @Test
    fun `search expands legs alias and ranks primary body parts before secondary`() {
        val legs = ExerciseMatcher.search(catalog, bodyPart = "legs", limit = 10)
        val names = legs.map { it.name }
        assertTrue("Barbell Back Squat" in names)
        assertTrue("Leg Press" in names)
        assertTrue("Standing Calf Raise" in names)
        // Romanian Deadlift has hamstrings/glutes as secondary muscles: included after primaries.
        assertTrue("Romanian Deadlift" in names)
        assertTrue(names.indexOf("Barbell Back Squat") < names.indexOf("Romanian Deadlift"))
        assertTrue("Lat Pulldown" !in names)
    }

    @Test
    fun `search supports equipment aliases and body weight`() {
        val dbChest = ExerciseMatcher.search(catalog, bodyPart = "chest", equipment = "db")
        assertEquals(listOf("Dumbbell Bench Press", "Incline Dumbbell Press"), dbChest.map { it.name })

        val bw = ExerciseMatcher.search(catalog, equipment = "bw")
        assertEquals(listOf("Plank"), bw.map { it.name })
    }
}
