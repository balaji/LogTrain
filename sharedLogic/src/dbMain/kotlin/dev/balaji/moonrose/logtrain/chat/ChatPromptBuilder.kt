package dev.balaji.moonrose.logtrain.chat

import dev.balaji.moonrose.logtrain.insights.llm.currentLanguageTag

/**
 * Assembles concise, guardrail-enforced system instructions and windowed
 * conversation transcripts sized for ~4K-token on-device models.
 */
internal object ChatPromptBuilder {
    private val LANGUAGE_NAMES = mapOf("sv" to "Swedish")

    internal const val DEFAULT_MAX_HISTORY_TURNS = 6
    internal const val DEFAULT_MAX_HISTORY_CHARS = 2400

    fun buildInstructions(
        seedContext: ChatSeedContext,
        supportsToolCalling: Boolean,
        languageTag: String = currentLanguageTag(),
    ): String {
        val languageName = LANGUAGE_NAMES[languageTag] ?: "English"
        val toolClause = if (supportsToolCalling) {
            "Always call the available workout and exercise tools to look up user data or catalog exercises before answering. "
        } else {
            "Answer using the provided workout data context. "
        }
        val seedClause = when (seedContext.kind) {
            "exercise" -> if (seedContext.exerciseName.isNotBlank()) {
                "The user opened this chat from the exercise \"${seedContext.exerciseName}\". "
            } else {
                ""
            }
            "day" -> if (seedContext.epochDay > 0L) {
                "The user opened this chat from their workout log on ${ChatToolbox.shortDate(seedContext.epochDay)}. "
            } else {
                ""
            }
            else -> ""
        }
        return "You are a concise, factual strength-training coach inside the LogTrain workout app. " +
            toolClause +
            seedClause +
            "Quote ONLY numbers, dates, exercises, and units returned by the tools or context — never invent, extrapolate, or recalculate statistics. " +
            "If requested data is missing or empty, say so briefly. " +
            "For form tips or exercise substitutions, prefer exercises from the app's catalog. " +
            "No medical or injury-rehab diagnoses, no diet or weight-loss prescriptions; keep body-weight and BMI remarks neutral and factual. " +
            "Keep answers brief and scannable. Respond in $languageName."
    }

    /**
     * Keeps the most recent completed turns within [maxTurns] and [maxChars]
     * so older turns drop smoothly before overflowing the ~4K context window.
     */
    fun windowHistory(
        messages: List<ChatMessage>,
        maxTurns: Int = DEFAULT_MAX_HISTORY_TURNS,
        maxChars: Int = DEFAULT_MAX_HISTORY_CHARS,
    ): List<ChatTurn> {
        val completed = messages.filter {
            it.status == ChatMessage.STATUS_COMPLETE && it.text.isNotBlank()
        }
        if (completed.isEmpty() || maxTurns <= 0 || maxChars <= 0) return emptyList()

        val selected = mutableListOf<ChatTurn>()
        var chars = 0
        for (msg in completed.asReversed()) {
            if (selected.size >= maxTurns) break
            val turnText = msg.text.trim()
            if (selected.isNotEmpty() && chars + turnText.length > maxChars) break
            val clipped = if (turnText.length > maxChars) turnText.take(maxChars) else turnText
            selected.add(ChatTurn(role = msg.role, text = clipped))
            chars += clipped.length
        }
        return selected.asReversed()
    }
}
