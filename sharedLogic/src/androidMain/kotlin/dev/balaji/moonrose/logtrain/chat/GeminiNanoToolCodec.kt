package dev.balaji.moonrose.logtrain.chat

import com.google.mlkit.genai.common.FunctionArgValue
import com.google.mlkit.genai.common.FunctionCall
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.schema.tools.FunctionDeclaration
import com.google.mlkit.genai.schema.tools.FunctionSchema
import com.google.mlkit.genai.schema.tools.FunctionType

/**
 * Schema declarations, argument decoding, request assembly, and error mapping
 * shared by [GeminiNanoChatProvider] and its host tests.
 */
internal object GeminiNanoToolCodec {

    const val TOOL_TRAINING_SUMMARY = "get_training_summary"
    const val TOOL_PERSONAL_RECORDS = "get_personal_records"
    const val TOOL_EXERCISE_PROGRESSION = "get_exercise_progression"
    const val TOOL_RECENT_WORKOUTS = "get_recent_workouts"
    const val TOOL_WORKOUTS_FOR_DAY = "get_workouts_for_day"
    const val TOOL_PROGRAMS_AND_ROUTINES = "get_programs_and_routines"
    const val TOOL_SEARCH_EXERCISES = "search_exercises"
    const val TOOL_BODY_METRICS = "get_body_metrics_and_profile"

    private fun stringSchema(description: String) = FunctionSchema(
        type = FunctionType.STRING,
        description = description,
    )

    private fun intSchema(description: String) = FunctionSchema(
        type = FunctionType.INTEGER,
        description = description,
    )

    private fun boolSchema(description: String) = FunctionSchema(
        type = FunctionType.BOOLEAN,
        description = description,
    )

    private fun objectSchema(
        properties: Map<String, FunctionSchema> = emptyMap(),
        required: List<String> = emptyList(),
    ) = FunctionSchema(
        type = FunctionType.OBJECT,
        properties = properties.ifEmpty { null },
        required = required.ifEmpty { null },
    )

