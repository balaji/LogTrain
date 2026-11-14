package dev.balaji.moonrose.trainfree.chat

import dev.balaji.moonrose.trainfree.db.InsightsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared ephemeral conversation state for the on-device AI chat sheet.
 *
 * Both SwiftUI (`iosApp`) and Compose (`sharedUI`) observe [uiState] and
 * dispatch user actions here. Conversation history stays in memory for the
 * app session (cleared explicitly via [clearConversation]); no Room schema
 * tables are used in v1.
 *
 * Provider callbacks are contractually on the main thread (mirroring
 * [dev.balaji.moonrose.trainfree.db.InsightNarrationState]).
 */
class ChatState internal constructor(
    private val provider: ChatGenerationProvider?,
    private val router: ChatContextRouter,
    private val loadTopTrainedExercise: suspend () -> String?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
) {
    constructor(provider: ChatGenerationProvider?) : this(
        provider = provider,
        router = ChatContextRouter(),
        loadTopTrainedExercise = { InsightsRepository.trainedExerciseNames(limit = 1).firstOrNull() },
    )

    private val _uiState = MutableStateFlow(
        ChatUiState(
            availability = provider?.availability() ?: "device_not_eligible",
            suggestedPrompts = defaultPromptsFor(ChatSeedContext(), topExercise = null),
        ),
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeHandle: ChatStreamHandle? = null
    private var activeJob: Job? = null
    private var nextMessageId = 1L
    private var needsSessionReset = true
    private var activeGenerationId = 0L

    /** Refreshes availability, prewarms if available, and builds starter chips for [seed]. */
    fun openWithSeed(seed: ChatSeedContext = ChatSeedContext()) {
        val availability = provider?.availability() ?: "device_not_eligible"
        val previousSeed = _uiState.value.seedContext
        val seedChanged = previousSeed != seed
        if (seedChanged) {
            // Seed change starts a fresh scoped conversation so prior context doesn't leak.
            cancelActiveWork()
            needsSessionReset = true
        }
        _uiState.update { current ->
            current.copy(
                availability = availability,
                seedContext = seed,
                messages = if (seedChanged) emptyList() else current.messages,
                isGenerating = if (seedChanged) false else current.isGenerating,
                suggestedPrompts = defaultPromptsFor(seed, topExercise = null),
            )
        }
        if (availability == "available") {
            provider?.prewarm()
        }
        if (seed.kind == "general") {
            scope.launch {
                val top = runCatching { loadTopTrainedExercise() }.getOrNull()
                if (_uiState.value.seedContext == seed) {
                    _uiState.update { it.copy(suggestedPrompts = defaultPromptsFor(seed, top)) }
                }
            }
        }
    }

    /** Sends [rawText] as a user message and streams the assistant's reply. */
    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating) return

        val availability = provider?.availability() ?: "device_not_eligible"
        val userMsg = ChatMessage(
            id = "msg-${nextMessageId++}",
            role = ChatMessage.ROLE_USER,
            text = text,
            status = ChatMessage.STATUS_COMPLETE,
        )
        val assistantId = "msg-${nextMessageId++}"

        if (provider == null || availability != "available") {
            val errorMsg = ChatMessage(
                id = assistantId,
                role = ChatMessage.ROLE_ASSISTANT,
                text = "",
                status = ChatMessage.STATUS_ERROR,
                errorKey = ChatMessage.ERROR_UNAVAILABLE,
            )
            _uiState.update {
                it.copy(
                    availability = availability,
                    messages = it.messages + userMsg + errorMsg,
                    isGenerating = false,
                )
            }
            return
        }

        val priorMessages = _uiState.value.messages
        val seed = _uiState.value.seedContext
        val placeholder = ChatMessage(
            id = assistantId,
            role = ChatMessage.ROLE_ASSISTANT,
            text = "",
            status = ChatMessage.STATUS_STREAMING,
        )
        _uiState.update {
            it.copy(
                availability = availability,
                messages = it.messages + userMsg + placeholder,
                isGenerating = true,
            )
        }

        val generationId = ++activeGenerationId
        activeJob = scope.launch {
            val routed = if (!provider.supportsToolCalling() || seed.kind != "general") {
                runCatching { router.route(text, seed) }.getOrNull()
            } else {
                null
            }
            if (generationId != activeGenerationId) return@launch

            val history = ChatPromptBuilder.windowHistory(priorMessages)
            startProviderTurn(
                provider = provider,
                generationId = generationId,
                assistantId = assistantId,
                userMessage = text,
                history = history,
                preloaded = routed,
                seed = seed,
                allowCompactRetry = true,
            )
        }
    }

    private fun startProviderTurn(
        provider: ChatGenerationProvider,
        generationId: Long,
        assistantId: String,
        userMessage: String,
        history: List<ChatTurn>,
        preloaded: ToolOutput?,
        seed: ChatSeedContext,
        allowCompactRetry: Boolean,
    ) {
        val reset = needsSessionReset
        needsSessionReset = false
        val request = ChatRequest(
            instructions = ChatPromptBuilder.buildInstructions(
                seedContext = seed,
                supportsToolCalling = provider.supportsToolCalling(),
            ),
            history = history,
            userMessage = userMessage,
            preloadedContext = preloaded?.summary.orEmpty(),
            resetSession = reset,
        )

        activeHandle = provider.generate(
            request = request,
            onPartial = { partial ->
                if (generationId != activeGenerationId) return@generate
                updateMessage(assistantId) { it.copy(text = partial) }
            },
            onComplete = { finalText, toolSources ->
                if (generationId != activeGenerationId) return@generate
                activeHandle = null
                val mergedSources = (toolSources + preloaded?.sources.orEmpty())
                    .distinctBy { Triple(it.kind, it.exerciseId, it.epochDay) to it.label }
                val trimmed = finalText.trim()
                if (trimmed.isEmpty()) {
                    updateMessage(assistantId) {
                        it.copy(
                            text = "",
                            status = ChatMessage.STATUS_ERROR,
                            errorKey = ChatMessage.ERROR_GENERATION_FAILED,
                        )
                    }
                } else {
                    updateMessage(assistantId) {
                        it.copy(
                            text = trimmed,
                            status = ChatMessage.STATUS_COMPLETE,
                            sources = mergedSources,
                        )
                    }
                }
                _uiState.update { it.copy(isGenerating = false) }
            },
            onError = { errorKey, _ ->
                if (generationId != activeGenerationId) return@generate
                activeHandle = null
                if (errorKey == ChatMessage.ERROR_CONTEXT_TOO_LONG && allowCompactRetry) {
                    needsSessionReset = true
                    updateMessage(assistantId) { it.copy(text = "") }
                    startProviderTurn(
                        provider = provider,
                        generationId = generationId,
                        assistantId = assistantId,
                        userMessage = userMessage,
                        history = emptyList(),
                        preloaded = preloaded,
                        seed = seed,
                        allowCompactRetry = false,
                    )
                    return@generate
                }
                updateMessage(assistantId) {
                    it.copy(
                        status = ChatMessage.STATUS_ERROR,
                        errorKey = errorKey.ifBlank { ChatMessage.ERROR_GENERATION_FAILED },
                    )
                }
                _uiState.update { it.copy(isGenerating = false) }
            },
        )
    }

    /** Stops an in-flight generation; drops empty placeholders or marks partial text cancelled. */
    fun cancelGeneration() {
        if (!_uiState.value.isGenerating) return
        cancelActiveWork()
        _uiState.update { current ->
            val updated = current.messages.mapNotNull { msg ->
                if (msg.status != ChatMessage.STATUS_STREAMING) {
                    msg
                } else if (msg.text.isBlank()) {
                    null
                } else {
                    msg.copy(status = ChatMessage.STATUS_CANCELLED)
                }
            }
            current.copy(messages = updated, isGenerating = false)
        }
    }

    /** Clears all messages and resets the underlying session for a fresh start. */
    fun clearConversation() {
        cancelActiveWork()
        needsSessionReset = true
        _uiState.update { it.copy(messages = emptyList(), isGenerating = false) }
    }

    /** Retries the most recent failed user prompt. */
    fun retryLast() {
        if (_uiState.value.isGenerating) return
        val messages = _uiState.value.messages
        val last = messages.lastOrNull() ?: return
        if (last.role != ChatMessage.ROLE_ASSISTANT || last.status != ChatMessage.STATUS_ERROR) return
        val lastUser = messages.dropLast(1).lastOrNull { it.role == ChatMessage.ROLE_USER } ?: return
        _uiState.update { it.copy(messages = messages.dropLast(2)) }
        sendMessage(lastUser.text)
    }

    private fun cancelActiveWork() {
        activeGenerationId++
        activeJob?.cancel()
        activeJob = null
        activeHandle?.cancel()
        activeHandle = null
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { current ->
            current.copy(
                messages = current.messages.map { if (it.id == id) transform(it) else it },
            )
        }
    }

    private fun defaultPromptsFor(
        seed: ChatSeedContext,
        topExercise: String?,
    ): List<ChatSuggestedPrompt> = when (seed.kind) {
        "exercise" -> buildList {
            val name = seed.exerciseName
            add(ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_EXERCISE_PROGRESSION, name))
            add(ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_EXERCISE_FORM, name))
            add(ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_EXERCISE_SUBSTITUTES, name))
        }
        "day" -> {
            val dateLabel = if (seed.epochDay > 0L) ChatToolbox.shortDate(seed.epochDay) else ""
            listOf(
                ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_DAY_SUMMARY, dateLabel),
                ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_SUMMARY),
                ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_PRS),
            )
        }
        else -> buildList {
            add(ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_SUMMARY))
            add(ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_PRS))
            if (!topExercise.isNullOrBlank()) {
                add(ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_EXERCISE_PROGRESSION, topExercise))
            } else {
                add(ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_LAST_WORKOUT))
            }
            add(ChatSuggestedPrompt(ChatSuggestedPrompt.KEY_NEXT_ROUTINE))
        }
    }
}
