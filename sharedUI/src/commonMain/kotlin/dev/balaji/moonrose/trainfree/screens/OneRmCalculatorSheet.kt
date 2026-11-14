package dev.balaji.moonrose.trainfree.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.trainfree.db.UserSettingsRepository
import dev.balaji.moonrose.trainfree.insights.InsightMath
import dev.balaji.moonrose.trainfree.insights.OneRmFormula
import dev.balaji.moonrose.trainfree.model.UserSettings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.trainfree.sharedui.resources.*

/**
 * Bottom-sheet 1RM calculator reached from Profile settings. Enter a weight
 * and rep count to see every formula's estimate side by side; tapping a row
 * saves that formula as the app-wide default used by all insights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OneRmCalculatorSheet(settings: UserSettings, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var weightText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<Map<OneRmFormula, Double>?>(null) }
    var infoFor by remember { mutableStateOf<OneRmFormula?>(null) }
    val weight = weightText.toDoubleOrNull()?.takeIf { it > 0 }
    val reps = repsText.toIntOrNull()?.takeIf { it > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.one_rm_calculator), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                    label = { Text("${stringResource(Res.string.weight)} (${weightUnitLabel(settings.weightUnit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(Res.string.reps)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                enabled = weight != null && reps != null,
                onClick = { results = OneRmFormula.entries.associateWith { it.estimate(weight!!, reps!!) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.calculate))
            }
            if (reps != null && reps > 15) {
                Text(
                    stringResource(Res.string.one_rm_high_reps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                stringResource(Res.string.one_rm_selected_formula_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                OneRmFormula.entries.forEach { formula ->
                    FormulaRow(
                        formula = formula,
                        selected = formula == settings.oneRmFormula,
                        result = results?.get(formula),
                        unitLabel = weightUnitLabel(settings.weightUnit),
                        onSelect = { scope.launch { UserSettingsRepository.setOneRmFormula(formula) } },
                        onInfo = { infoFor = formula },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    infoFor?.let { formula -> FormulaInfoDialog(formula, onDismiss = { infoFor = null }) }
}

@Composable
private fun FormulaRow(
    formula: OneRmFormula,
    selected: Boolean,
    result: Double?,
    unitLabel: String,
    onSelect: () -> Unit,
    onInfo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Text(oneRmFormulaLabel(formula), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            result?.let { "${InsightMath.formatNumber(it)} $unitLabel" } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onInfo) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.find_out_more))
        }
    }
}

@Composable
private fun FormulaInfoDialog(formula: OneRmFormula, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val searchQuery = stringResource(Res.string.one_rm_search_query, oneRmFormulaLabel(formula))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(oneRmFormulaLabel(formula)) },
        // ~200 words; scrolls on short screens instead of clipping.
        text = { Text(oneRmInfoText(formula), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(
                onClick = { uriHandler.openUri("https://google.com/search?q=" + searchQuery.replace(" ", "%20")) },
            ) {
                Text(stringResource(Res.string.find_out_more))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        },
    )
}
