package dev.balaji.moonrose.trainfree.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.trainfree.chat.ChatAutoScrollController
import dev.balaji.moonrose.trainfree.chat.ChatMessage
import dev.balaji.moonrose.trainfree.chat.ChatScrollDecision
import dev.balaji.moonrose.trainfree.chat.ChatSourceRef
import dev.balaji.moonrose.trainfree.chat.ChatState
import dev.balaji.moonrose.trainfree.chat.ChatUiState
import dev.balaji.moonrose.trainfree.sharedui.resources.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Modal bottom sheet presenting the on-device AI coach backed by [ChatState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSheet(
    chatState: ChatState,
    onDismiss: () -> Unit,
    onSourceClick: (ChatSourceRef) -> Unit,
) {
    val uiState by chatState.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inputText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = {
            chatState.cancelGeneration()
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            ChatHeader(
                hasMessages = uiState.messages.isNotEmpty(),
                onNewChat = chatState::clearConversation,
                onDismiss = {
                    chatState.cancelGeneration()
                    onDismiss()
                },
            )
            HorizontalDivider()

            val availabilityNote = chatAvailabilityMessage(uiState.availability)
            if (availabilityNote != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = availabilityNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (uiState.messages.isEmpty()) {
                    ChatEmptyState(
                        uiState = uiState,
                        enabled = uiState.availability == "available",
                        onPromptClick = { promptText -> chatState.sendMessage(promptText) },
                    )
                } else {
                    ChatMessageList(
                        messages = uiState.messages,
                        isGenerating = uiState.isGenerating,
                        onRetry = chatState::retryLast,
                        onSourceClick = onSourceClick,
                    )
                }
            }

            HorizontalDivider()
            ChatComposer(
                text = inputText,
                onTextChange = { inputText = it },
                isGenerating = uiState.isGenerating,
                enabled = uiState.availability == "available",
                onSend = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty()) {
                        inputText = ""
                        chatState.sendMessage(trimmed)
                    }
                },
                onStop = chatState::cancelGeneration,
            )
        }
    }
}

