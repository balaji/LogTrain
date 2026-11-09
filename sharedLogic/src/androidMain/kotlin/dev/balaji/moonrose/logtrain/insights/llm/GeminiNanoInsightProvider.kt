package dev.balaji.moonrose.logtrain.insights.llm

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Feature
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * [InsightGenerationProvider] backed by Android's on-device Gemini Nano via
 * the ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt`).
 *
 * Each generation runs as a stateless one-shot [GenerateContentRequest] with
 * card-specific instructions and prompt so context never accumulates across
 * cards. All callbacks are invoked on the main thread as required by
 * [dev.balaji.moonrose.logtrain.db.InsightNarrationState].
 */
class GeminiNanoInsightProvider private constructor() : InsightGenerationProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client: GenerativeModel by lazy { Generation.getClient() }

    @Volatile
    private var cachedAvailability: String = "device_not_eligible"

    @Volatile
    private var systemPromptSupported: Boolean? = null

    override fun availability(): String = cachedAvailability

    override fun prewarm() {
        scope.launch {
            runCatching { client.warmup() }
        }
    }

    override fun generate(
        instructions: String,
        prompt: String,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val result = runCatching {
                val request = buildRequest(instructions, prompt)
                val response = client.generateContent(request)
                response.candidates.firstOrNull()?.text
                    ?: throw IllegalStateException("Empty response from Gemini Nano")
            }
            result.fold(
                onSuccess = { onComplete(it) },
                onFailure = { onError("On-device generation failed: ${it.message ?: it}") },
            )
        }
    }

    private suspend fun buildRequest(instructions: String, prompt: String): GenerateContentRequest {
        val supportsSystem = systemPromptSupported
            ?: runCatching { Feature.SYSTEM_PROMPT in client.getAvailableFeatures() }
                .getOrDefault(false)
                .also { systemPromptSupported = it }
        return if (supportsSystem) {
            GenerateContentRequest.Builder(
                SystemInstruction(instructions),
                TextPart(prompt),
            ).build()
        } else {
            GenerateContentRequest.Builder(
                TextPart("$instructions\n\n$prompt"),
            ).build()
        }
    }

    private suspend fun resolveAvailability(): Boolean {
        if (cachedAvailability == "available") return true
        return runCatching {
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> {
                    cachedAvailability = "available"
                    true
                }
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    cachedAvailability = "model_downloading"
                    val terminal = client.download().firstOrNull { status ->
                        status is DownloadStatus.DownloadCompleted ||
                            status is DownloadStatus.DownloadFailed
                    }
                    if (terminal is DownloadStatus.DownloadCompleted) {
                        cachedAvailability = "available"
                        true
                    } else {
                        cachedAvailability = "device_not_eligible"
                        false
                    }
                }
                else -> {
                    cachedAvailability = "device_not_eligible"
                    false
                }
            }
        }.getOrElse {
            cachedAvailability = "device_not_eligible"
            false
        }
    }

    companion object {
        private val instance = GeminiNanoInsightProvider()

        /**
         * Returns the shared provider immediately if its on-device model has
         * already been verified as available in this process, or `null` before
         * [awaitAvailableProvider] finishes.
         */
        fun currentIfAvailable(): GeminiNanoInsightProvider? =
            instance.takeIf { it.cachedAvailability == "available" }

        /**
         * Queries AICore for Gemini Nano availability (and awaits model
         * download if the device is eligible and downloadable), returning the
         * ready provider or `null` when unsupported.
         */
        suspend fun awaitAvailableProvider(): GeminiNanoInsightProvider? =
            if (instance.resolveAvailability()) instance else null
    }
}
