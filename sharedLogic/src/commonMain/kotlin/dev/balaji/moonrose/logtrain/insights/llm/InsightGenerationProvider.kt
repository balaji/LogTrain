package dev.balaji.moonrose.logtrain.insights.llm

/**
 * One-shot on-device text generation for insight-card narration. Implemented
 * per platform (Apple Foundation Models on iOS; Gemini Nano on Android) and
 * injected into InsightNarrationState by the platform UI. Deliberately
 * callback-based rather than suspend so a Swift class can conform to the
 * bridged protocol directly.
 *
 * Providers never compute statistics themselves: InsightCardNarrator hands
 * them a fully-assembled prompt with every number pre-formatted.
 */
interface InsightGenerationProvider {
    /**
     * One of: "available", "device_not_eligible",
     * "apple_intelligence_disabled", "model_downloading".
     */
    fun availability(): String

    /** Hint that generations are imminent; may pre-load the model. */
    fun prewarm()

    /**
     * Generates a single response; no conversation state is kept between
     * calls. Exactly one of [onComplete]/[onError] is invoked; [onError]
     * receives a loggable message. Implementations MUST invoke callbacks on
     * the main thread.
     */
    fun generate(
        instructions: String,
        prompt: String,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    )
}

/**
 * Narration UI state for one insight card. Primitive-typed like the insight
 * models so it crosses the SKIE bridge cleanly.
 */
data class CardNarration(
    val cardId: String,
    /** "loading" | "ready" | "failed". */
    val status: String,
    val text: String,
)

/** A fully-assembled one-shot generation request for one card. */
data class CardNarrationRequest(
    val cardId: String,
    val instructions: String,
    val prompt: String,
    /** Cache key: hash of instructions + prompt + prompt version. */
    val statsHash: String,
)
