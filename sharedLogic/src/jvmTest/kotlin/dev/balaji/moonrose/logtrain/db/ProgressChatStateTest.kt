package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.insights.llm.InsightChatProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeProvider : InsightChatProvider {
    var prewarmed = false
    var resets = 0
    var lastText: String? = null
    var onPartial: ((String) -> Unit)? = null
    var onComplete: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    override fun availability() = "available"
    override fun prewarm() { prewarmed = true }
    override fun resetSession() { resets++ }
    override fun sendMessage(
        text: String,
        onPartial: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        lastText = text
        this.onPartial = onPartial
        this.onComplete = onComplete
        this.onError = onError
    }
}

class ProgressChatStateTest {
    @Test
    fun `streams partials into the pending assistant message and completes`() {
        val provider = FakeProvider()
        val state = ProgressChatState(provider)
        assertTrue(provider.prewarmed)
        assertEquals("available", state.uiState.value.availability)

        state.send("  how is my bench?  ")
        assertEquals("how is my bench?", provider.lastText)
        assertEquals(listOf("user", "assistant"), state.uiState.value.messages.map { it.role })
        assertTrue(state.uiState.value.responding)
        assertTrue(state.uiState.value.messages.last().pending)

        provider.onPartial!!("Your bench")
        assertEquals("Your bench", state.uiState.value.messages.last().text)

        provider.onComplete!!("Your bench is trending up.")
        val done = state.uiState.value
        assertFalse(done.responding)
        assertFalse(done.messages.last().pending)
        assertEquals("Your bench is trending up.", done.messages.last().text)
    }

    @Test
    fun `errors drop an empty pending bubble but keep streamed text`() {
        val provider = FakeProvider()
        val state = ProgressChatState(provider)

        state.send("hello")
        provider.onError!!("Model unavailable.")
        var current = state.uiState.value
        assertEquals(listOf("user"), current.messages.map { it.role })
        assertEquals("Model unavailable.", current.errorMessage)
        assertFalse(current.responding)

        state.clearError()
        assertNull(state.uiState.value.errorMessage)

        state.send("second try")
        provider.onPartial!!("Partial answer")
        provider.onError!!("Context overflow.")
        current = state.uiState.value
        assertEquals("Partial answer", current.messages.last().text)
        assertFalse(current.messages.last().pending)
        assertEquals("Context overflow.", current.errorMessage)
    }

    @Test
    fun `sends are ignored while responding and reset clears the conversation`() {
        val provider = FakeProvider()
        val state = ProgressChatState(provider)

        state.send("first")
        state.send("second")
        assertEquals("first", provider.lastText)
        assertEquals(2, state.uiState.value.messages.size)

        provider.onComplete!!("done")
        state.resetConversation()
        assertEquals(1, provider.resets)
        assertTrue(state.uiState.value.messages.isEmpty())
    }

    @Test
    fun `no provider means no_provider availability and inert sends`() {
        val state = ProgressChatState(null)
        assertEquals("no_provider", state.uiState.value.availability)
        state.send("hello")
        assertTrue(state.uiState.value.messages.isEmpty())
    }
}
