package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.llm.CardNarration
import dev.balaji.moonrose.logtrain.insights.llm.CardNarrationRequest
import dev.balaji.moonrose.logtrain.insights.llm.InsightCardNarrator
import dev.balaji.moonrose.logtrain.insights.llm.InsightGenerationProvider
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Per-card LLM narration for the Progress tab, keyed by InsightCard.id. The
 * platform UI injects its on-device InsightGenerationProvider (Foundation
 * Models on iOS) and joins narrations to cards by id; platforms without a
 * provider simply never construct this state. Provider callbacks are
 * contractually on the main thread, so state mutation needs no
 * synchronization.
 *
 * Generated text is cached in ai_insight_cache keyed by a hash of the
 * card's prompt (and thus its underlying numbers): unchanged stats serve
 * the cached paragraph instantly; changed stats enqueue a regeneration.
 * Generations run strictly sequentially — one on-device session at a time.
 */
class InsightNarrationState internal constructor(
    private val provider: InsightGenerationProvider?,
    private val cards: Flow<List<InsightCard>>,
    private val buildRequest: suspend (InsightCard) -> CardNarrationRequest?,
    private val cacheFind: suspend (String) -> AiInsightCacheEntity?,
    private val cachePut: suspend (AiInsightCacheEntity) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    constructor(provider: InsightGenerationProvider?) : this(
        provider = provider,
        cards = InsightsRepository.observeCards(),
        buildRequest = InsightCardNarrator::buildRequest,
        cacheFind = { AppDb.db.aiInsightCacheDao().find(it) },
        cachePut = { AppDb.db.aiInsightCacheDao().upsert(it) },
    )

    private val _narrations = MutableStateFlow<Map<String, CardNarration>>(emptyMap())
    val narrations: StateFlow<Map<String, CardNarration>> = _narrations.asStateFlow()
    private val queue = Channel<CardNarrationRequest>(Channel.UNLIMITED)

    /**
     * cardId -> stats hash last enqueued; dedupes re-emissions and marks
     * superseded work. A [FAILED_PREFIX]ed hash records a failed generation
     * so identical stats are not retried on every re-emission (each retry
     * pulses the loading placeholder in the UI); changed stats — or the next
     * app launch, since this map is in-memory — retry naturally.
     */
    private val enqueued = mutableMapOf<String, String>()
    private var prewarmed = false

    init {
        if (provider?.availability() == "available") {
            scope.launch { worker() }
            scope.launch { cards.collect { reconcile(it) } }
        }
    }

    private class PreparedCard(val request: CardNarrationRequest, val cached: AiInsightCacheEntity?)

    private suspend fun reconcile(cards: List<InsightCard>) {
        // Suspending fetches happen first; the decide phase below is
        // suspension-free so a completing generation can't interleave with
        // it and get clobbered by stale reads.
        val prepared = cards.mapNotNull { card ->
            val request = buildRequest(card) ?: return@mapNotNull null
            PreparedCard(request, cacheFind(card.id))
        }
        val next = mutableMapOf<String, CardNarration>()
        for (item in prepared) {
            val id = item.request.cardId
            if (item.cached?.statsHash == item.request.statsHash) {
                next[id] = CardNarration(id, "ready", item.cached.text)
            } else if (enqueued[id] == item.request.statsHash) {
                // Already queued or just finished for these exact stats; keep
                // whatever the worker last reported (loading or ready).
                next[id] = _narrations.value[id] ?: CardNarration(id, "loading", "")
            } else if (enqueued[id] == FAILED_PREFIX + item.request.statsHash) {
                next[id] = CardNarration(id, "failed", "")
            } else {
                enqueued[id] = item.request.statsHash
                next[id] = CardNarration(id, "loading", "")
                if (!prewarmed) {
                    prewarmed = true
                    provider?.prewarm()
                }
                queue.trySend(item.request)
            }
        }
        // Cards that disappeared drop out of the map here.
        _narrations.value = next
    }

    private suspend fun worker() {
        for (request in queue) {
            // A newer emission superseded this request while it sat queued.
            if (enqueued[request.cardId] != request.statsHash) continue
            val text = generate(request)?.trim()
            if (text.isNullOrEmpty()) {
                // Remember the failure for these exact stats (unless newer
                // stats were enqueued meanwhile) so re-emissions don't retry.
                if (enqueued[request.cardId] == request.statsHash) {
                    enqueued[request.cardId] = FAILED_PREFIX + request.statsHash
                }
                update(request.cardId, CardNarration(request.cardId, "failed", ""))
                continue
            }
            cachePut(AiInsightCacheEntity(request.cardId, request.statsHash, text, nowMillis()))
            // Only surface the text if these stats are still the latest.
            if (enqueued[request.cardId] == request.statsHash) {
                update(request.cardId, CardNarration(request.cardId, "ready", text))
            }
        }
    }

    private suspend fun generate(request: CardNarrationRequest): String? =
        suspendCancellableCoroutine { continuation ->
            provider?.generate(
                instructions = request.instructions,
                prompt = request.prompt,
                onComplete = { if (continuation.isActive) continuation.resume(it) },
                onError = { if (continuation.isActive) continuation.resume(null) },
            ) ?: continuation.resume(null)
        }

    /** Replaces the narration only while its card is still present. */
    private fun update(cardId: String, narration: CardNarration) {
        val current = _narrations.value
        if (cardId !in current) return
        _narrations.value = current + (cardId to narration)
    }

    private companion object {
        const val FAILED_PREFIX = "failed:"
    }
}
