package dev.balaji.moonrose.trainfree.chat

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.FunctionArgValue
import com.google.mlkit.genai.common.FunctionCall
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.FunctionResponsePart
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

private class RecordingChatToolboxPort : ChatToolboxPort {
    val calls = mutableListOf<String>()

    override suspend fun getTrainingSummary(weeks: Int): ToolOutput {
        calls += "summary:$weeks"
        return ToolOutput(
            summary = "8-week volume 12,400 kg",
            sources = listOf(ChatSourceRef(kind = "progress", label = "Training summary")),
        )
    }

    override suspend fun getPersonalRecords(exerciseQuery: String?, limit: Int): ToolOutput {
        calls += "prs:${exerciseQuery.orEmpty()}:$limit"
        return ToolOutput(
            summary = "Bench Press best 100 kg x 5",
            sources = listOf(ChatSourceRef(kind = "exercise", label = "Bench Press", exerciseId = 7L)),
        )
    }

    override suspend fun getExerciseProgression(exerciseQuery: String, weeks: Int): ToolOutput {
        calls += "prog:$exerciseQuery:$weeks"
        return ToolOutput(
            summary = "$exerciseQuery trending +5 kg over $weeks weeks",
            sources = listOf(ChatSourceRef(kind = "exercise", label = exerciseQuery, exerciseId = 7L)),
        )
    }

    override suspend fun getRecentWorkouts(limit: Int, routineQuery: String?, days: Int?): ToolOutput {
        calls += "recent:$limit:${routineQuery.orEmpty()}:${days ?: -1}"
        return ToolOutput("Last workout: Push A (45 min)")
    }

    override suspend fun getWorkoutsForDay(epochDay: Long): ToolOutput {
        calls += "day:$epochDay"
        return ToolOutput("Completed Push A on $epochDay")
    }

    override suspend fun getProgramsAndRoutines(routineQuery: String?): ToolOutput {
        calls += "programs:${routineQuery.orEmpty()}"
        return ToolOutput("Upper/Lower split")
    }

    override suspend fun searchExercises(
        query: String?,
        bodyPart: String?,
        equipment: String?,
        includeDescription: Boolean,
        limit: Int,
    ): ToolOutput {
        calls += "search:${query.orEmpty()}:${bodyPart.orEmpty()}:${equipment.orEmpty()}:$includeDescription:$limit"
        return ToolOutput("Incline Dumbbell Press, Chest Dip")
    }

    override suspend fun getBodyMetricsAndProfile(days: Int): ToolOutput {
        calls += "metrics:$days"
        return ToolOutput("Weight 78.2 kg, BMI 23.8")
    }
}

private class FakeGeminiNanoClient(
    var status: Int = FeatureStatus.AVAILABLE,
    var features: List<Int> = listOf(Feature.SYSTEM_PROMPT, Feature.FUNCTION_CALLING),
    var tokenCount: Int? = 200,
    var tokenLimit: Int? = 4096,
    private val streams: (GenerateContentRequest) -> Flow<GeminiStreamChunk>,
) : GeminiNanoClient {
    val requests = mutableListOf<GenerateContentRequest>()

    override suspend fun checkStatus(): Int = status
    override suspend fun getAvailableFeatures(): List<Int> = features
    override fun download(): Flow<DownloadStatus> = emptyFlow()
    override suspend fun warmup() = Unit
    override suspend fun countTokens(request: GenerateContentRequest): Int? = tokenCount
    override suspend fun getTokenLimit(): Int? = tokenLimit
    override fun generateContentStream(request: GenerateContentRequest): Flow<GeminiStreamChunk> {
        requests += request
        return streams(request)
    }
}

class GeminiNanoChatProviderTest {

    @Test
    fun probesCapabilitiesAndRunsMultiRoundToolLoop() = runBlocking {
        val toolbox = RecordingChatToolboxPort()
        var turn = 0
        val client = FakeGeminiNanoClient(
            streams = {
                turn += 1
                if (turn == 1) {
                    flowOf(
                        GeminiStreamChunk(
                            functionCalls = listOf(
                                FunctionCall(
                                    name = GeminiNanoToolCodec.TOOL_PERSONAL_RECORDS,
                                    arguments = mapOf(
                                        "exercise_query" to FunctionArgValue.StringValue("Bench Press"),
                                        "limit" to FunctionArgValue.IntValue(3),
                                    ),
                                    id = "call_1",
                                ),
                            ),
                        ),
                    )
                } else {
                    flowOf(
                        GeminiStreamChunk(text = "Your best bench "),
                        GeminiStreamChunk(text = "is 100 kg x 5."),
                    )
                }
            },
        )

        val provider = GeminiNanoChatProvider(client, toolbox, Dispatchers.Unconfined)
        assertTrue(provider.resolveAvailability())
        assertEquals(GeminiNanoChatProvider.AVAILABILITY_AVAILABLE, provider.availability())
        assertTrue(provider.supportsToolCalling())

        val partials = mutableListOf<String>()
        var completedText = ""
        var completedSources = emptyList<ChatSourceRef>()
        var failedKey = ""

        provider.generate(
            request = ChatRequest(
                instructions = "Be factual.",
                history = emptyList(),
                userMessage = "What is my bench PR?",
            ),
            onPartial = { partials += it },
            onComplete = { text, sources ->
                completedText = text
                completedSources = sources
            },
            onError = { key, _ -> failedKey = key },
        )

        assertEquals("", failedKey)
        assertEquals(listOf("prs:Bench Press:3"), toolbox.calls)
        assertEquals(listOf("Your best bench ", "Your best bench is 100 kg x 5."), partials)
        assertEquals("Your best bench is 100 kg x 5.", completedText)
        assertEquals(1, completedSources.size)
        assertEquals("Bench Press", completedSources.single().label)

        // Second request carried the FunctionResponsePart result back to the model.
        assertEquals(2, client.requests.size)
        assertFalse(client.requests[0].dynamicTools.orEmpty().isEmpty())
        val responsePart = client.requests[1].contents.last().parts.filterIsInstance<FunctionResponsePart>().single()
        assertEquals("Bench Press best 100 kg x 5", responsePart.response["result"])
    }

