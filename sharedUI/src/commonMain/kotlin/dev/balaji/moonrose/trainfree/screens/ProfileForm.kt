package dev.balaji.moonrose.trainfree.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.trainfree.db.GENDER_KEYS
import dev.balaji.moonrose.trainfree.db.ProfileFormState
import dev.balaji.moonrose.trainfree.db.UserSettingsRepository
import dev.balaji.moonrose.trainfree.model.Profile
import dev.balaji.moonrose.trainfree.model.WeightUnit
import dev.balaji.moonrose.trainfree.navigation.LocalBottomFabPadding
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.trainfree.sharedui.resources.*

/**
 * Profile create/edit form, shared by the first-run setup flow and the
 * Profile tab's edit mode. Weight and height can be switched between metric
 * (kg/cm) and imperial (lb + ft/in); the shared form state converts back to
 * canonical kg/cm and persists the unit preference to settings on save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileForm(
    initial: Profile? = null,
    saveLabel: String = stringResource(Res.string.save),
    onSaved: () -> Unit = {},
) {
    val settings by UserSettingsRepository.settings.collectAsState(initial = null)
    val initialUnit = settings?.weightUnit ?: return
    val formState = remember { ProfileFormState(initial, initialUnit) }
    val uiState by formState.uiState.collectAsState()
    var genderExpanded by remember { mutableStateOf(false) }
    var heightWheelsExpanded by remember { mutableStateOf(false) }
    // Typing in any text field puts the height wheels away.
    val collapseWheels = Modifier.onFocusChanged { if (it.isFocused) heightWheelsExpanded = false }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                top = 24.dp,
                end = 24.dp,
                bottom = 24.dp + LocalBottomFabPadding.current,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = formState::updateName,
            label = { Text(stringResource(Res.string.name)) },
            isError = uiState.nameError != null,
            supportingText = uiState.nameError?.let { error ->
                { Text(profileNameErrorText(error)) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().then(collapseWheels),
        )
        OutlinedTextField(
            value = uiState.age,
            onValueChange = formState::updateAge,
            label = { Text(stringResource(Res.string.age)) },
            isError = uiState.ageError,
            supportingText = if (uiState.ageError) {
                { Text(profileAgeErrorText()) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().then(collapseWheels),
        )
        ExposedDropdownMenuBox(
            expanded = genderExpanded,
            onExpandedChange = { genderExpanded = it },
        ) {
            OutlinedTextField(
                value = uiState.gender?.let { genderLabel(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(Res.string.gender)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = genderExpanded,
                onDismissRequest = { genderExpanded = false },
            ) {
                GENDER_KEYS.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(genderLabel(key)) },
                        onClick = {
                            formState.updateGender(key)
                            genderExpanded = false
                        },
                    )
                }
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WeightUnit.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = uiState.unit == entry,
                    onClick = {
                        if (entry == WeightUnit.METRIC) heightWheelsExpanded = false
                        formState.updateUnit(entry)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = WeightUnit.entries.size),
                ) {
                    Text(
                        stringResource(
                            when (entry) {
                                WeightUnit.METRIC -> Res.string.units_metric
                                WeightUnit.IMPERIAL -> Res.string.units_imperial
                            },
                        ),
                    )
                }
            }
        }
        OutlinedTextField(
            value = uiState.weight,
            onValueChange = formState::updateWeight,
            label = { Text("${stringResource(Res.string.weight)} (${weightUnitLabel(uiState.unit)})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().then(collapseWheels),
        )
        if (uiState.unit == WeightUnit.METRIC) {
            OutlinedTextField(
                value = uiState.height,
                onValueChange = formState::updateHeight,
                label = { Text("${stringResource(Res.string.height)} (${stringResource(Res.string.unit_cm)})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FeetInchesField(
                label = stringResource(Res.string.height),
                feet = uiState.heightFeet,
                inches = uiState.heightInches,
                expanded = heightWheelsExpanded,
                onExpandedChange = { heightWheelsExpanded = it },
                onChange = formState::updateHeightFeetInches,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = { formState.save(onSaved) },
            enabled = !uiState.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(saveLabel)
        }
    }
}
