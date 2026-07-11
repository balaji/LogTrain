package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.llm.CardNarrationRequest
import dev.balaji.moonrose.logtrain.insights.llm.InsightGenerationProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Captures generate callbacks so tests control completion order; an
 * Unconfined scope makes every hand-off run synchronously on the test thread,
 * mirroring the main-thread contract of the real provider.
 */
private class FakeProvider : InsightGenerationProvider {
    var availability = "available"
    var prewarms = 0
    val calls = mutableListOf<CallRecord>()

    class CallRecord(
        val instructions: String,
        val prompt: String,
        val onComplete: (String) -> Unit,
        val onError: (String) -> Unit,
    ) {
        var finished = false
    }

    override fun availability() = availability
    override fun prewarm() { prewarms++ }
    override fun generate(
        instructions: String,
        prompt: String,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        calls += CallRecord(instructions, prompt, onComplete, onError)
    }

    /** Completes the oldest unfinished call. */
    fun complete(text: String) = finish { it.onComplete(text) }
    fun fail(message: String) = finish { it.onError(message) }

    private fun finish(action: (CallRecord) -> Unit) {
        val call = calls.first { !it.finished }
        call.finished = true
        action(call)
    }
}

class InsightNarrationStateTest {
    private val provider = FakeProvider()
    private val cardsFlow = MutableStateFlow<List<InsightCard>>(emptyList())
    private val cache = mutableMapOf<String, AiInsightCacheEntity>()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun card(id: String, stats: String) = InsightCard(
        id = id, kind = "volume", exerciseName = null,
        headlineKey = "", headlineArgs = emptyList(),
        detailKey = stats, detailArgs = emptyList(),
        trend = "none", spark = emptyList(),
    )

    /** The request derives entirely from the card, with detailKey standing in for the stats. */
    private fun requestFor(card: InsightCard) = CardNarrationRequest(
        cardId = card.id,
        instructions = "inst:${card.kind}",
        prompt = "prompt:${card.detailKey}",
        statsHash = "hash:${card.detailKey}",
    )

    private fun state(provider: InsightGenerationProvider? = this.provider) = InsightNarrationState(
        provider = provider,
        cards = cardsFlow,
        buildRequest = { requestFor(it) },
        cacheFind = { cache[it] },
        cachePut = { cache[it.cardId] = it },
        scope = scope,
    )

    @Test
    fun `cache hit serves ready text immediately without generating`() {
        cache["volume"] = AiInsightCacheEntity("volume", "hash:v1", "Cached insight.", 0)
        val state = state()
        cardsFlow.value = listOf(card("volume", "v1"))

        val narration = state.narrations.value.getValue("volume")
        assertEquals("ready", narration.status)
        assertEquals("Cached insight.", narration.text)
        assertTrue(provider.calls.isEmpty())
        assertEquals(0, provider.prewarms)
    }

    @Test
    fun `cache miss shows loading then persists and flips to ready`() {
        val state = state()
        cardsFlow.value = listOf(card("volume", "v1"))

        assertEquals("loading", state.narrations.value.getValue("volume").status)
        assertEquals(1, provider.prewarms)
        assertEquals("inst:volume", provider.calls.single().instructions)
        assertEquals("prompt:v1", provider.calls.single().prompt)

        provider.complete("  Fresh insight.  ")
        val narration = state.narrations.value.getValue("volume")
        assertEquals("ready", narration.status)
        assertEquals("Fresh insight.", narration.text)
        assertEquals("Fresh insight.", cache.getValue("volume").text)
        assertEquals("hash:v1", cache.getValue("volume").statsHash)
    }