@Composable
private fun ChatHeader(
    hasMessages: Boolean,
    onNewChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(Res.string.chat_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        if (hasMessages) {
            TextButton(onClick = onNewChat) {
                Text(stringResource(Res.string.chat_new_chat))
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.close),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmptyState(
    uiState: ChatUiState,
    enabled: Boolean,
    onPromptClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.chat_on_device_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.suggestedPrompts.forEach { suggestion ->
                val label = chatSuggestedPromptLabel(suggestion)
                SuggestionChip(
                    onClick = { onPromptClick(label) },
                    enabled = enabled,
                    label = { Text(label) },
                )
            }
        }
    }
}

private const val BOTTOM_ANCHOR_KEY = "__chat_bottom_anchor__"

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onRetry: () -> Unit,
    onSourceClick: (ChatSourceRef) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bottomThresholdPx = remember(density) {
        with(density) { ChatAutoScrollController.BOTTOM_THRESHOLD_DP.dp.roundToPx() }
    }
    val controller = remember { ChatAutoScrollController() }
    var showScrollToBottomBadge by remember { mutableStateOf(false) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var throttleJob by remember { mutableStateOf<Job?>(null) }
    var lastUserScrollDirectionDown by remember { mutableStateOf(false) }
    val currentBottomAnchorIndex by rememberUpdatedState(messages.size)

    fun applyDecision(decision: ChatScrollDecision) {
        if (showScrollToBottomBadge != decision.showScrollToBottomBadge) {
            showScrollToBottomBadge = decision.showScrollToBottomBadge
        }
        when (decision.action) {
            ChatScrollDecision.ACTION_CANCEL -> {
                throttleJob?.cancel()
                throttleJob = null
                scrollJob?.cancel()
                scrollJob = null
            }
            ChatScrollDecision.ACTION_SCROLL_SMOOTH -> {
                throttleJob?.cancel()
                throttleJob = null
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    scrollSmoothToBottom(listState, currentBottomAnchorIndex)
                }
            }
            ChatScrollDecision.ACTION_SCROLL_SETTLE -> {
                throttleJob?.cancel()
                throttleJob = null
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    settleAtBottom(listState, currentBottomAnchorIndex)
                }
            }
        }
        if (decision.scheduleDelayMillis > 0L) {
            throttleJob?.cancel()
            throttleJob = coroutineScope.launch {
                delay(decision.scheduleDelayMillis)
                applyDecision(controller.onThrottleTimerFired())
            }
        }
    }

    fun onScrollDirection(deltaY: Float, thresholdPx: Float): Boolean {
        if (
            deltaY > thresholdPx &&
            (listState.canScrollBackward || !isAtBottom(listState, bottomThresholdPx))
        ) {
            if (lastUserScrollDirectionDown) {
                lastUserScrollDirectionDown = false
            }
            if (controller.isPinnedToBottom || controller.isAtBottom) {
                applyDecision(controller.onUserScrolledUp())
            }
            return true
        } else if (deltaY < -thresholdPx && !lastUserScrollDirectionDown) {
            lastUserScrollDirectionDown = true
        }
        return false
    }

    val lastMessage = messages.lastOrNull()
    LaunchedEffect(
        messages.size,
        lastMessage?.id,
        lastMessage?.text?.length ?: 0,
        lastMessage?.status,
        lastMessage?.sources?.size ?: 0,
        isGenerating,
    ) {
        applyDecision(controller.onMessagesUpdated(messages, isGenerating))
    }

    LaunchedEffect(listState, bottomThresholdPx) {
        snapshotFlow {
            isAtBottom(listState, bottomThresholdPx) && lastUserScrollDirectionDown
        }
            .distinctUntilChanged()
            .collect { reachedViaDownScroll ->
                if (reachedViaDownScroll && !controller.isPinnedToBottom) {
                    applyDecision(controller.onUserReachedBottom())
                }
            }
    }

    val nestedScrollConnection = remember(listState, bottomThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    onScrollDirection(available.y, thresholdPx = 0f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                onScrollDirection(available.y, thresholdPx = 0f)
                return Velocity.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .pointerInput(listState, bottomThresholdPx) {
                    awaitPointerEventScope {
                        var draggedUp = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            when (event.type) {
                                PointerEventType.Press -> {
                                    draggedUp = false
                                    lastUserScrollDirectionDown = false
                                    applyDecision(controller.onUserTouchDown())
                                }
                                PointerEventType.Move -> {
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        val dy = change.position.y - change.previousPosition.y
                                        if (onScrollDirection(dy, thresholdPx = 1f)) {
                                            draggedUp = true
                                        }
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (event.changes.none { it.pressed }) {
                                        applyDecision(controller.onUserTouchUp())
                                        if (
                                            !draggedUp &&
                                            !controller.isPinnedToBottom &&
                                            isAtBottom(listState, bottomThresholdPx)
                                        ) {
                                            applyDecision(controller.onUserReachedBottom())
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            contentPadding = PaddingValues(16.dp),
        ) {
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                ChatMessageBubble(
                    message = message,
                    onRetry = onRetry,
                    onSourceClick = onSourceClick,
                    modifier = Modifier.padding(bottom = if (index < messages.lastIndex) 12.dp else 0.dp),
                )
            }
            item(key = BOTTOM_ANCHOR_KEY) {
                Spacer(Modifier.height(1.dp))
            }
        }

        AnimatedVisibility(
            visible = showScrollToBottomBadge,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            ScrollToBottomBadge(
                onClick = {
                    lastUserScrollDirectionDown = false
                    applyDecision(controller.onScrollToBottomTapped())
                },
            )
        }
    }
}

@Composable
private fun ScrollToBottomBadge(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(Res.string.chat_scroll_to_bottom),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun isAtBottom(listState: LazyListState, bottomThresholdPx: Int): Boolean {
    val layoutInfo = listState.layoutInfo
    val totalCount = layoutInfo.totalItemsCount
    if (totalCount == 0) return true
    val anchorIndex = totalCount - 1
    val anchorItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == anchorIndex }
    val distanceToBottom = if (anchorItem != null) {
        (anchorItem.offset + anchorItem.size + layoutInfo.afterContentPadding) -
            layoutInfo.viewportEndOffset
    } else {
        Int.MAX_VALUE
    }
    return distanceToBottom <= bottomThresholdPx || !listState.canScrollForward
}

private suspend fun scrollSmoothToBottom(
    listState: LazyListState,
    bottomAnchorIndex: Int,
) {
    if (bottomAnchorIndex < 0) return
    val layoutInfo = listState.layoutInfo
    val visibleItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == bottomAnchorIndex }
        ?: layoutInfo.visibleItemsInfo.lastOrNull { it.index == bottomAnchorIndex - 1 }
    if (visibleItem != null) {
        val remaining = (visibleItem.offset + visibleItem.size + layoutInfo.afterContentPadding) -
            layoutInfo.viewportEndOffset
        if (remaining > 0) {
            listState.animateScrollBy(
                value = remaining.toFloat(),
                animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
            )
        }
    } else {
        listState.animateScrollToItem(bottomAnchorIndex)
    }
}

private suspend fun settleAtBottom(
    listState: LazyListState,
    bottomAnchorIndex: Int,
) {
    if (bottomAnchorIndex < 0) return
    withFrameNanos {}
    listState.scrollToItem(bottomAnchorIndex)
    val layoutInfo = listState.layoutInfo
    val visibleItem = layoutInfo.visibleItemsInfo.lastOrNull { it.index == bottomAnchorIndex }
        ?: layoutInfo.visibleItemsInfo.lastOrNull { it.index == bottomAnchorIndex - 1 }
    if (visibleItem != null) {
        val remaining = (visibleItem.offset + visibleItem.size + layoutInfo.afterContentPadding) -
            layoutInfo.viewportEndOffset
        if (remaining > 0) {
            listState.scrollBy(remaining.toFloat())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    onSourceClick: (ChatSourceRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == ChatMessage.ROLE_USER
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (message.status == ChatMessage.STATUS_STREAMING && message.text.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(Res.string.chat_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (message.text.isNotBlank()) {
                    SelectionContainer {
                        if (isUser) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            MarkdownText(
                                markdown = message.text,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (message.status == ChatMessage.STATUS_CANCELLED) {
                    Text(
                        text = stringResource(Res.string.chat_cancelled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (message.status == ChatMessage.STATUS_ERROR) {
                    Text(
                        text = chatErrorLabel(message.errorKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    ) {
                        Text(stringResource(Res.string.chat_retry))
                    }
                }
            }
        }

        if (!isUser && message.sources.isNotEmpty() && message.status == ChatMessage.STATUS_COMPLETE) {
            Spacer(Modifier.size(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                message.sources.forEach { source ->
                    AssistChip(
                        onClick = { onSourceClick(source) },
                        label = {
                            Text(
                                text = source.label,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            maxLines = 4,
            placeholder = { Text(stringResource(Res.string.chat_input_placeholder)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (!isGenerating && text.isNotBlank()) onSend()
                },
            ),
        )
        FilledIconButton(
            onClick = { if (isGenerating) onStop() else onSend() },
            enabled = isGenerating || (enabled && text.isNotBlank()),
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(
                    if (isGenerating) Res.string.chat_stop else Res.string.chat_send,
                ),
            )
        }
    }
}
