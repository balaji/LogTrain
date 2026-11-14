package dev.balaji.moonrose.logtrain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatAutoScrollTest {
    private fun userMsg(id: String = "u1", text: String = "How is my bench?") = ChatMessage(
        id = id,
        role = ChatMessage.ROLE_USER,
        text = text,
        status = ChatMessage.STATUS_COMPLETE,
    )

    private fun assistantMsg(
        id: String = "a1",
        text: String = "",
        status: String = ChatMessage.STATUS_STREAMING,
        sources: List<ChatSourceRef> = emptyList(),
    ) = ChatMessage(
        id = id,
        role = ChatMessage.ROLE_ASSISTANT,
        text = text,
        status = status,
        sources = sources,
    )

    @Test
    fun streamStartScrollsForPlaceholderAndImmediatelyForFirstTokens() {
        val controller = ChatAutoScrollController(throttleIntervalMillis = 90L)

        // Placeholder added on send.
        val startDecision = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "")),
            isGenerating = true,
            nowMillis = 1_000L,
        )
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, startDecision.action)
        assertEquals(0L, startDecision.scheduleDelayMillis)
        assertTrue(startDecision.isPinnedToBottom)
        assertFalse(startDecision.showScrollToBottomBadge)

        // First tokens arrive just 5 ms later — must scroll immediately without waiting 90 ms.
        val firstTokenDecision = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Your bench")),
            isGenerating = true,
            nowMillis = 1_005L,
        )
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, firstTokenDecision.action)
        assertEquals(0L, firstTokenDecision.scheduleDelayMillis)
        assertTrue(firstTokenDecision.isPinnedToBottom)
    }

    @Test
    fun passiveWatchingThrottlesRapidPartialUpdates() {
        val controller = ChatAutoScrollController(throttleIntervalMillis = 90L)

        controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "")),
            isGenerating = true,
            nowMillis = 1_000L,
        )
        // First tokens at t = 1_010.
        val first = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Hello")),
            isGenerating = true,
            nowMillis = 1_010L,
        )
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, first.action)

        // Rapid token at t = 1_030 -> throttled, schedules remaining 70 ms.
        val throttled = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Hello world")),
            isGenerating = true,
            nowMillis = 1_030L,
        )
        assertEquals(ChatScrollDecision.ACTION_NONE, throttled.action)
        assertEquals(70L, throttled.scheduleDelayMillis)

        // Timer fires at t = 1_100 -> emits smooth scroll.
        val timerFired = controller.onThrottleTimerFired(nowMillis = 1_100L)
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, timerFired.action)
        assertEquals(0L, timerFired.scheduleDelayMillis)

        // Another token after full interval has passed (t = 1_200) -> scrolls right away.
        val afterInterval = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Hello world! Next sentence.")),
            isGenerating = true,
            nowMillis = 1_200L,
        )
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, afterInterval.action)
        assertEquals(0L, afterInterval.scheduleDelayMillis)
    }

    @Test
    fun touchDownAndScrollUpCancelAutoScrollAndShowBadge() {
        val controller = ChatAutoScrollController(throttleIntervalMillis = 90L)

        controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Token 1")),
            isGenerating = true,
            nowMillis = 1_000L,
        )
        // Schedule a pending throttle scroll.
        val pending = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Token 1 2")),
            isGenerating = true,
            nowMillis = 1_020L,
        )
        assertEquals(70L, pending.scheduleDelayMillis)

        // User touches down while streaming -> stops auto-scroll immediately.
        val touchCancel = controller.onUserTouchDown()
        assertEquals(ChatScrollDecision.ACTION_CANCEL, touchCancel.action)
        assertFalse(touchCancel.isPinnedToBottom)
        assertTrue(touchCancel.showScrollToBottomBadge)

        // Pending throttle timer becomes a no-op.
        val cancelledTimer = controller.onThrottleTimerFired(nowMillis = 1_100L)
        assertEquals(ChatScrollDecision.ACTION_NONE, cancelledTimer.action)

        // User scrolls up.
        val scrollUp = controller.onUserScrolledUp()
        assertEquals(ChatScrollDecision.ACTION_CANCEL, scrollUp.action)
        assertFalse(scrollUp.isPinnedToBottom)
        assertTrue(scrollUp.showScrollToBottomBadge)

        controller.onUserTouchUp()

        // Subsequent tokens do not scroll and keep badge visible.
        val laterToken = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Token 1 2 3 4")),
            isGenerating = true,
            nowMillis = 1_500L,
        )
        assertEquals(ChatScrollDecision.ACTION_NONE, laterToken.action)
        assertEquals(0L, laterToken.scheduleDelayMillis)
        assertFalse(laterToken.isPinnedToBottom)
        assertTrue(laterToken.showScrollToBottomBadge)
    }

    @Test
    fun reachingBottomOrTappingBadgeResumesAutoScrollUntilStreamEnds() {
        val controller = ChatAutoScrollController(throttleIntervalMillis = 90L)

        controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Start")),
            isGenerating = true,
            nowMillis = 1_000L,
        )
        controller.onUserTouchDown()
        controller.onUserScrolledUp()
        controller.onUserTouchUp()
        assertTrue(controller.showScrollToBottomBadge)

        // Reaches bottom manually -> hides badge and resumes pinning.
        val reached = controller.onUserReachedBottom(nowMillis = 1_200L)
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, reached.action)
        assertTrue(reached.isPinnedToBottom)
        assertFalse(reached.showScrollToBottomBadge)

        // Subsequent tokens keep following.
        val nextToken = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Start more text")),
            isGenerating = true,
            nowMillis = 1_300L,
        )
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, nextToken.action)
        assertTrue(nextToken.isPinnedToBottom)

        // Scroll up again, then tap the floating badge.
        controller.onUserScrolledUp()
        assertTrue(controller.showScrollToBottomBadge)
        val tapped = controller.onScrollToBottomTapped(nowMillis = 1_400L)
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, tapped.action)
        assertTrue(tapped.isPinnedToBottom)
        assertFalse(tapped.showScrollToBottomBadge)

        // Tokens continue auto-scrolling until stream finishes.
        val streamed = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Start more text and end")),
            isGenerating = true,
            nowMillis = 1_500L,
        )
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, streamed.action)
    }

    @Test
    fun streamFinishSettlesAtBottomOnlyWhenPinned() {
        // Case 1: Pinned when stream completes.
        val pinnedController = ChatAutoScrollController(throttleIntervalMillis = 90L)
        pinnedController.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Working...")),
            isGenerating = true,
            nowMillis = 1_000L,
        )
        val finishPinned = pinnedController.onMessagesUpdated(
            messages = listOf(
                userMsg(),
                assistantMsg(
                    text = "Done!",
                    status = ChatMessage.STATUS_COMPLETE,
                    sources = listOf(ChatSourceRef("progress", "Overview")),
                ),
            ),
            isGenerating = false,
            nowMillis = 1_020L,
        )
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SETTLE, finishPinned.action)
        assertEquals(0L, finishPinned.scheduleDelayMillis)
        assertTrue(finishPinned.isPinnedToBottom)
        assertFalse(finishPinned.showScrollToBottomBadge)

        // Case 2: Scrolled up when stream completes -> must NOT yank back to bottom.
        val scrolledUpController = ChatAutoScrollController(throttleIntervalMillis = 90L)
        scrolledUpController.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Working...")),
            isGenerating = true,
            nowMillis = 2_000L,
        )
        scrolledUpController.onUserScrolledUp()
        val finishUnpinned = scrolledUpController.onMessagesUpdated(
            messages = listOf(
                userMsg(),
                assistantMsg(text = "Done!", status = ChatMessage.STATUS_COMPLETE),
            ),
            isGenerating = false,
            nowMillis = 2_200L,
        )
        assertEquals(ChatScrollDecision.ACTION_NONE, finishUnpinned.action)
        assertEquals(0L, finishUnpinned.scheduleDelayMillis)
        assertFalse(finishUnpinned.isPinnedToBottom)
        assertTrue(finishUnpinned.showScrollToBottomBadge)
    }

    @Test
    fun compactRetryResetRetriggersImmediateFirstTokenScroll() {
        val controller = ChatAutoScrollController(throttleIntervalMillis = 90L)

        controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Partial text")),
            isGenerating = true,
            nowMillis = 1_000L,
        )

        // Compact retry clears streaming text back to "".
        val resetDecision = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "")),
            isGenerating = true,
            nowMillis = 1_010L,
        )
        assertEquals(ChatScrollDecision.ACTION_NONE, resetDecision.action)

        // New first token arrives shortly after -> immediate smooth scroll (unthrottled).
        val retryFirstToken = controller.onMessagesUpdated(
            messages = listOf(userMsg(), assistantMsg(text = "Fresh start")),
            isGenerating = true,
            nowMillis = 1_020L,
        )
        assertEquals(ChatScrollDecision.ACTION_SCROLL_SMOOTH, retryFirstToken.action)
        assertEquals(0L, retryFirstToken.scheduleDelayMillis)
    }
}
