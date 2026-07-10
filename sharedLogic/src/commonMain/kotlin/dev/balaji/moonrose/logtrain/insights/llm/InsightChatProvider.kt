package dev.balaji.moonrose.logtrain.insights.llm

/**
 * An on-device language model that answers questions about the user's
 * training data. Implemented per platform (Apple Foundation Models on iOS;
 * a Gemini Nano implementation can slot in on Android later) and injected
 * into ProgressChatState by the platform UI. Deliberately callback-based
 * rather than suspend so a Swift class can conform to the bridged protocol
 * directly.
 *
 * Providers never compute statistics themselves: they call the shared
 * insight tools (InsightToolExecutor) and narrate the results.
 */
interface InsightChatProvider {
    /**
     * One of: "available", "device_not_eligible",
     * "apple_intelligence_disabled", "model_downloading".
     */
    fun availability(): String

    /** Hint that a conversation is about to start; may pre-load the model. */
    fun prewarm()

    /**
     * Sends a user message. [onPartial] receives the cumulative response
     * text as it streams; exactly one of [onComplete]/[onError] follows.
     * [onError] receives a user-presentable message. Implementations MUST
     * invoke all callbacks on the main thread.
     */
    fun sendMessage(
        text: String,
        onPartial: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    )

    /** Drops the conversation context; the next send starts fresh. */
    fun resetSession()
}
