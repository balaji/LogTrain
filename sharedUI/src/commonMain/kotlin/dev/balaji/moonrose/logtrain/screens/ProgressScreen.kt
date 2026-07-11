package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.ProgressScreenState
import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

/** Progress tab: deterministic insight cards plus body-weight logging. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onOpenProfile: () -> Unit) {
    val state = remember { ProgressScreenState() }

    if (LocalUseNativeNavigation.current) {
        ProgressContent(state, Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()))
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.tab_progress)) },
                    navigationIcon = { ProfileTopBarButton(onClick = onOpenProfile) },
                )
            },
        ) { padding ->
            ProgressContent(state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ProgressContent(state: ProgressScreenState, modifier: Modifier = Modifier) {
    val uiState by state.uiState.collectAsState()
    var showLogWeight by remember { mutableStateOf(false) }

    if (uiState.loading) return

    val unitLabel = weightUnitLabel(uiState.weightUnit)
    if (uiState.cards.isEmpty()) {
        EmptyProgress(onLogWeight = { showLogWeight = true }, modifier)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "log_weight") {
                LogWeightRow(
                    latest = uiState.latestBodyWeight,
                    unitLabel = unitLabel,
                    onLogWeight = { showLogWeight = true },
                )
            }
            items(count = uiState.cards.size, key = { uiState.cards[it].id }) { index ->
                InsightCardRow(uiState.cards[index], unitLabel)
            }
        }
    }

    if (showLogWeight) {
        LogWeightDialog(
            unitLabel = unitLabel,
            onConfirm = { text ->
                state.logWeight(text)
                showLogWeight = false
            },
            onDismiss = { showLogWeight = false },
        )
    }
}

@Composable
private fun EmptyProgress(onLogWeight: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(Res.string.no_progress_yet), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.progress_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onLogWeight, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(Res.string.log_body_weight))
        }
    }
}

@Composable
private fun LogWeightRow(latest: String, unitLabel: String, onLogWeight: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.body_weight), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (latest.isEmpty()) stringResource(Res.string.not_logged_yet) else "$latest $unitLabel",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        OutlinedButton(onClick = onLogWeight) { Text(stringResource(Res.string.log_weight)) }
    }
}

@Composable
private fun InsightCardRow(card: InsightCard, unitLabel: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    insightCardTitle(card),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TrendIcon(card.trend)
            }
            Text(insightHeadline(card, unitLabel), style = MaterialTheme.typography.titleLarge)
            Text(
                insightDetail(card, unitLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (card.spark.size >= 2) {
                Sparkline(
                    card.spark,
                    Modifier.fillMaxWidth().height(40.dp).padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun TrendIcon(trend: String) {
    val (icon, tint, label) = when (trend) {
        "up" -> Triple(Icons.AutoMirrored.Filled.TrendingUp, MaterialTheme.colorScheme.primary, stringResource(Res.string.trend_up))
        "down" -> Triple(Icons.AutoMirrored.Filled.TrendingDown, MaterialTheme.colorScheme.tertiary, stringResource(Res.string.trend_down))
        "flat" -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(Res.string.trend_flat))
        else -> return
    }
    Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = tint)
}

@Composable
private fun Sparkline(points: List<SparkPoint>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val minX = points.minOf { it.epochDay }
        val maxX = points.maxOf { it.epochDay }
        val minY = points.minOf { it.value }
        val maxY = points.maxOf { it.value }
        val spanX = (maxX - minX).coerceAtLeast(1).toFloat()
        val spanY = (maxY - minY).toFloat().coerceAtLeast(1e-3f)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = (p.epochDay - minX) / spanX * size.width
            val y = size.height - (p.value - minY).toFloat() / spanY * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        points.lastOrNull()?.let { p ->
            val x = (p.epochDay - minX) / spanX * size.width
            val y = size.height - (p.value - minY).toFloat() / spanY * size.height
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun LogWeightDialog(
    unitLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.log_body_weight)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() || it == '.' } },
                label = { Text(stringResource(Res.string.weight)) },
                suffix = { Text(unitLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(
                enabled = (text.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = { onConfirm(text) },
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
