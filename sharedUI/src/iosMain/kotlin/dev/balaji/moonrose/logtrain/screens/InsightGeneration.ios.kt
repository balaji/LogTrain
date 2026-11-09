package dev.balaji.moonrose.logtrain.screens

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.insights.llm.InsightGenerationProvider

// Compose UI is Android-only; iOS injects FoundationModelInsightProvider natively in SwiftUI.
@Composable
actual fun rememberInsightGenerationProvider(): InsightGenerationProvider? = null
