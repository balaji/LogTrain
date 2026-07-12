package dev.balaji.moonrose.logtrain.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.RestTimerController
import dev.balaji.moonrose.logtrain.db.RestTimerUiState
import dev.balaji.moonrose.logtrain.sharedui.resources.Res
import dev.balaji.moonrose.logtrain.sharedui.resources.rest_timer_minus_10
import dev.balaji.moonrose.logtrain.sharedui.resources.rest_timer_plus_10
import dev.balaji.moonrose.logtrain.sharedui.resources.rest_timer_skip
import org.jetbrains.compose.resources.stringResource

/**
 * The rest countdown shown under the set that started it: remaining time and
 * a bar draining once a second. Tapping the row reveals −10s / Skip / +10s
 * controls that act on the running [RestTimerController].
 */
@Composable
internal fun RestTimerRow(state: RestTimerUiState, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (state.totalSeconds > 0) {
            state.remainingSeconds.toFloat() / state.totalSeconds
        } else {
            0f
        },
        // One linear second per tick, so the bar drains continuously instead
        // of stepping.
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .animateContentSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatElapsed(state.remainingSeconds.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = { RestTimerController.adjust(-10) }) {
                        Text(stringResource(Res.string.rest_timer_minus_10))
                    }
                    TextButton(onClick = { RestTimerController.cancel() }) {
                        Text(stringResource(Res.string.rest_timer_skip))
                    }
                    TextButton(onClick = { RestTimerController.adjust(10) }) {
                        Text(stringResource(Res.string.rest_timer_plus_10))
                    }
                }
            }
        }
    }
}
