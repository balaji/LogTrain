package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.insights.llm.InsightChatProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatMessage(
    val id: Long,
    /** "user" | "assistant". */
    val role: String,
    val text: String,
    /** True while the assistant message is still streaming. */
    val pending: Boolean = false,
)

data class ProgressChatUiState(
    /** Provider availability string, or "no_provider" when none was injected. */
    val availability: String = "no_provider",
    val messages: List<ChatMessage> = emptyList(),
    val responding: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Conversation state for the insights chat. The platform UI injects its
 * on-device InsightChatProvider (Foundation Models on iOS); provider
 * callbacks are contractually on the main thread, so state mutation needs
 * no synchronization.
 */
class ProgressChatState(private val provider: InsightChatProvider?) {
    private val _uiState = MutableStateFlow(
        ProgressChatUiState(availability = provider?.availability() ?: "no_provider"),
    )
    val uiState: StateFlow<ProgressChatUiState> = _uiState.asStateFlow()
    private var nextId = 0L

    init {
        if (provider?.availability() == "available") provider.prewarm()
    }

    fun send(text: String) {
        val provider = provider ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.responding) return

        val userMessage = ChatMessage(id = nextId++, role = "user", text = trimmed)
        val pendingId = nextId++
        val pending = ChatMessage(id = pendingId, role = "assistant", text = "", pending = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage + pending,
            responding = true,
            errorMessage = null,
        )

        provider.sendMessage(
            text = trimmed,
            onPartial = { partial -> updateMessage(pendingId) { it.copy(text = partial) } },
            onComplete = { full ->
                updateMessage(pendingId) { it.copy(text = full, pending = false) }
                _uiState.value = _uiState.value.copy(responding = false)
            },
            onError = { message ->
                // Drop the empty pending bubble; keep any streamed partial text.
                val current = _uiState.value
                _uiState.value = current.copy(
                    messages = current.messages.mapNotNull {
                        when {
                            it.id != pendingId -> it
                            it.text.isEmpty() -> null
                            else -> it.copy(pending = false)
                        }
                    },
                    responding = false,
                    errorMessage = message,
                )
            },
        )
    }

    fun resetConversation() {
        provider?.resetSession()
        _uiState.value = _uiState.value.copy(messages = emptyList(), responding = false, errorMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun updateMessage(id: Long, transform: (ChatMessage) -> ChatMessage) {
        val current = _uiState.value
        _uiState.value = current.copy(
            messages = current.messages.map { if (it.id == id) transform(it) else it },
        )
    }
}
