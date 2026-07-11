package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.BarbellKeyboardState
import dev.balaji.moonrose.logtrain.db.BarbellKeyboardUiState
import dev.balaji.moonrose.logtrain.model.PlateMath
import dev.balaji.moonrose.logtrain.model.PlateOption
import dev.balaji.moonrose.logtrain.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * In-app keyboard for barbell weight cells: plate buttons compute
 * bar + 2 x plates, with a numeric fallback keypad for weights that can't be
 * loaded from standard plates. All state lives in [BarbellKeyboardState];
 * this is a thin rendering shared by Android and desktop.
 */
@Composable
internal fun BarbellPlateKeypad(
    state: BarbellKeyboardUiState,
    keyboard: BarbellKeyboardState,
    modifier: Modifier = Modifier,
) {
    val unitLabel = weightUnitLabel(state.unit)
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BarWeightChip(state, keyboard, unitLabel)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.totalLabel} $unitLabel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                // Switching to numeric happens from the plate grid itself;
                // this button only needs to exist to switch back.
                if (state.numericMode) {
                    TextButton(onClick = { keyboard.setNumericMode(false) }) {
                        Text(stringResource(Res.string.plate_use_plates))
                    }
                }
                IconButton(onClick = { keyboard.close() }) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(Res.string.plate_done),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.numericMode) {
                NumericPad(keyboard)
            } else {
                PlateBreakdownRow(state, keyboard)
                PlateGrid(state, keyboard)
            }
        }
    }
}

@Composable
private fun BarWeightChip(
    state: BarbellKeyboardUiState,
    keyboard: BarbellKeyboardState,
    unitLabel: String,
) {
    var showBarMenu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { showBarMenu = true }) {
            Text("${stringResource(Res.string.plate_bar)} ${PlateMath.formatWeight(state.barWeight)} $unitLabel")
        }
        DropdownMenu(expanded = showBarMenu, onDismissRequest = { showBarMenu = false }) {
            Text(
                stringResource(Res.string.plate_bar_weight),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            state.barOptions.forEach { bar ->
                DropdownMenuItem(
                    text = { Text("${bar.label} $unitLabel") },
                    onClick = {
                        showBarMenu = false
                        keyboard.selectBarWeight(bar.value)
                    },
                )
            }
        }
    }
}

/**
 * Blue ramp, lightest to strongest, one step per plate denomination (there
 * are always exactly 7). Assigned by rank within the full plate set, not by
 * absolute value, so a given denomination's color is stable across kg and lb.
 */
private val PlateChipPalette = listOf(
    Color(0xFFE3F2FD),
    Color(0xFFBBDEFB),
    Color(0xFF90CAF9),
    Color(0xFF64B5F6),
    Color(0xFF42A5F5),
    Color(0xFF1E88E5),
    Color(0xFF0D47A1),
)

/** Background for [value]'s rank among [plateOptions] (smallest = lightest). */
private fun chipColorFor(value: Double, plateOptions: List<PlateOption>): Color {
    val index = plateOptions.indexOfFirst { it.value == value }.coerceAtLeast(0)
    return PlateChipPalette[index.coerceIn(0, PlateChipPalette.lastIndex)]
}

/** White text on the strong end of the ramp, dark text on the light end. */
private fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

/** The tapped plates, in tap order; tapping a chip unloads that plate. */
@Composable
private fun PlateBreakdownRow(state: BarbellKeyboardUiState, keyboard: BarbellKeyboardState) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.plateBreakdown.isEmpty()) {
            Text(
                stringResource(Res.string.plate_per_side),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.plateBreakdown.forEach { entry ->
                val bg = chipColorFor(entry.option.value, state.plateOptions)
                InputChip(
                    selected = false,
                    onClick = { keyboard.removePlate(entry.option.value) },
                    label = { Text("${entry.count} × ${entry.option.label}") },
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = bg,
                        labelColor = contentColorFor(bg),
                    ),
                    border = null,
                )
            }
        }
    }
}

/** One cell of the plate grid: a plate denomination, or one of the two utility keys. */
private sealed interface PlateGridCell {
    data class Plate(val option: PlateOption) : PlateGridCell
    data object Backspace : PlateGridCell
    data object ToggleNumeric : PlateGridCell
}

/**
 * Plate buttons plus backspace and the numeric-mode toggle, three per row —
 * the two utility keys fill out whatever's left of the final row.
 */
@Composable
private fun PlateGrid(state: BarbellKeyboardUiState, keyboard: BarbellKeyboardState) {
    // The toggle sits just before the last plate value so the final row
    // reads [123, <largest plate>, backspace].
    val cells = if (state.plateOptions.isEmpty()) {
        listOf(PlateGridCell.ToggleNumeric, PlateGridCell.Backspace)
    } else {
        state.plateOptions.dropLast(1).map { PlateGridCell.Plate(it) } +
            listOf(
                PlateGridCell.ToggleNumeric,
                PlateGridCell.Plate(state.plateOptions.last()),
                PlateGridCell.Backspace,
            )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        cells.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { cell ->
                    when (cell) {
                        is PlateGridCell.Plate -> FilledTonalButton(
                            onClick = { keyboard.addPlate(cell.option.value) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(cell.option.label, maxLines = 1)
                        }

                        PlateGridCell.Backspace -> OutlinedButton(
                            onClick = { keyboard.removeLastPlate() },
                            enabled = state.platesPerSide.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("⌫")
                        }

                        PlateGridCell.ToggleNumeric -> OutlinedButton(
                            onClick = { keyboard.setNumericMode(true) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(stringResource(Res.string.plate_use_numeric), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumericPad(keyboard: BarbellKeyboardState) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
                            if (key == "⌫") keyboard.deleteNumericChar() else keyboard.appendNumericChar(key)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(key)
                    }
                }
            }
        }
    }
}
