package dev.balaji.moonrose.trainfree.chat

import dev.balaji.moonrose.trainfree.model.Exercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private class RecordingToolbox : ChatToolboxPort {
    val calls = mutableListOf<String>()

    override suspend fun getTrainingSummary(weeks: Int): ToolOutput {
        calls += "summary:$weeks"
        return ToolOutput("Summary block", listOf(ChatSourceRef("progress", "Progress overview")))
    }

    override suspend fun getPersonalRecords(exerciseQuery: String?, limit: Int): ToolOutput {
        calls += "prs:${exerciseQuery.orEmpty()}"
        return ToolOutput("PR block for ${exerciseQuery ?: "all"}", listOf(ChatSourceRef("exercise", exerciseQuery ?: "All", 1L)))
    }

    override suspend fun getExerciseProgression(exerciseQuery: String, weeks: Int): ToolOutput {
        calls += "prog:$exerciseQuery"
        return ToolOutput("Progression block for $exerciseQuery", listOf(ChatSourceRef("exercise", exerciseQuery, 1L)))
    }

    override suspend fun getRecentWorkouts(limit: Int, routineQuery: String?, days: Int?): ToolOutput {
        calls += "recent:${routineQuery.orEmpty()}"
        return ToolOutput("Recent workouts block", listOf(ChatSourceRef("day", "Nov 10", epochDay = 20_000L)))
    }

    override suspend fun getWorkoutsForDay(epochDay: Long): ToolOutput {
        calls += "day:$epochDay"
        return ToolOutput("Day $epochDay block", listOf(ChatSourceRef("day", "Day", epochDay = epochDay)))
    }

    override suspend fun getProgramsAndRoutines(routineQuery: String?): ToolOutput {
        calls += "programs:${routineQuery.orEmpty()}"
        return ToolOutput("Programs block", listOf(ChatSourceRef("program", "PPL")))
    }

    override suspend fun searchExercises(
        query: String?,
        bodyPart: String?,
        equipment: String?,
        includeDescription: Boolean,
        limit: Int,
    ): ToolOutput {
        calls += "search:q=${query.orEmpty()},bp=${bodyPart.orEmpty()},eq=${equipment.orEmpty()},desc=$includeDescription"
        return ToolOutput("Exercise search block")
    }

    override suspend fun getBodyMetricsAndProfile(days: Int): ToolOutput {
        calls += "body:$days"
        return ToolOutput("Body metrics block")
    }
}

class ChatContextRouterTest {
    private val bench = Exercise(1L, "Barbell Bench Press", "Press bar.", "strength", "chest", "barbell", true)
    private val dbBench = Exercise(2L, "Dumbbell Bench Press", "Press DBs.", "strength", "chest", "dumbbell", true)
    private val ohp = Exercise(3L, "Barbell Overhead Press", "Press overhead.", "strength", "shoulders", "barbell", true)
    private val catalog = listOf(bench, dbBench, ohp)
    private val trained = mapOf(1L to 30, 3L to 12)

    private fun router(toolbox: RecordingToolbox = RecordingToolbox()) = ChatContextRouter(
        toolbox = toolbox,
        loadExercises = { catalog },
        loadTrainedCounts = { trained },
        loadRoutineNames = { listOf("Push Day", "Pull Day") },
    )

    @Test
    fun `routes OHP PR query to personal records without triggering body metrics`() = runBlocking {
        val tb = RecordingToolbox()
        val out = router(tb).route("What is my best OHP PR?")
        assertEquals(listOf("prs:Barbell Overhead Press"), tb.calls)
        assertTrue(out.summary.contains("PR block for Barbell Overhead Press"))
    }

    @Test
    fun `routes dumbbell substitute question for bench press`() = runBlocking {
        val tb = RecordingToolbox()
        router(tb).route("What dumbbell substitute can I use instead of bench press?")
        assertTrue(tb.calls.any { it == "search:q=,bp=chest,eq=dumbbell,desc=false" })
    }

    @Test
    fun `distinguishes lifting weight question from body weight and BMI`() {
        val r = router()
        val liftPlan = r.analyzePrompt("What weight did I use on bench press last time?", ChatSeedContext(), catalog, trained, emptyList())
        assertEquals("Barbell Bench Press", liftPlan.exerciseQuery)
        assertFalse(liftPlan.wantsBodyMetrics)
        assertTrue(liftPlan.wantsProgression)

        val bodyPlan = r.analyzePrompt("How is my body weight and BMI trending?", ChatSeedContext(), catalog, trained, emptyList())
        assertNull(bodyPlan.exerciseQuery)
        assertTrue(bodyPlan.wantsBodyMetrics)
    }

    @Test
    fun `routes routine mention and day or exercise seed contexts`() = runBlocking {
        val tbRoutine = RecordingToolbox()
        router(tbRoutine).route("When was my last workout on Push Day?")
        assertEquals(listOf("recent:Push Day"), tbRoutine.calls)

        val tbDay = RecordingToolbox()
        router(tbDay).route("Summarize this workout", ChatSeedContext(kind = "day", epochDay = 20_100L))
        assertEquals(listOf("day:20100"), tbDay.calls)

        val tbExercise = RecordingToolbox()
        router(tbExercise).route(
            "How do I perform this with good form?",
            ChatSeedContext(kind = "exercise", exerciseId = 1L, exerciseName = "Barbell Bench Press"),
        )
        assertEquals(listOf("search:q=Barbell Bench Press,bp=,eq=,desc=false"), tbExercise.calls)

        val tbExplain = RecordingToolbox()
        router(tbExplain).route(
            "What is Barbell Bench Press?",
            ChatSeedContext(kind = "exercise", exerciseId = 1L, exerciseName = "Barbell Bench Press"),
        )
        assertEquals(listOf("search:q=Barbell Bench Press,bp=,eq=,desc=false"), tbExplain.calls)

        val tbUnknownForm = RecordingToolbox()
        val unknownOut = router(tbUnknownForm).route("How do I perform a Jefferson Curl?")
        assertTrue(tbUnknownForm.calls.isEmpty())
        assertEquals("", unknownOut.summary)
    }

    @Test
    fun `unmatched prompt falls back to training summary and recent workouts and respects char cap`() = runBlocking {
        val tbSummary = RecordingToolbox()
        router(tbSummary).route("How am I doing?")
        assertEquals(listOf("summary:8"), tbSummary.calls)

        val tbFallback = RecordingToolbox()
        val out = router(tbFallback).route("Hey coach", maxContextChars = 20)
        assertEquals(listOf("summary:8", "recent:"), tbFallback.calls)
        assertTrue(out.summary.length <= 20)
    }
}
