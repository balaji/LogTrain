package dev.balaji.moonrose.trainfree.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.trainfree.db.UserSettingsRepository
import dev.balaji.moonrose.trainfree.insights.BmiClassification
import dev.balaji.moonrose.trainfree.insights.BmiFormula
import dev.balaji.moonrose.trainfree.insights.InsightMath
import dev.balaji.moonrose.trainfree.model.HeightConversion
import dev.balaji.moonrose.trainfree.model.Profile
import dev.balaji.moonrose.trainfree.model.UserSettings
import dev.balaji.moonrose.trainfree.model.WeightUnit
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.trainfree.sharedui.resources.*

/** Fallback height when the profile has none, so the wheels start somewhere sane. */
private const val DEFAULT_HEIGHT_CM = 170.0

/** Which option's explainer dialog is open, if any. */
private sealed interface BmiInfo {
    data class Formula(val formula: BmiFormula) : BmiInfo

    data class Classification(val classification: BmiClassification) : BmiInfo
}

/**
 * Bottom-sheet BMI calculator reached from Profile > Tools. Weight and height
 * prefill from the saved profile in the user's unit system: kg/cm text fields
 * in metric, or lb text plus a tap-to-reveal feet/inches wheel in imperial.
 * Everything is converted to kg/cm for the calculation, and either input can
 * be overridden for a one-off estimate. Tapping a formula or classification
 * row saves it as the app-wide default and re-derives the shown result, so
 * the options can be compared in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BmiCalculatorSheet(settings: UserSettings, profile: Profile?, onDismiss: () -> Unit) {
    val unit = settings.weightUnit
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var weightText by remember {
        mutableStateOf(profile?.weight?.let { InsightMath.formatNumber(unit.fromKilograms(it)) } ?: "")
    }
    var heightText by remember { mutableStateOf(profile?.height?.let { InsightMath.formatNumber(it) } ?: "") }
    val initialFeetInches = remember { HeightConversion.cmToFeetInches(profile?.height ?: DEFAULT_HEIGHT_CM) }
    var feet by remember { mutableStateOf(initialFeetInches.feet) }
    var inches by remember { mutableStateOf(initialFeetInches.inches) }
    var wheelsExpanded by remember { mutableStateOf(false) }

    // Inputs captured at Calculate time (kg, cm); BMI and category derive from
    // the current settings so option taps update the shown result live.
    var calculated by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var infoFor by remember { mutableStateOf<BmiInfo?>(null) }

    val weight = weightText.toDoubleOrNull()?.takeIf { it > 0 }
    val heightCm = when (unit) {
        WeightUnit.METRIC -> heightText.toDoubleOrNull()?.takeIf { it > 0 }
        WeightUnit.IMPERIAL -> HeightConversion.feetInchesToCm(feet, inches)
    }

    // Full-height sheet: the inputs plus the formula and classification
    // groups don't fit a content-sized one. Kept below the status bar with a
    // small gap, like a full-height iOS sheet.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.statusBarsPadding().padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.bmi_calculator), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                    label = { Text("${stringResource(Res.string.weight)} (${weightUnitLabel(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        // Typing a weight puts the height wheels away.
                        .onFocusChanged { if (it.isFocused) wheelsExpanded = false },
                )
                if (unit == WeightUnit.METRIC) {
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                        label = { Text("${stringResource(Res.string.height)} (${stringResource(Res.string.unit_cm)})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (unit == WeightUnit.IMPERIAL) {
                FeetInchesField(
                    label = stringResource(Res.string.height),
                    feet = feet,
                    inches = inches,
                    expanded = wheelsExpanded,
                    onExpandedChange = { wheelsExpanded = it },
                    onChange = { f, i ->
                        feet = f
                        inches = i
                    },
                )
            }
            Button(
                enabled = weight != null && heightCm != null,
                onClick = {
                    wheelsExpanded = false
                    calculated = unit.toKilograms(weight!!) to heightCm!!
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.calculate))
            }
            calculated?.let { (kg, cm) ->
                val bmi = settings.bmiFormula.bmi(kg, cm)
                Text(
                    stringResource(
                        Res.string.bmi_result,
                        InsightMath.formatNumber(bmi),
                        bmiCategoryLabel(settings.bmiClassification.of(bmi)),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                BmiSpectrum(bmi, settings.bmiClassification)
            }
            Text(
                stringResource(Res.string.bmi_selected_formula_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(Res.string.bmi_formula_heading), style = MaterialTheme.typography.titleSmall)
            Column {
                BmiFormula.entries.forEach { formula ->
                    BmiOptionRow(
                        label = bmiFormulaLabel(formula),
                        selected = formula == settings.bmiFormula,
                        value = calculated?.let { (kg, cm) -> InsightMath.formatNumber(formula.bmi(kg, cm)) },
                        onSelect = { scope.launch { UserSettingsRepository.setBmiFormula(formula) } },
                        onInfo = { infoFor = BmiInfo.Formula(formula) },
                    )
                    HorizontalDivider()
                }
            }
            Text(stringResource(Res.string.bmi_classification_heading), style = MaterialTheme.typography.titleSmall)
            Column {
                BmiClassification.entries.forEach { classification ->
                    BmiOptionRow(
                        label = bmiClassificationLabel(classification),
                        selected = classification == settings.bmiClassification,
                        value = calculated?.let { (kg, cm) ->
                            bmiCategoryLabel(classification.of(settings.bmiFormula.bmi(kg, cm)))
                        },
                        onSelect = { scope.launch { UserSettingsRepository.setBmiClassification(classification) } },
                        onInfo = { infoFor = BmiInfo.Classification(classification) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    infoFor?.let { info -> BmiInfoDialog(info, onDismiss = { infoFor = null }) }
}

@Composable
private fun BmiOptionRow(
    label: String,
    selected: Boolean,
    value: String?,
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
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onInfo) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(Res.string.find_out_more))
        }
    }
}

@Composable
private fun BmiInfoDialog(info: BmiInfo, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val title = when (info) {
        is BmiInfo.Formula -> bmiFormulaLabel(info.formula)
        is BmiInfo.Classification -> bmiClassificationLabel(info.classification)
    }
    val body = when (info) {
        is BmiInfo.Formula -> bmiFormulaInfoText(info.formula)
        is BmiInfo.Classification -> bmiClassificationInfoText(info.classification)
    }
    val searchQuery = stringResource(Res.string.bmi_search_query, title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        // ~200 words; scrolls on short screens instead of clipping.
        text = { Text(body, modifier = Modifier.verticalScroll(rememberScrollState())) },
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
