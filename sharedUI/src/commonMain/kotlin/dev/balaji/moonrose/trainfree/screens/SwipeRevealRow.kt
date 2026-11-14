package dev.balaji.moonrose.trainfree.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal data class SwipeRevealAction(
    val icon: ImageVector,
    val contentDescription: String,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit,
)

private enum class SwipeRevealAnchor { Closed, Open }

/**
 * A list row that swipes left to reveal trailing action buttons. Tapping the
 * row while open closes it; tapping while closed invokes [onClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwipeRevealRow(
    actions: List<SwipeRevealAction>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    trailingContent: (@Composable () -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionWidth = 72.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    val anchors = remember(actionWidthPx, actions.size) {
        DraggableAnchors {
            SwipeRevealAnchor.Closed at 0f
            SwipeRevealAnchor.Open at -actionWidthPx * actions.size
        }
    }
    val swipeState = remember {
        AnchoredDraggableState(initialValue = SwipeRevealAnchor.Closed, anchors = anchors)
    }
    LaunchedEffect(anchors) { swipeState.updateAnchors(anchors) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
        ) {
            actions.forEach { action ->
                IconButton(
                    onClick = {
                        scope.launch { swipeState.animateTo(SwipeRevealAnchor.Closed) }
                        action.onClick()
                    },
                    modifier = Modifier
                        .width(actionWidth)
                        .fillMaxHeight()
                        .background(action.containerColor),
                ) {
                    Icon(
                        action.icon,
                        contentDescription = action.contentDescription,
                        tint = action.contentColor,
                    )
                }
            }
        }
        ListItem(
            headlineContent = headlineContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(containerColor = containerColor),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeState.requireOffset().roundToInt(), 0) }
                .anchoredDraggable(swipeState, Orientation.Horizontal)
                .background(containerColor)
                .clickable {
                    if (swipeState.currentValue == SwipeRevealAnchor.Open) {
                        scope.launch { swipeState.animateTo(SwipeRevealAnchor.Closed) }
                    } else {
                        onClick()
                    }
                },
        )
    }
}
