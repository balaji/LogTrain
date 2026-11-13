package dev.balaji.moonrose.logtrain.screens

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.chat.ChatGenerationProvider

// Compose UI is Android-only; iOS injects FoundationModelChatProvider natively in SwiftUI.
@Composable
actual fun rememberChatGenerationProvider(): ChatGenerationProvider? = null
