package dev.balaji.moonrose.logtrain.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.chat.ChatMessage
import dev.balaji.moonrose.logtrain.chat.ChatSourceRef
import dev.balaji.moonrose.logtrain.chat.ChatState
import dev.balaji.moonrose.logtrain.chat.ChatUiState
import dev.balaji.moonrose.logtrain.sharedui.resources.*
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

@Composable
private fun ChatMessageList(
    messages: List<ChatMessage>,
    onRetry: () -> Unit,
    onSourceClick: (ChatSourceRef) -> Unit,
) {
    val listState = rememberLazyListState()
    val lastTextLen = messages.lastOrNull()?.text?.length ?: 0

    LaunchedEffect(messages.size, lastTextLen) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            ChatMessageBubble(
                message = message,
                onRetry = onRetry,
                onSourceClick = onSourceClick,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onRetry: () -> Unit,
    onSourceClick: (ChatSourceRef) -> Unit,
) {
    val isUser = message.role == ChatMessage.ROLE_USER
    Column(
        modifier = Modifier.fillMaxWidth(),
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
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
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
