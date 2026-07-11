package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.ProgressScreenState
import dev.balaji.moonrose.logtrain.insights.BodyPartSlice
import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.insights.SparkPoint
import dev.balaji.moonrose.logtrain.insights.SparkSeries
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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
            val lastProgressionIndex = uiState.cards.indexOfLast { it.kind in PROGRESSION_KINDS }
            uiState.cards.forEachIndexed { index, card ->
                item(key = card.id) {
                    if (card.kind in PROGRESSION_KINDS) {
                        val slot = card.id.substringAfter(PROGRESSION_ID_PREFIX).toIntOrNull() ?: 0
                        ProgressionCard(
                            card = card,
                            selected = uiState.progressionConfigs.getOrNull(slot)?.exercises
                                ?: card.series.map { it.name },
                            options = uiState.progressionOptions,
                            unitLabel = unitLabel,
                            canRemove = uiState.progressionConfigs.size > 1,
                            onSetExercises = { state.setProgressionCardExercises(slot, it) },
                            onToggleAxis = { state.toggleProgressionAxis(slot) },
                            onRemove = { state.removeProgressionCard(slot) },
                        )
                    } else {
                        InsightCardRow(card, unitLabel)
                    }
                }
                if (index == lastProgressionIndex && uiState.progressionConfigs.size < 3) {
                    val unused = uiState.progressionOptions.firstOrNull { option ->
                        uiState.progressionConfigs.none { option in it.exercises }
                    } ?: uiState.progressionOptions.firstOrNull()
                    if (unused != null) {
                        item(key = "add_progression") {
                            TextButton(onClick = { state.addProgressionCard(unused) }) {
                                Text(stringResource(Res.string.insight_add_comparison))
                            }
                        }
                    }
                }
            }
            if (uiState.bodyPartBreakdown.isNotEmpty()) {
                item(key = "body_parts") {
                    BodyPartBreakdownCard(uiState.bodyPartBreakdown)
                }
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
            if (card.kind == "frequency" && card.spark.isNotEmpty()) {
                WeeklyVisitBars(
                    card.spark,
                    Modifier.fillMaxWidth().height(40.dp).padding(top = 12.dp),
                )
            } else if (card.spark.size >= 2) {
                Sparkline(
                    card.spark,
                    Modifier.fillMaxWidth().height(40.dp).padding(top = 12.dp),
                )
            }
        }
    }
}

private const val PROGRESSION_ID_PREFIX = "progression:slot"
private val PROGRESSION_KINDS = setOf("progression", "plateau", "progression_multi")
private const val MAX_COMPARED_EXERCISES = 3

/**
 * A user-configurable progression card: an exercise multi-select (up to
 * three exercises overlaid on one est-1RM chart), a %-change axis toggle,
 * and a remove action. Single-exercise cards keep the classic headline and
 * detail; comparisons render one legend row per series instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressionCard(
    card: InsightCard,
    selected: List<String>,
    options: List<String>,
    unitLabel: String,
    canRemove: Boolean,
    onSetExercises: (List<String>) -> Unit,
    onToggleAxis: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = selected.joinToString(", "),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.insight_exercises)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        val checked = option in selected
                        // The menu stays open: picking a comparison set is a
                        // multi-toggle interaction, not a single choice.
                        DropdownMenuItem(
                            text = { Text(option) },
                            leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                            enabled = if (checked) selected.size > 1 else selected.size < MAX_COMPARED_EXERCISES,
                            onClick = {
                                onSetExercises(
                                    if (checked) selected - option else selected + option,
                                )
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (card.series.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    card.series.forEachIndexed { i, series ->
                        SeriesLegendRow(series, seriesColor(i), unitLabel)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        insightHeadline(card, unitLabel),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TrendIcon(card.trend)
                }
                Text(
                    insightDetail(card, unitLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (card.series.isNotEmpty() && card.series.any { it.points.size >= 2 }) {
                ProgressionChart(
                    series = card.series,
                    showPctChange = card.showPctChange,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = card.showPctChange,
                    onClick = onToggleAxis,
                    label = { Text(stringResource(Res.string.insight_axis_pct_change)) },
                )
                Spacer(Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.insight_remove_card),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesLegendRow(series: SparkSeries, color: Color, unitLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(series.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            stringResource(Res.string.insight_est_1rm, InsightMath.formatNumber(series.latest), unitLabel),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                Res.string.insight_series_slope,
                (if (series.slopePerWeek >= 0) "+" else "") + InsightMath.formatNumber(series.slopePerWeek),
                unitLabel,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun seriesColor(index: Int): Color {
    val palette = if (isSystemInDarkTheme()) PieColorsDark else PieColorsLight
    return palette[index % palette.size]
}

/**
 * Overlaid line chart for 1–3 series sharing one x/y domain, with min/max
 * y-axis labels so the %-change toggle has a visible effect. In %-change
 * mode every series is re-based to percent moved since its first week.
 */
