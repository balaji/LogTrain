package dev.balaji.moonrose.logtrain.chat

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.FunctionCall
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.FunctionCallPart
import com.google.mlkit.genai.prompt.FunctionResponsePart
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.content
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Minimal emission slice extracted from ML Kit's [GenerateContentResponse]. */
internal data class GeminiStreamChunk(
    val text: String = "",
    val functionCalls: List<FunctionCall> = emptyList(),
)

/**
 * Thin seam over ML Kit [GenerativeModel] so [GeminiNanoChatProvider] can run
 * full streaming, tool-loop, and error-mapping unit tests on the JVM host.
 */
internal interface GeminiNanoClient {
    suspend fun checkStatus(): Int
    suspend fun getAvailableFeatures(): List<Int>
    fun download(): Flow<DownloadStatus>
    suspend fun warmup()
    suspend fun countTokens(request: GenerateContentRequest): Int?
    suspend fun getTokenLimit(): Int?
    fun generateContentStream(request: GenerateContentRequest): Flow<GeminiStreamChunk>
}

private class LiveGeminiNanoClient : GeminiNanoClient {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun checkStatus(): Int = model.checkStatus()

    override suspend fun getAvailableFeatures(): List<Int> = model.getAvailableFeatures()

    override fun download(): Flow<DownloadStatus> = model.download()

    override suspend fun warmup() {
        model.warmup()
    }

    override suspend fun countTokens(request: GenerateContentRequest): Int? =
        runCatching { model.countTokens(request).totalTokens }.getOrNull()

    override suspend fun getTokenLimit(): Int? =
        runCatching { model.getTokenLimit() }.getOrNull()

    override fun generateContentStream(request: GenerateContentRequest): Flow<GeminiStreamChunk> =
        model.generateContentStream(request).map { response ->
            val candidate = response.candidates.firstOrNull()
            GeminiStreamChunk(
                text = candidate?.text.orEmpty(),
                functionCalls = candidate?.functionCalls.orEmpty(),
            )
        }
}

/**
 * On-device Android [ChatGenerationProvider] backed by Gemini Nano via ML Kit
 * GenAI Prompt (`com.google.mlkit:genai-prompt`).
 *
 * When AICore advertises [Feature.FUNCTION_CALLING], executes a bounded tool loop
 * (`MAX_TOOL_ROUNDS = 4`) against [ChatToolbox]. Otherwise [ChatState] uses
 * [ChatContextRouter] to attach compact pre-fetched data directly to the prompt.
 */