    /** Dynamic tool schemas exposed when Gemini Nano supports `Feature.FUNCTION_CALLING`. */
    fun toolDeclarations(): List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = TOOL_TRAINING_SUMMARY,
            description = "Weekly volume trend, workout frequency/streaks, and body-part breakdown.",
            parameters = objectSchema(
                mapOf("weeks" to intSchema("Lookback weeks, 1 to 12. Defaults to 8.")),
            ),
        ),
        FunctionDeclaration(
            name = TOOL_PERSONAL_RECORDS,
            description = "Personal records across exercises or for one named exercise.",
            parameters = objectSchema(
                mapOf(
                    "exercise_query" to stringSchema("Optional exercise name or abbreviation."),
                    "limit" to intSchema("Max PR rows when unfiltered, 1 to 12. Defaults to 5."),
                ),
            ),
        ),
        FunctionDeclaration(
            name = TOOL_EXERCISE_PROGRESSION,
            description = "Weekly progression and recent working sets for one exercise.",
            parameters = objectSchema(
                properties = mapOf(
                    "exercise_query" to stringSchema("Exercise name or abbreviation."),
                    "weeks" to intSchema("Lookback weeks, 2 to 24. Defaults to 12."),
                ),
                required = listOf("exercise_query"),
            ),
        ),
        FunctionDeclaration(
            name = TOOL_RECENT_WORKOUTS,
            description = "Recent completed workouts with sets, weights, reps, and duration.",
            parameters = objectSchema(
                mapOf(
                    "limit" to intSchema("Max workouts, 1 to 10. Defaults to 5."),
                    "routine_query" to stringSchema("Optional routine name filter."),
                    "days" to intSchema("Optional lookback window in days."),
                ),
            ),
        ),
        FunctionDeclaration(
            name = TOOL_WORKOUTS_FOR_DAY,
            description = "Completed workouts performed on one specific epoch day.",
            parameters = objectSchema(
                properties = mapOf("epoch_day" to intSchema("Days since 1970-01-01.")),
                required = listOf("epoch_day"),
            ),
        ),
        FunctionDeclaration(
            name = TOOL_PROGRAMS_AND_ROUTINES,
            description = "Saved programs, routines in split order, target sets, and last performed dates.",
            parameters = objectSchema(
                mapOf("routine_query" to stringSchema("Optional routine name filter.")),
            ),
        ),
        FunctionDeclaration(
            name = TOOL_SEARCH_EXERCISES,
            description = "Search catalog exercises by name, body part, or equipment for form or substitutes.",
            parameters = objectSchema(
                mapOf(
                    "query" to stringSchema("Exercise name or keyword."),
                    "body_part" to stringSchema("Muscle or group like chest, back, legs, arms."),
                    "equipment" to stringSchema("Equipment like barbell, dumbbell, cable, bodyweight."),
                    "include_description" to boolSchema("Include form instructions when needed."),
                    "limit" to intSchema("Max results, 1 to 12. Defaults to 8."),
                ),
            ),
        ),
        FunctionDeclaration(
            name = TOOL_BODY_METRICS,
            description = "Body weight trend, profile details, and BMI using configured formula.",
            parameters = objectSchema(
                mapOf("days" to intSchema("Lookback days, 7 to 365. Defaults to 90.")),
            ),
        ),
    )

    /** Dispatches one model [FunctionCall] onto [toolbox]. */
    suspend fun executeToolCall(
        call: FunctionCall,
        toolbox: ChatToolboxPort = LiveChatToolboxPort,
    ): ToolOutput {
        val args = call.arguments
        return when (call.name) {
            TOOL_TRAINING_SUMMARY -> toolbox.getTrainingSummary(
                weeks = args.intArg("weeks") ?: 8,
            )
            TOOL_PERSONAL_RECORDS -> toolbox.getPersonalRecords(
                exerciseQuery = args.stringArg("exercise_query"),
                limit = args.intArg("limit") ?: 5,
            )
            TOOL_EXERCISE_PROGRESSION -> toolbox.getExerciseProgression(
                exerciseQuery = args.stringArg("exercise_query").orEmpty(),
                weeks = args.intArg("weeks") ?: 12,
            )
            TOOL_RECENT_WORKOUTS -> toolbox.getRecentWorkouts(
                limit = args.intArg("limit") ?: 5,
                routineQuery = args.stringArg("routine_query"),
                days = args.intArg("days"),
            )
            TOOL_WORKOUTS_FOR_DAY -> {
                val epochDay = args.longArg("epoch_day")
                if (epochDay == null) {
                    ToolOutput("Missing epoch_day argument.")
                } else {
                    toolbox.getWorkoutsForDay(epochDay)
                }
            }
            TOOL_PROGRAMS_AND_ROUTINES -> toolbox.getProgramsAndRoutines(
                routineQuery = args.stringArg("routine_query"),
            )
            TOOL_SEARCH_EXERCISES -> toolbox.searchExercises(
                query = args.stringArg("query"),
                bodyPart = args.stringArg("body_part"),
                equipment = args.stringArg("equipment"),
                includeDescription = args.boolArg("include_description") ?: false,
                limit = args.intArg("limit") ?: 8,
            )
            TOOL_BODY_METRICS -> toolbox.getBodyMetricsAndProfile(
                days = args.intArg("days") ?: 90,
            )
            else -> ToolOutput("Unknown tool: ${call.name}")
        }
    }

    /** Formats the final user turn text with optional pre-fetched context. */
    fun buildUserPrompt(request: ChatRequest, inlineInstructions: String? = null): String {
        val body = buildString {
            if (request.preloadedContext.isNotBlank()) {
                append("Context from LogTrain:\n")
                append(request.preloadedContext.trim())
                append("\n\nQuestion: ")
            }
            append(request.userMessage)
        }
        return if (inlineInstructions.isNullOrBlank()) {
            body
        } else {
            "$inlineInstructions\n\n$body"
        }
    }

    /** Converts [ChatRequest] history and current prompt into ML Kit [Content] turns. */
    fun buildInitialContents(
        request: ChatRequest,
        supportsSystemPrompt: Boolean,
    ): MutableList<Content> {
        val contents = mutableListOf<Content>()
        var prependedInlineInstructions = false

        for (turn in request.history) {
            val role = if (turn.role == ChatMessage.ROLE_USER) Content.Role.USER else Content.Role.MODEL
            val text = if (!supportsSystemPrompt && !prependedInlineInstructions && role == Content.Role.USER) {
                prependedInlineInstructions = true
                "${request.instructions}\n\n${turn.text}"
            } else {
                turn.text
            }
            contents += content(role) { text(text) }
        }

        val trailingInstructions = if (!supportsSystemPrompt && !prependedInlineInstructions) {
            request.instructions
        } else {
            null
        }
        contents += content(Content.Role.USER) {
            text(buildUserPrompt(request, trailingInstructions))
        }
        return contents
    }

    /** Builds a [GenerateContentRequest] with optional system instruction and dynamic tools. */
    fun buildGenerateContentRequest(
        contents: List<Content>,
        instructions: String,
        supportsSystemPrompt: Boolean,
        tools: List<FunctionDeclaration>,
    ): GenerateContentRequest = GenerateContentRequest.Builder(contents).apply {
        if (supportsSystemPrompt && instructions.isNotBlank()) {
            systemInstruction = SystemInstruction(instructions)
        }
        if (tools.isNotEmpty()) {
            setTools(tools)
        }
    }.build()

    /** Maps a thrown error into a language-neutral [ChatMessage] error key, or null when cancelled. */
    fun classifyErrorKey(throwable: Throwable): String? {
        if (throwable is GenAiException) {
            return when (throwable.errorCode) {
                GenAiException.ErrorCode.CANCELLED -> null
                GenAiException.ErrorCode.REQUEST_TOO_LARGE,
                GenAiException.ErrorCode.MAX_TURNS_EXCEEDED,
                -> ChatMessage.ERROR_CONTEXT_TOO_LONG
                GenAiException.ErrorCode.NOT_AVAILABLE,
                GenAiException.ErrorCode.NOT_SUPPORTED,
                GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
                GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
                -> ChatMessage.ERROR_UNAVAILABLE
                else -> ChatMessage.ERROR_GENERATION_FAILED
            }
        }
        val msg = throwable.message?.lowercase().orEmpty()
        return if ("too large" in msg || "context" in msg && "token" in msg) {
            ChatMessage.ERROR_CONTEXT_TOO_LONG
        } else {
            ChatMessage.ERROR_GENERATION_FAILED
        }
    }
}

