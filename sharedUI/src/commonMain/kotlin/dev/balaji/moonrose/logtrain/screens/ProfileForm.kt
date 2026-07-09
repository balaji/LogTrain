package dev.balaji.moonrose.logtrain.screens

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.ProfileFormState
import dev.balaji.moonrose.logtrain.model.Profile

private val GENDER_OPTIONS = listOf("Male", "Female", "Other", "Prefer not to say")

/**
 * Profile create/edit form, shared by the first-run setup flow and the
 * Profile tab's edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileForm(
    initial: Profile? = null,
    saveLabel: String = "Save",
    onSaved: () -> Unit = {},
) {
    val formState = remember { ProfileFormState(initial) }
    val uiState by formState.uiState.collectAsState()
    var genderExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = formState::updateName,
            label = { Text("Name") },
            isError = uiState.nameError != null,
            supportingText = uiState.nameError?.let { error ->
                { Text(error) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.age,
            onValueChange = formState::updateAge,
            label = { Text("Age") },
            isError = uiState.ageError != null,
            supportingText = uiState.ageError?.let { error ->
                { Text(error) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(
            expanded = genderExpanded,
            onExpandedChange = { genderExpanded = it },
        ) {
            OutlinedTextField(
                value = uiState.gender ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Gender") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = genderExpanded,
                onDismissRequest = { genderExpanded = false },
            ) {
                GENDER_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            formState.updateGender(option)
                            genderExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = uiState.weight,
            onValueChange = formState::updateWeight,
            label = { Text("Weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.height,
            onValueChange = formState::updateHeight,
            label = { Text("Height (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { formState.save(onSaved) },
            enabled = !uiState.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(saveLabel)
        }
    }
}
