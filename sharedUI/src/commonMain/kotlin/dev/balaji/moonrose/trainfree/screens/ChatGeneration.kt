package dev.balaji.moonrose.trainfree.screens

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.trainfree.chat.ChatGenerationProvider

/**
 * Returns the platform's on-device [ChatGenerationProvider] once verified as
 * available, or `null` when the device does not support on-device chat.
 */
@Composable
expect fun rememberChatGenerationProvider(): ChatGenerationProvider?
