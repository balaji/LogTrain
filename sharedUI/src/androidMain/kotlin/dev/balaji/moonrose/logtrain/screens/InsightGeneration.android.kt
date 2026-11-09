package dev.balaji.moonrose.logtrain.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.balaji.moonrose.logtrain.insights.llm.GeminiNanoInsightProvider
import dev.balaji.moonrose.logtrain.insights.llm.InsightGenerationProvider

@Composable
actual fun rememberInsightGenerationProvider(): InsightGenerationProvider? =
    produceState<InsightGenerationProvider?>(
        initialValue = GeminiNanoInsightProvider.currentIfAvailable(),
    ) {
        value = GeminiNanoInsightProvider.awaitAvailableProvider()
    }.value
