package dev.balaji.moonrose.trainfree.chat

/** Cancellation handle for an in-flight streaming chat generation. */
interface ChatStreamHandle {
    fun cancel()
}

/**
 * Streaming, multi-turn on-device chat driver implemented per platform
 * (Apple Foundation Models on iOS; Gemini Nano via ML Kit GenAI Prompt on
 * Android). Callback-based like [dev.balaji.moonrose.trainfree.insights.llm.InsightGenerationProvider]
 * so a Swift class can conform across the SKIE bridge directly.
 */
interface ChatGenerationProvider {
    /**
     * One of: "available", "device_not_eligible",
     * "apple_intelligence_disabled", "model_downloading".
     */
    fun availability(): String

    /**
     * Whether this driver executes native tool calls against [ChatToolbox]
     * during generation. When false, [ChatState] uses [ChatContextRouter] to
     * populate [ChatRequest.preloadedContext] deterministically beforehand.
     */
    fun supportsToolCalling(): Boolean

    /** Hint that the chat UI opened; may warm up model weights. */
    fun prewarm()

    /**
     * Starts a streaming generation for [request]. Implementations MUST invoke
     * callbacks on the main thread:
     * - [onPartial] receives the cumulative generated text so far.
     * - [onComplete] receives the final trimmed text and any [ChatSourceRef]s
     *   gathered from tool executions during this turn.
     * - [onError] receives a language-neutral error key (`ChatMessage.ERROR_*`)
     *   and a loggable debug message.
     */
    fun generate(
        request: ChatRequest,
        onPartial: (String) -> Unit,
        onComplete: (String, List<ChatSourceRef>) -> Unit,
        onError: (String, String) -> Unit,
    ): ChatStreamHandle
}