private fun Map<String, FunctionArgValue>.stringArg(key: String): String? =
    when (val value = this[key]) {
        is FunctionArgValue.StringValue -> value.value.takeIf { it.isNotBlank() }
        is FunctionArgValue.IntValue -> value.value.toString()
        is FunctionArgValue.LongValue -> value.value.toString()
        is FunctionArgValue.DoubleValue -> value.value.toString()
        else -> null
    }

private fun Map<String, FunctionArgValue>.intArg(key: String): Int? =
    when (val value = this[key]) {
        is FunctionArgValue.IntValue -> value.value
        is FunctionArgValue.LongValue -> value.value.toInt()
        is FunctionArgValue.DoubleValue -> value.value.toInt()
        is FunctionArgValue.StringValue -> value.value.trim().toIntOrNull()
        else -> null
    }

private fun Map<String, FunctionArgValue>.longArg(key: String): Long? =
    when (val value = this[key]) {
        is FunctionArgValue.LongValue -> value.value
        is FunctionArgValue.IntValue -> value.value.toLong()
        is FunctionArgValue.DoubleValue -> value.value.toLong()
        is FunctionArgValue.StringValue -> value.value.trim().toLongOrNull()
        else -> null
    }

private fun Map<String, FunctionArgValue>.boolArg(key: String): Boolean? =
    when (val value = this[key]) {
        is FunctionArgValue.BoolValue -> value.value
        is FunctionArgValue.StringValue -> value.value.trim().lowercase().let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        else -> null
    }
