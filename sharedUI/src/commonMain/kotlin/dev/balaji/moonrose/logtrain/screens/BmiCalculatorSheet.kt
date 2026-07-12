package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.insights.BmiCategory
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.model.HeightConversion
import dev.balaji.moonrose.logtrain.model.Profile
import dev.balaji.moonrose.logtrain.model.UserSettings
import dev.balaji.moonrose.logtrain.model.WeightUnit
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

/** Fallback height when the profile has none, so the wheels start somewhere sane. */
private const val DEFAULT_HEIGHT_CM = 170.0

/**
 * Bottom-sheet BMI calculator reached from Profile > Tools. Weight and height
 * prefill from the saved profile in the user's unit system: kg/cm text fields
 * in metric, or lb text plus a tap-to-reveal feet/inches wheel in imperial.
 * Everything is converted to kg/cm for the calculation, and either input can
 * be overridden for a one-off estimate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BmiCalculatorSheet(settings: UserSettings, profile: Profile?, onDismiss: () -> Unit) {
    val unit = settings.weightUnit
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var weightText by remember {
        mutableStateOf(profile?.weight?.let { InsightMath.formatNumber(unit.fromKilograms(it)) } ?: "")
    }
    var heightText by remember { mutableStateOf(profile?.height?.let { InsightMath.formatNumber(it) } ?: "") }
    val initialFeetInches = remember { HeightConversion.cmToFeetInches(profile?.height ?: DEFAULT_HEIGHT_CM) }
    var feet by remember { mutableStateOf(initialFeetInches.feet) }
    var inches by remember { mutableStateOf(initialFeetInches.inches) }
    var wheelsExpanded by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Double?>(null) }

    val weight = weightText.toDoubleOrNull()?.takeIf { it > 0 }
    val heightCm = when (unit) {
        WeightUnit.METRIC -> heightText.toDoubleOrNull()?.takeIf { it > 0 }
        WeightUnit.IMPERIAL -> HeightConversion.feetInchesToCm(feet, inches)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
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
                    result = InsightMath.bmi(unit.toKilograms(weight!!), heightCm!!)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.calculate))
            }
            result?.let { bmi ->
                Text(
                    stringResource(Res.string.bmi_result, InsightMath.formatNumber(bmi), bmiCategoryLabel(BmiCategory.of(bmi))),
                    style = MaterialTheme.typography.bodyLarge,
                )
                BmiSpectrum(bmi)
            }
        }
    }
}
