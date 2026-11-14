package dev.balaji.moonrose.trainfree.chat

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Scroll command emitted by [ChatAutoScrollController] for a Compose or SwiftUI
 * chat message list. Primitive-typed for clean SKIE interop on iOS.
 */
data class ChatScrollDecision(
    val action: String,
    val scheduleDelayMillis: Long = 0L,
    val showScrollToBottomBadge: Boolean = false,
    val isPinnedToBottom: Boolean = true,
) {
    companion object {
        const val ACTION_NONE = "none"
        const val ACTION_SCROLL_SMOOTH = "scroll_smooth"
        const val ACTION_SCROLL_SETTLE = "scroll_settle"
        const val ACTION_CANCEL = "cancel"
    }
}

/**
 * Platform-agnostic state machine driving chat conversation auto-scrolling
 * across both Android (Compose) and iOS (SwiftUI).
 *
 * Keeps the list pinned to the bottom with throttled smooth scrolls while a
 * response streams, yields immediately on user touch or upward scroll, surfaces
 * a floating "Scroll to bottom" indicator while unpinned, resumes pinning when
 * the user reaches the bottom manually or taps the indicator, and emits a
 * drift-free settle command once generation finishes.
 */
class ChatAutoScrollController(
    val throttleIntervalMillis: Long,
) {
    constructor() : this(DEFAULT_THROTTLE_INTERVAL_MS)

    var isPinnedToBottom: Boolean = true
        private set
    var isStreaming: Boolean = false
        private set
    var isAtBottom: Boolean = true
        private set

    val showScrollToBottomBadge: Boolean
        get() = !isPinnedToBottom && (isStreaming || !isAtBottom)

    private var wasStreaming: Boolean = false
    private var isUserTouching: Boolean = false
    private var hasPendingScroll: Boolean = false
    private var seenFirstTokenInCurrentStream: Boolean = false
    private var lastSmoothScrollMillis: Long = Long.MIN_VALUE / 4
    private var lastMessageId: String? = null
    private var lastMessageTextLen: Int = 0
    private var lastMessageStatus: String = ""
    private var lastMessageSourcesCount: Int = 0

    fun onMessagesUpdated(
        messages: List<ChatMessage>,
        isGenerating: Boolean,
    ): ChatScrollDecision = onMessagesUpdated(messages, isGenerating, currentTimeMillis())

    fun onMessagesUpdated(
        messages: List<ChatMessage>,
        isGenerating: Boolean,
        nowMillis: Long,
    ): ChatScrollDecision {
        if (messages.isEmpty()) {
            reset()
            return decision(ChatScrollDecision.ACTION_NONE)
        }

        val last = messages.last()
        val currentlyStreaming = isGenerating || last.status == ChatMessage.STATUS_STREAMING

        if (
            last.id == lastMessageId &&
            last.text.length == lastMessageTextLen &&
            last.status == lastMessageStatus &&
            last.sources.size == lastMessageSourcesCount &&
            currentlyStreaming == wasStreaming
        ) {
            return decision(ChatScrollDecision.ACTION_NONE)
        }

        // Stream or turn starts (new tail message or an idle message entering streaming).
        if (last.id != lastMessageId || (!wasStreaming && currentlyStreaming)) {
            isPinnedToBottom = true
            isAtBottom = true
            isUserTouching = false
            hasPendingScroll = false
            seenFirstTokenInCurrentStream = currentlyStreaming && last.text.isNotEmpty()
            recordLast(last, currentlyStreaming)

            return if (currentlyStreaming) {
                if (last.text.isNotEmpty()) {
                    lastSmoothScrollMillis = nowMillis
                }
                decision(ChatScrollDecision.ACTION_SCROLL_SMOOTH)
            } else {
                decision(ChatScrollDecision.ACTION_SCROLL_SETTLE)
            }
        }

        // Stream in progress.
        if (currentlyStreaming) {
            if (last.text.isEmpty()) {
                seenFirstTokenInCurrentStream = false
                recordLast(last, true)
                return decision(ChatScrollDecision.ACTION_NONE)
            }

            if (!seenFirstTokenInCurrentStream && last.text.isNotEmpty()) {
                seenFirstTokenInCurrentStream = true
                recordLast(last, true)
                return if (isPinnedToBottom && !isUserTouching) {
                    lastSmoothScrollMillis = nowMillis
                    hasPendingScroll = false
                    decision(ChatScrollDecision.ACTION_SCROLL_SMOOTH)
                } else {
                    decision(ChatScrollDecision.ACTION_NONE)
                }
            }

            if (last.text.length != lastMessageTextLen) {
                recordLast(last, true)
                if (!isPinnedToBottom || isUserTouching) {
                    hasPendingScroll = false
                    return decision(ChatScrollDecision.ACTION_NONE)
                }
                val elapsed = (nowMillis - lastSmoothScrollMillis).coerceAtLeast(0L)
                return if (elapsed >= throttleIntervalMillis) {
                    lastSmoothScrollMillis = nowMillis
                    hasPendingScroll = false
                    decision(ChatScrollDecision.ACTION_SCROLL_SMOOTH)
                } else {
                    hasPendingScroll = true
                    val remaining = (throttleIntervalMillis - elapsed).coerceAtLeast(1L)
                    decision(ChatScrollDecision.ACTION_NONE, scheduleDelayMillis = remaining)
                }
            }

            recordLast(last, true)
            return decision(ChatScrollDecision.ACTION_NONE)
        }

        // Stream finishes.
        if (wasStreaming && !currentlyStreaming) {
            recordLast(last, false)
            seenFirstTokenInCurrentStream = false
            hasPendingScroll = false
            return if (isPinnedToBottom && !isUserTouching) {
                isAtBottom = true
                decision(ChatScrollDecision.ACTION_SCROLL_SETTLE)
            } else {
                decision(ChatScrollDecision.ACTION_NONE)
            }
        }

        // Non-streaming update (for example, source chips added after completion).
        recordLast(last, false)
        return decision(
            if (isPinnedToBottom) {
                ChatScrollDecision.ACTION_SCROLL_SETTLE
            } else {
                ChatScrollDecision.ACTION_NONE
            },
        )
    }

    fun onThrottleTimerFired(): ChatScrollDecision = onThrottleTimerFired(currentTimeMillis())

    fun onThrottleTimerFired(nowMillis: Long): ChatScrollDecision {
        if (!hasPendingScroll || !isPinnedToBottom || isUserTouching || !isStreaming) {
            hasPendingScroll = false
            return decision(ChatScrollDecision.ACTION_NONE)
        }
        hasPendingScroll = false
        lastSmoothScrollMillis = nowMillis
        return decision(ChatScrollDecision.ACTION_SCROLL_SMOOTH)
    }

    fun onUserTouchDown(): ChatScrollDecision {
        isUserTouching = true
        hasPendingScroll = false
        return if (isStreaming) {
            isPinnedToBottom = false
            decision(ChatScrollDecision.ACTION_CANCEL)
        } else {
            decision(ChatScrollDecision.ACTION_NONE)
        }
    }

    fun onUserTouchUp(): ChatScrollDecision {
        isUserTouching = false
        return decision(ChatScrollDecision.ACTION_NONE)
    }

    fun onUserScrolledUp(): ChatScrollDecision {
        isPinnedToBottom = false
        isAtBottom = false
        hasPendingScroll = false
        return decision(ChatScrollDecision.ACTION_CANCEL)
    }

    fun onUserReachedBottom(): ChatScrollDecision = onUserReachedBottom(currentTimeMillis())

    fun onUserReachedBottom(nowMillis: Long): ChatScrollDecision {
        val wasUnpinned = !isPinnedToBottom
        isPinnedToBottom = true
        isAtBottom = true
        hasPendingScroll = false
        return if (isStreaming && wasUnpinned && !isUserTouching) {
            lastSmoothScrollMillis = nowMillis
            decision(ChatScrollDecision.ACTION_SCROLL_SMOOTH)
        } else {
            decision(ChatScrollDecision.ACTION_NONE)
        }
    }

    fun onScrollToBottomTapped(): ChatScrollDecision = onScrollToBottomTapped(currentTimeMillis())

    fun onScrollToBottomTapped(nowMillis: Long): ChatScrollDecision {
        isUserTouching = false
        isPinnedToBottom = true
        isAtBottom = true
        hasPendingScroll = false
        lastSmoothScrollMillis = nowMillis
        return decision(ChatScrollDecision.ACTION_SCROLL_SMOOTH)
    }

    private fun recordLast(last: ChatMessage, streaming: Boolean) {
        lastMessageId = last.id
        lastMessageTextLen = last.text.length
        lastMessageStatus = last.status
        lastMessageSourcesCount = last.sources.size
        wasStreaming = streaming
        isStreaming = streaming
    }

    private fun reset() {
        isPinnedToBottom = true
        isStreaming = false
        isAtBottom = true
        wasStreaming = false
        isUserTouching = false
        hasPendingScroll = false
        seenFirstTokenInCurrentStream = false
        lastSmoothScrollMillis = Long.MIN_VALUE / 4
        lastMessageId = null
        lastMessageTextLen = 0
        lastMessageStatus = ""
        lastMessageSourcesCount = 0
    }

    private fun decision(
        action: String,
        scheduleDelayMillis: Long = 0L,
    ) = ChatScrollDecision(
        action = action,
        scheduleDelayMillis = scheduleDelayMillis,
        showScrollToBottomBadge = showScrollToBottomBadge,
        isPinnedToBottom = isPinnedToBottom,
    )

    companion object {
        const val DEFAULT_THROTTLE_INTERVAL_MS = 90L
        const val BOTTOM_THRESHOLD_DP = 24

        @OptIn(ExperimentalTime::class)
        private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
