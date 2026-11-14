package dev.balaji.moonrose.trainfree.screens

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.trainfree.insights.llm.InsightGenerationProvider

/**
 * Returns the platform's on-device [InsightGenerationProvider] once it is
 * verified as available, or `null` when the device does not support on-device
 * narration.
 */
@Composable
expect fun rememberInsightGenerationProvider(): InsightGenerationProvider?
