package dev.balaji.moonrose.logtrain.chat

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
