package dev.balaji.moonrose.trainfree.screens

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.trainfree.insights.llm.InsightGenerationProvider

// Compose UI is Android-only; iOS injects FoundationModelInsightProvider natively in SwiftUI.
@Composable
actual fun rememberInsightGenerationProvider(): InsightGenerationProvider? = null
