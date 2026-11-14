package dev.balaji.moonrose.trainfree.chat

/**
 * Reference to an underlying app record cited by a tool result, so the UI
 * can display clickable verification chips (e.g. jump to an exercise or day)
 * without parsing text. Primitive-typed for SKIE compatibility.
 */
data class ChatSourceRef(
    /** "exercise" | "day" | "program" | "progress". */
    val kind: String,
    val label: String,
    val exerciseId: Long = 0L,
    val epochDay: Long = 0L,
)

/**
 * Result of executing one [ChatToolbox] read query: [summary] is a compact,
 * unit-labeled, token-bounded text block for the language model; [sources]
 * carries structured references for UI verification pills.
 */
data class ToolOutput(
    val summary: String,
    val sources: List<ChatSourceRef> = emptyList(),
)

/**
 * Optional screen context when launching chat from Exercise Detail or Day
 * Detail instead of the Progress tab. Primitive-typed for SKIE.
 */
data class ChatSeedContext(
    /** "general" | "exercise" | "day". */
    val kind: String = "general",
    val exerciseId: Long = 0L,
    val exerciseName: String = "",
    val epochDay: Long = 0L,
)

/**
 * One user or assistant bubble rendered in the chat UI.
 * Status values: "streaming" | "complete" | "error" | "cancelled".
 */
data class ChatMessage(
    val id: String,
    /** "user" | "assistant". */
    val role: String,
    val text: String,
    val status: String = STATUS_COMPLETE,
    /** Language-neutral error key when [status] == "error": "unavailable" | "context_too_long" | "generation_failed" | "". */
    val errorKey: String = "",
    val sources: List<ChatSourceRef> = emptyList(),
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        const val STATUS_STREAMING = "streaming"
        const val STATUS_COMPLETE = "complete"
        const val STATUS_ERROR = "error"
        const val STATUS_CANCELLED = "cancelled"

        const val ERROR_UNAVAILABLE = "unavailable"
        const val ERROR_CONTEXT_TOO_LONG = "context_too_long"
        const val ERROR_GENERATION_FAILED = "generation_failed"
    }
}

/**
 * Starter prompt suggestion keyed by a language-neutral identifier so each UI
 * maps [key] + [arg] to localized text via strings.xml / L10n.swift.
 */
data class ChatSuggestedPrompt(
    val key: String,
    val arg: String = "",
) {
    companion object {
        const val KEY_SUMMARY = "summary"
        const val KEY_PRS = "prs"
        const val KEY_LAST_WORKOUT = "last_workout"
        const val KEY_NEXT_ROUTINE = "next_routine"
        const val KEY_EXERCISE_PROGRESSION = "exercise_progression"
        const val KEY_EXERCISE_FORM = "exercise_form"
        const val KEY_EXERCISE_SUBSTITUTES = "exercise_substitutes"
        const val KEY_DAY_SUMMARY = "day_summary"
    }
}

/** Minimal prior turn passed to [ChatGenerationProvider] inside the context window. */
data class ChatTurn(
    val role: String,
    val text: String,
)

/**
 * Fully assembled generation request handed from [ChatState] to the platform
 * [ChatGenerationProvider].
 */
data class ChatRequest(
    val instructions: String,
    val history: List<ChatTurn>,
    val userMessage: String,
    /** Pre-fetched [ChatToolbox] context used when tool calling is unavailable or context is pre-seeded. */
    val preloadedContext: String = "",
    /** True when the conversation was cleared or compacted so stateful sessions (iOS) should reset. */
    val resetSession: Boolean = false,
)

/** Observed by SwiftUI and Compose chat views via [ChatState.uiState]. */
data class ChatUiState(
    val availability: String = "device_not_eligible",
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val seedContext: ChatSeedContext = ChatSeedContext(),
    val suggestedPrompts: List<ChatSuggestedPrompt> = emptyList(),
)
