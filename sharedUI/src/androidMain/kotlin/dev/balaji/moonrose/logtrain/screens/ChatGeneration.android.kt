package dev.balaji.moonrose.logtrain.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import dev.balaji.moonrose.logtrain.chat.ChatGenerationProvider
import dev.balaji.moonrose.logtrain.chat.GeminiNanoChatProvider

@Composable
actual fun rememberChatGenerationProvider(): ChatGenerationProvider? =
    produceState<ChatGenerationProvider?>(
        initialValue = GeminiNanoChatProvider.currentIfAvailable(),
    ) {
        value = GeminiNanoChatProvider.awaitAvailableProvider()
    }.value
