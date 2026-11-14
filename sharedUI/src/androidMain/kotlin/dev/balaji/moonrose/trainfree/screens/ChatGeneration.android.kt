package dev.balaji.moonrose.trainfree.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.balaji.moonrose.trainfree.chat.ChatGenerationProvider
import dev.balaji.moonrose.trainfree.chat.GeminiNanoChatProvider

@Composable
actual fun rememberChatGenerationProvider(): ChatGenerationProvider? =
    produceState<ChatGenerationProvider?>(
        initialValue = GeminiNanoChatProvider.currentIfAvailable(),
    ) {
        value = GeminiNanoChatProvider.awaitAvailableProvider()
    }.value