    @Test
    fun `generations run one at a time and re-emissions do not re-enqueue`() {
        val state = state()
        cardsFlow.value = listOf(card("a", "v1"), card("b", "v1"))
        assertEquals(1, provider.calls.size)

        // Same stats re-emitted while generating: no duplicate work.
        cardsFlow.value = listOf(card("a", "v1"), card("b", "v1"))
        assertEquals(1, provider.calls.size)

        provider.complete("A text")
        assertEquals(2, provider.calls.size)
        provider.complete("B text")
        assertEquals("A text", state.narrations.value.getValue("a").text)
        assertEquals("B text", state.narrations.value.getValue("b").text)
    }

    @Test
    fun `changed stats regenerate and a superseded queued request is skipped`() {
        val state = state()
        cardsFlow.value = listOf(card("a", "v1"), card("b", "v1"))

        // While a:v1 generates, b's stats change: b:v1 sits queued but stale.
        cardsFlow.value = listOf(card("a", "v1"), card("b", "v2"))
        assertEquals("loading", state.narrations.value.getValue("b").status)

        provider.complete("A text")
        // b:v1 was skipped; the live call is b:v2.
        assertEquals(2, provider.calls.size)
        assertEquals("prompt:v2", provider.calls.last().prompt)

        provider.complete("B text")
        assertEquals("B text", state.narrations.value.getValue("b").text)
        assertEquals("hash:v2", cache.getValue("b").statsHash)
    }

    @Test
    fun `stale completion is cached but not surfaced when newer stats are queued`() {
        val state = state()
        cardsFlow.value = listOf(card("a", "v1"))
        cardsFlow.value = listOf(card("a", "v2"))

        provider.complete("Old text")
        assertEquals("loading", state.narrations.value.getValue("a").status)
        assertEquals("hash:v1", cache.getValue("a").statsHash)

        provider.complete("New text")
        assertEquals("New text", state.narrations.value.getValue("a").text)
        assertEquals("hash:v2", cache.getValue("a").statsHash)
    }

    @Test
    fun `errors mark the card failed and only changed stats retry`() {
        val state = state()
        cardsFlow.value = listOf(card("volume", "v1"))
        provider.fail("boom")
        assertEquals("failed", state.narrations.value.getValue("volume").status)
        assertNull(cache["volume"])

        // Re-emissions with identical stats stay quietly failed instead of
        // pulsing the loading placeholder on every edit. (StateFlow dedupes
        // equal lists, so force a fresh emission.)
        cardsFlow.value = emptyList()
        cardsFlow.value = listOf(card("volume", "v1"))
        assertEquals("failed", state.narrations.value.getValue("volume").status)
        assertEquals(1, provider.calls.size)

        // Changed stats are a new request and retry.
        cardsFlow.value = listOf(card("volume", "v2"))
        assertEquals("loading", state.narrations.value.getValue("volume").status)
        assertEquals(2, provider.calls.size)
        provider.complete("Recovered.")
        assertEquals("ready", state.narrations.value.getValue("volume").status)
    }

    @Test
    fun `blank output counts as a failure`() {
        val state = state()
        cardsFlow.value = listOf(card("volume", "v1"))
        provider.complete("   ")
        assertEquals("failed", state.narrations.value.getValue("volume").status)
        assertNull(cache["volume"])
    }

    @Test
    fun `cards that disappear drop out of the map`() {
        cache["volume"] = AiInsightCacheEntity("volume", "hash:v1", "Cached.", 0)
        val state = state()
        cardsFlow.value = listOf(card("volume", "v1"))
        assertEquals(setOf("volume"), state.narrations.value.keys)

        cardsFlow.value = emptyList()
        assertTrue(state.narrations.value.isEmpty())
    }

    @Test
    fun `null or unavailable provider keeps the map empty and inert`() {
        val state = state(provider = null)
        cardsFlow.value = listOf(card("volume", "v1"))
        assertTrue(state.narrations.value.isEmpty())

        provider.availability = "device_not_eligible"
        val gated = state()
        cardsFlow.value = listOf(card("volume", "v2"))
        assertTrue(gated.narrations.value.isEmpty())
        assertTrue(provider.calls.isEmpty())
    }
}
