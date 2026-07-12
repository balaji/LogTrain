package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*
import kotlin.math.abs

/** Feet the height wheels offer; inches always span 0–11. */
internal val HEIGHT_FEET_RANGE = 3..8

/**
 * A scrolling wheel/drum selector: the item nearest the vertical center is the
 * selection, reported via [onSelectedIndex] as the drum settles. This is the
 * Compose stand-in for iOS's `.wheel` Picker, used by the BMI calculator's
 * feet/inches height input.
 */
@Composable
internal fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 3,
    itemHeight: Dp = 40.dp,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    // The centered item = the one whose midpoint is closest to the viewport's.
    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                selectedIndex
            } else {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2f) - center) }!!.index
            }
        }
    }
    LaunchedEffect(centerIndex) { onSelectedIndex(centerIndex) }

    Box(modifier.height(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        // Selection guides bracket the center slot; drawn under the list so
        // the numbers scroll over them and the list keeps the scroll gesture.
        Box(Modifier.fillMaxWidth().height(itemHeight)) {
            HorizontalDivider(Modifier.align(Alignment.TopCenter))
            HorizontalDivider(Modifier.align(Alignment.BottomCenter))
        }
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            // Half a wheel of padding lets the first and last items reach center.
            contentPadding = PaddingValues(vertical = itemHeight * ((visibleCount - 1) / 2)),
        ) {
            itemsIndexed(items) { i, label ->
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(if (i == centerIndex) 1f else 0.35f),
                    )
                }
            }
        }
    }
}

/**
 * Height entry in feet/inches: a read-only field showing the current value
 * whose tap toggles a pair of ft/in wheels below it. The wheels start hidden;
 * callers collapse them (via [onExpandedChange]) when another field takes
 * focus. Used by the profile form and the BMI calculator in imperial mode.
 */
@Composable
internal fun FeetInchesField(
    label: String,
    feet: Int,
    inches: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onChange: (feet: Int, inches: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A read-only text field ignores taps, so presses are watched through the
    // interaction source instead — the standard fake-dropdown trick. The
    // collector runs for the field's whole lifetime, so it must read the
    // *current* expanded value, not the one captured at first composition.
    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is PressInteraction.Release) currentOnExpandedChange(!currentExpanded)
        }
    }
    Column(modifier) {
        OutlinedTextField(
            value = stringResource(Res.string.height_ft_in_value, feet.toString(), inches.toString()),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
        )
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LabeledWheel(
                    label = stringResource(Res.string.unit_ft),
                    items = HEIGHT_FEET_RANGE.map { it.toString() },
                    selectedIndex = (feet - HEIGHT_FEET_RANGE.first).coerceIn(0, HEIGHT_FEET_RANGE.last - HEIGHT_FEET_RANGE.first),
                    onSelectedIndex = { onChange(HEIGHT_FEET_RANGE.first + it, inches) },
                    modifier = Modifier.weight(1f),
                )
                LabeledWheel(
                    label = stringResource(Res.string.unit_in),
                    items = (0..11).map { it.toString() },
                    selectedIndex = inches.coerceIn(0, 11),
                    onSelectedIndex = { onChange(feet, it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LabeledWheel(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WheelPicker(
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndex = onSelectedIndex,
            modifier = Modifier.weight(1f),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