@Composable
private fun ProgressionChart(
    series: List<SparkSeries>,
    showPctChange: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = series.indices.map { seriesColor(it) }
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val lines = series.map { s ->
        if (showPctChange) {
            val base = s.points.first().value
            s.points.map { SparkPoint(it.epochDay, if (base == 0.0) 0.0 else (it.value / base - 1) * 100) }
        } else {
            s.points
        }
    }
    Canvas(modifier) {
        val all = lines.flatten()
        val minX = all.minOf { it.epochDay }
        val maxX = all.maxOf { it.epochDay }
        val minY = all.minOf { it.value }
        val maxY = all.maxOf { it.value }
        val spanX = (maxX - minX).coerceAtLeast(1).toFloat()
        val spanY = (maxY - minY).toFloat().coerceAtLeast(1e-3f)

        fun label(value: Double) =
            if (showPctChange) "${InsightMath.formatNumber(value, 0)}%" else InsightMath.formatNumber(value, 0)
        val maxLabel = textMeasurer.measure(label(maxY), labelStyle)
        val minLabel = textMeasurer.measure(label(minY), labelStyle)
        val chartLeft = maxOf(maxLabel.size.width, minLabel.size.width) + 6.dp.toPx()
        val chartWidth = (size.width - chartLeft).coerceAtLeast(1f)

        drawText(maxLabel, topLeft = Offset(0f, 0f))
        drawText(minLabel, topLeft = Offset(0f, size.height - minLabel.size.height))

        lines.forEachIndexed { seriesIndex, points ->
            if (points.size < 2) return@forEachIndexed
            val color = colors[seriesIndex]
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = chartLeft + (p.epochDay - minX) / spanX * chartWidth
                val y = size.height - (p.value - minY).toFloat() / spanY * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
            points.last().let { p ->
                val x = chartLeft + (p.epochDay - minX) / spanX * chartWidth
                val y = size.height - (p.value - minY).toFloat() / spanY * size.height
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
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

/**
 * Sessions per week as bars from a zero baseline, one slot per week (zero
 * weeks stay as visible gaps in the fixed time axis). Zero-count weeks keep a
 * faint track so an empty week reads as "no visits", not "no data".
 */
@Composable
private fun WeeklyVisitBars(points: List<SparkPoint>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val maxCount = points.maxOf { it.value }.coerceAtLeast(1.0)
        val slot = size.width / points.size
        val gap = 2.dp.toPx()
        val barWidth = (slot - gap).coerceAtLeast(1f)
        val corner = CornerRadius(2.dp.toPx())
        points.forEachIndexed { i, p ->
            val left = i * slot + gap / 2
            val trackHeight = 2.dp.toPx()
            drawRoundRect(
                trackColor,
                topLeft = Offset(left, size.height - trackHeight),
                size = Size(barWidth, trackHeight),
                cornerRadius = corner,
            )
            val barHeight = (p.value / maxCount).toFloat() * size.height
            if (barHeight > 0f) {
                drawRoundRect(
                    barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }
    }
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

// Categorical palette for the body-part pie, in a fixed CVD-safe order with a
// separate step set per color mode; the folded "Other" tail is always gray.
private val PieColorsLight = listOf(
    Color(0xFF2A78D6), Color(0xFF1BAF7A), Color(0xFFEDA100), Color(0xFF008300),
    Color(0xFF4A3AA7), Color(0xFFE34948), Color(0xFFE87BA4), Color(0xFFEB6834),
)
private val PieColorsDark = listOf(
    Color(0xFF3987E5), Color(0xFF199E70), Color(0xFFC98500), Color(0xFF008300),
    Color(0xFF9085E9), Color(0xFFE66767), Color(0xFFD55181), Color(0xFFD95926),
)
private val PieOtherColor = Color(0xFF898781)

/** A pie can carry at most this many slices; smaller shares fold into "Other". */
private const val MAX_PIE_SLICES = 8

private data class PieEntry(val label: String, val color: Color, val setCount: Int)

@Composable
private fun BodyPartBreakdownCard(slices: List<BodyPartSlice>) {
    val total = slices.sumOf { it.setCount }
    if (total <= 0) return
    val palette = if (isSystemInDarkTheme()) PieColorsDark else PieColorsLight
    val shown = if (slices.size > MAX_PIE_SLICES) slices.take(MAX_PIE_SLICES - 1) else slices
    val entries = buildList {
        shown.forEachIndexed { i, slice ->
            add(PieEntry(exerciseCatalogLabel(slice.bodyPart), palette[i], slice.setCount))
        }
        val rest = total - shown.sumOf { it.setCount }
        if (rest > 0) add(PieEntry(stringResource(Res.string.body_part_other), PieOtherColor, rest))
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.insight_title_body_parts),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BodyPartPie(entries, total, Modifier.size(112.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entries.forEach { entry ->
                        BodyPartLegendRow(entry, pct = (entry.setCount * 100f / total).roundToInt())
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyPartPie(entries: List<PieEntry>, total: Int, modifier: Modifier = Modifier) {
    // The pie itself carries no text; the legend rows alongside are the
    // accessible reading of the same data.
    val separator = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        var start = -90f
        entries.forEach { entry ->
            val sweep = entry.setCount * 360f / total
            drawArc(entry.color, startAngle = start, sweepAngle = sweep, useCenter = true)
            start += sweep
        }
        if (entries.size > 1) {
            val radius = size.minDimension / 2f
            var angle = -90f
            entries.forEach { entry ->
                val rad = angle * PI.toFloat() / 180f
                drawLine(
                    color = separator,
                    start = center,
                    end = center + Offset(cos(rad), sin(rad)) * radius,
                    strokeWidth = 2.dp.toPx(),
                )
                angle += entry.setCount * 360f / total
            }
        }
    }
}

@Composable
private fun BodyPartLegendRow(entry: PieEntry, pct: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(10.dp).clip(CircleShape).background(entry.color))
        Spacer(Modifier.width(8.dp))
        Text(entry.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            stringResource(Res.string.insight_body_part_share, entry.setCount.toString(), pct.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
