package dev.balaji.moonrose.trainfree.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.balaji.moonrose.trainfree.insights.llm.GeminiNanoInsightProvider
import dev.balaji.moonrose.trainfree.insights.llm.InsightGenerationProvider

@Composable
actual fun rememberInsightGenerationProvider(): InsightGenerationProvider? =
    produceState<InsightGenerationProvider?>(
        initialValue = GeminiNanoInsightProvider.currentIfAvailable(),
    ) {
        value = GeminiNanoInsightProvider.awaitAvailableProvider()
    }.value