class GeminiNanoChatProvider internal constructor(
    private val client: GeminiNanoClient,
    private val toolbox: ChatToolboxPort = LiveChatToolboxPort,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ChatGenerationProvider {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val toolDeclarations by lazy { GeminiNanoToolCodec.toolDeclarations() }

    @Volatile
    private var cachedAvailability: String = AVAILABILITY_NOT_ELIGIBLE

    @Volatile
    private var systemPromptSupported: Boolean? = null

    @Volatile
    private var functionCallingSupported: Boolean? = null

    override fun availability(): String = cachedAvailability

    override fun supportsToolCalling(): Boolean = functionCallingSupported == true

    override fun prewarm() {
        scope.launch {
            runCatching { client.warmup() }
        }
    }

    override fun generate(
        request: ChatRequest,
        onPartial: (String) -> Unit,
        onComplete: (String, List<ChatSourceRef>) -> Unit,
        onError: (String, String) -> Unit,
    ): ChatStreamHandle {
        val cancelled = AtomicBoolean(false)
        val job = scope.launch {
            try {
                if (cachedAvailability != AVAILABILITY_AVAILABLE && !resolveAvailability()) {
                    if (!cancelled.get()) {
                        onError(ChatMessage.ERROR_UNAVAILABLE, "Gemini Nano is unavailable on this device.")
                    }
                    return@launch
                }

                probeFeaturesIfNeeded()
                val (answer, sources) = runConversationLoop(request, cancelled, onPartial)
                if (!cancelled.get()) {
                    if (answer.isBlank()) {
                        onError(ChatMessage.ERROR_GENERATION_FAILED, "Empty response from Gemini Nano.")
                    } else {
                        onComplete(answer, sources)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (!cancelled.get()) {
                    val errorKey = GeminiNanoToolCodec.classifyErrorKey(t) ?: return@launch
                    onError(errorKey, "On-device generation failed: ${t.message ?: t}")
                }
            }
        }

        return object : ChatStreamHandle {
            override fun cancel() {
                cancelled.set(true)
                job.cancel()
            }
        }
    }

    private suspend fun runConversationLoop(
        request: ChatRequest,
        cancelled: AtomicBoolean,
        onPartial: (String) -> Unit,
    ): Pair<String, List<ChatSourceRef>> {
        val useSystemPrompt = systemPromptSupported == true
        val canUseTools = functionCallingSupported == true
        val contents = GeminiNanoToolCodec.buildInitialContents(request, useSystemPrompt)
        val collectedSources = mutableListOf<ChatSourceRef>()

        val preflightReq = GeminiNanoToolCodec.buildGenerateContentRequest(
            contents = contents,
            instructions = request.instructions,
            supportsSystemPrompt = useSystemPrompt,
            tools = if (canUseTools) toolDeclarations else emptyList(),
        )
        checkTokenBudget(preflightReq)

        val textBuffer = StringBuilder()
        for (round in 0..MAX_TOOL_ROUNDS) {
            val allowToolsThisRound = canUseTools && round < MAX_TOOL_ROUNDS
            val genRequest = GeminiNanoToolCodec.buildGenerateContentRequest(
                contents = contents,
                instructions = request.instructions,
                supportsSystemPrompt = useSystemPrompt,
                tools = if (allowToolsThisRound) toolDeclarations else emptyList(),
            )

            val roundCalls = mutableListOf<FunctionCall>()
            client.generateContentStream(genRequest).collect { chunk ->
                if (cancelled.get()) return@collect
                if (chunk.functionCalls.isNotEmpty()) {
                    roundCalls += chunk.functionCalls
                }
                if (chunk.text.isNotEmpty() && roundCalls.isEmpty()) {
                    textBuffer.append(chunk.text)
                    onPartial(textBuffer.toString())
                }
            }

            if (cancelled.get()) return "" to emptyList()
            if (roundCalls.isEmpty()) break

            textBuffer.clear()
            contents += content(Content.Role.MODEL) {
                roundCalls.forEach { addPart(FunctionCallPart(it)) }
            }
            val responseParts = roundCalls.map { call ->
                val output = GeminiNanoToolCodec.executeToolCall(call, toolbox)
                collectedSources += output.sources
                FunctionResponsePart(
                    id = call.id,
                    name = call.name,
                    response = mapOf("result" to output.summary),
                )
            }
            contents += content(Content.Role.USER) {
                responseParts.forEach { addPart(it) }
            }
        }

        val uniqueSources = collectedSources.distinct()
        return textBuffer.toString().trim() to uniqueSources
    }

    private suspend fun checkTokenBudget(request: GenerateContentRequest) {
        val limit = client.getTokenLimit() ?: return
        if (limit <= 0) return
        val total = client.countTokens(request) ?: return
        if (total * 10 >= limit * 9) {
            throw ContextTooLongException("Context near token limit ($total / $limit).")
        }
    }

    internal suspend fun probeFeaturesIfNeeded() {
        if (systemPromptSupported != null && functionCallingSupported != null) return
        val features = runCatching { client.getAvailableFeatures() }.getOrDefault(emptyList())
        systemPromptSupported = Feature.SYSTEM_PROMPT in features
        functionCallingSupported = Feature.FUNCTION_CALLING in features
    }

    internal suspend fun resolveAvailability(): Boolean {
        if (cachedAvailability == AVAILABILITY_AVAILABLE) {
            probeFeaturesIfNeeded()
            return true
        }
        return runCatching {
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> {
                    cachedAvailability = AVAILABILITY_AVAILABLE
                    probeFeaturesIfNeeded()
                    true
                }
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    cachedAvailability = AVAILABILITY_DOWNLOADING
                    val terminal = client.download().firstOrNull { status ->
                        status is DownloadStatus.DownloadCompleted ||
                            status is DownloadStatus.DownloadFailed
                    }
                    if (terminal is DownloadStatus.DownloadCompleted) {
                        cachedAvailability = AVAILABILITY_AVAILABLE
                        probeFeaturesIfNeeded()
                        true
                    } else {
                        cachedAvailability = AVAILABILITY_NOT_ELIGIBLE
                        false
                    }
                }
                else -> {
                    cachedAvailability = AVAILABILITY_NOT_ELIGIBLE
                    false
                }
            }
        }.getOrElse {
            cachedAvailability = AVAILABILITY_NOT_ELIGIBLE
            false
        }
    }

    private class ContextTooLongException(message: String) : IllegalStateException(message) {
        // Caught by classifyErrorKey via "context" + "token" keywords when thrown pre-flight.
    }

    companion object {
        const val AVAILABILITY_AVAILABLE = "available"
        const val AVAILABILITY_DOWNLOADING = "model_downloading"
        const val AVAILABILITY_NOT_ELIGIBLE = "device_not_eligible"
        internal const val MAX_TOOL_ROUNDS = 4

        private val instance by lazy { GeminiNanoChatProvider(LiveGeminiNanoClient()) }

        /** Returns the shared provider immediately if verified available in this process. */
        fun currentIfAvailable(): GeminiNanoChatProvider? =
            instance.takeIf { it.cachedAvailability == AVAILABILITY_AVAILABLE }

        /** Queries AICore for availability (downloading if needed) and probes supported features. */
        suspend fun awaitAvailableProvider(): GeminiNanoChatProvider? =
            if (instance.resolveAvailability()) instance else null
    }
}