    @Test
    fun fallsBackToInlineInstructionsAndPreloadedContextWithoutSystemPromptOrTools() = runBlocking {
        val toolbox = RecordingChatToolboxPort()
        val client = FakeGeminiNanoClient(
            features = emptyList(),
            streams = { flowOf(GeminiStreamChunk(text = "Logged Push A.")) },
        )

        val provider = GeminiNanoChatProvider(client, toolbox, Dispatchers.Unconfined)
        assertTrue(provider.resolveAvailability())
        assertFalse(provider.supportsToolCalling())

        var completedText = ""
        provider.generate(
            request = ChatRequest(
                instructions = "SYSTEM GUARDRAIL",
                history = emptyList(),
                userMessage = "What did I train?",
                preloadedContext = "Recent workouts: Push A",
            ),
            onPartial = {},
            onComplete = { text, _ -> completedText = text },
            onError = { _, _ -> },
        )

        assertEquals("Logged Push A.", completedText)
        val sent = client.requests.single()
        assertTrue(sent.dynamicTools.orEmpty().isEmpty())
        assertEquals(null, sent.systemInstruction)
        val promptText = sent.contents.single().parts.filterIsInstance<TextPart>().single().textString
        assertTrue("SYSTEM GUARDRAIL" in promptText)
        assertTrue("Recent workouts: Push A" in promptText)
        assertTrue("Question: What did I train?" in promptText)
    }

    @Test
    fun stopsToolCallingOnFinalSynthesisPassAfterMaxToolRounds() = runBlocking {
        val toolbox = RecordingChatToolboxPort()
        val client = FakeGeminiNanoClient(
            streams = { req ->
                if (!req.dynamicTools.isNullOrEmpty()) {
                    flowOf(
                        GeminiStreamChunk(
                            functionCalls = listOf(
                                FunctionCall(GeminiNanoToolCodec.TOOL_TRAINING_SUMMARY, emptyMap(), "c"),
                            ),
                        ),
                    )
                } else {
                    flowOf(GeminiStreamChunk(text = "Synthesized answer."))
                }
            },
        )

        val provider = GeminiNanoChatProvider(client, toolbox, Dispatchers.Unconfined)
        var result = ""
        provider.generate(
            request = ChatRequest(instructions = "Inst", history = emptyList(), userMessage = "Summary"),
            onPartial = {},
            onComplete = { text, _ -> result = text },
            onError = { _, _ -> },
        )

        assertEquals(GeminiNanoChatProvider.MAX_TOOL_ROUNDS, toolbox.calls.size)
        assertEquals(GeminiNanoChatProvider.MAX_TOOL_ROUNDS + 1, client.requests.size)
        assertTrue(client.requests.last().dynamicTools.isNullOrEmpty())
        assertEquals("Synthesized answer.", result)
    }

    @Test
    fun triggersContextTooLongWhenPreflightApproachesTokenLimit() {
        val client = FakeGeminiNanoClient(
            tokenCount = 3800,
            tokenLimit = 4096,
            streams = { emptyFlow() },
        )
        val provider = GeminiNanoChatProvider(client, RecordingChatToolboxPort(), Dispatchers.Unconfined)
        var errorKey = ""
        provider.generate(
            request = ChatRequest(instructions = "Inst", history = emptyList(), userMessage = "Hi"),
            onPartial = {},
            onComplete = { _, _ -> },
            onError = { key, _ -> errorKey = key },
        )

        assertEquals(ChatMessage.ERROR_CONTEXT_TOO_LONG, errorKey)
        assertEquals(0, client.requests.size)
    }

    @Test
    fun mapsGenAiExceptionsToNeutralErrorKeys() {
        assertEquals(
            ChatMessage.ERROR_CONTEXT_TOO_LONG,
            GeminiNanoToolCodec.classifyErrorKey(GenAiException(RuntimeException("too big"), GenAiException.ErrorCode.REQUEST_TOO_LARGE)),
        )
        assertEquals(
            ChatMessage.ERROR_UNAVAILABLE,
            GeminiNanoToolCodec.classifyErrorKey(GenAiException(RuntimeException("missing"), GenAiException.ErrorCode.NOT_AVAILABLE)),
        )
        assertEquals(
            null,
            GeminiNanoToolCodec.classifyErrorKey(GenAiException(RuntimeException("cancelled"), GenAiException.ErrorCode.CANCELLED)),
        )

        val client = FakeGeminiNanoClient(
            streams = {
                flow {
                    throw GenAiException(RuntimeException("overflow"), GenAiException.ErrorCode.REQUEST_TOO_LARGE)
                }
            },
        )
        val provider = GeminiNanoChatProvider(client, RecordingChatToolboxPort(), Dispatchers.Unconfined)
        var errorKey = ""
        provider.generate(
            request = ChatRequest(instructions = "Inst", history = emptyList(), userMessage = "Hi"),
            onPartial = {},
            onComplete = { _, _ -> },
            onError = { key, _ -> errorKey = key },
        )
        assertEquals(ChatMessage.ERROR_CONTEXT_TOO_LONG, errorKey)
    }

    @Test
    fun dispatchesAllEightToolCallsAndParsesArguments() = runBlocking {
        val toolbox = RecordingChatToolboxPort()
        GeminiNanoToolCodec.executeToolCall(
            FunctionCall(GeminiNanoToolCodec.TOOL_TRAINING_SUMMARY, mapOf("weeks" to FunctionArgValue.IntValue(4)), "1"),
            toolbox,
        )
        GeminiNanoToolCodec.executeToolCall(
            FunctionCall(GeminiNanoToolCodec.TOOL_PERSONAL_RECORDS, mapOf("exercise_query" to FunctionArgValue.StringValue("squat")), "2"),
            toolbox,
        )
        GeminiNanoToolCodec.executeToolCall(
            FunctionCall(GeminiNanoToolCodec.TOOL_EXERCISE_PROGRESSION, mapOf("exercise_query" to FunctionArgValue.StringValue("deadlift"), "weeks" to FunctionArgValue.LongValue(6L)), "3"),
            toolbox,
        )
        GeminiNanoToolCodec.executeToolCall(
            FunctionCall(GeminiNanoToolCodec.TOOL_RECENT_WORKOUTS, mapOf("limit" to FunctionArgValue.IntValue(3), "routine_query" to FunctionArgValue.StringValue("Pull"), "days" to FunctionArgValue.StringValue("14")), "4"),
            toolbox,
        )
        GeminiNanoToolCodec.executeToolCall(
            FunctionCall(GeminiNanoToolCodec.TOOL_WORKOUTS_FOR_DAY, mapOf("epoch_day" to FunctionArgValue.LongValue(20400L)), "5"),
            toolbox,
        )
        GeminiNanoToolCodec.executeToolCall(
            FunctionCall(GeminiNanoToolCodec.TOOL_PROGRAMS_AND_ROUTINES, mapOf("routine_query" to FunctionArgValue.StringValue("Upper")), "6"),
            toolbox,
        )
        GeminiNanoToolCodec.executeToolCall(
            FunctionCall(
                GeminiNanoToolCodec.TOOL_SEARCH_EXERCISES,
                mapOf(
                    "query" to FunctionArgValue.StringValue("row"),
                    "body_part" to FunctionArgValue.StringValue("back"),
                    "equipment" to FunctionArgValue.StringValue("dumbbell"),
                    "limit" to FunctionArgValue.IntValue(4),
                ),
                "7",
            ),
            toolbox,
        )
        GeminiNanoToolCodec.executeToolCall(
            FunctionCall(GeminiNanoToolCodec.TOOL_BODY_METRICS, mapOf("days" to FunctionArgValue.IntValue(30)), "8"),
            toolbox,
        )

        assertEquals(
            listOf(
                "summary:4",
                "prs:squat:5",
                "prog:deadlift:6",
                "recent:3:Pull:14",
                "day:20400",
                "programs:Upper",
                "search:row:back:dumbbell:false:4",
                "metrics:30",
            ),
            toolbox.calls,
        )
    }

    @Test
    fun cancelSuppressesFurtherStreamCallbacks() = runBlocking {
        val firstChunkDelivered = CompletableDeferred<Unit>()
        val client = FakeGeminiNanoClient(
            streams = {
                flow {
                    emit(GeminiStreamChunk(text = "First "))
                    firstChunkDelivered.complete(Unit)
                    delay(5_000)
                    emit(GeminiStreamChunk(text = "Never delivered"))
                }
            },
        )

        val provider = GeminiNanoChatProvider(client, RecordingChatToolboxPort(), Dispatchers.Default)
        assertTrue(provider.resolveAvailability())

        var partialCount = 0
        var completed = false
        var errored = false
        val handle = provider.generate(
            request = ChatRequest(instructions = "Inst", history = emptyList(), userMessage = "Hi"),
            onPartial = { partialCount += 1 },
            onComplete = { _, _ -> completed = true },
            onError = { _, _ -> errored = true },
        )

        firstChunkDelivered.await()
        handle.cancel()
        delay(50)

        assertEquals(1, partialCount)
        assertFalse(completed)
        assertFalse(errored)
    }
}
