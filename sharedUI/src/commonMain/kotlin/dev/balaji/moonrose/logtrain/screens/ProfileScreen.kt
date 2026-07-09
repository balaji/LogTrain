package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.db.ProfileRepository
import dev.balaji.moonrose.logtrain.db.UserSettingsRepository
import dev.balaji.moonrose.logtrain.model.Appearance
import dev.balaji.moonrose.logtrain.model.Profile
import dev.balaji.moonrose.logtrain.model.WeightUnit
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onGoBack: () -> Unit) {
    if (LocalUseNativeNavigation.current) {
        ProfileContent(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()))
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profile") },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            ProfileContent(Modifier.padding(padding))
        }
    }
}

/**
 * Entry point to the profile screen, shown as the leading top-bar action on
 * each tab root now that Profile no longer has a tab of its own.
 */
@Composable
fun ProfileTopBarButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
    }
}

@Composable
private fun ProfileContent(modifier: Modifier = Modifier) {
    val profile by ProfileRepository.profile.collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }

    val current = profile ?: return

    Column(modifier = modifier.fillMaxSize()) {
        if (editing) {
            ProfileForm(
                initial = current,
                onSaved = { editing = false },
            )
        } else {
            ProfileDetails(profile = current, onEdit = { editing = true })
        }
    }
}

@Composable
private fun ProfileDetails(profile: Profile, onEdit: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProfileField("Name", profile.name)
        HorizontalDivider()
        ProfileField("Age", profile.age?.toString())
        HorizontalDivider()
        ProfileField("Gender", profile.gender)
        HorizontalDivider()
        ProfileField("Weight", profile.weight?.let { "$it kg" })
        HorizontalDivider()
        ProfileField("Height", profile.height?.let { "$it cm" })
        HorizontalDivider()
        TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.End)) {
            Text("Edit")
        }
        SettingsSection()
    }
}

/**
 * Always-editable app settings backed by the user_settings table — no Edit
 * mode; each change saves immediately. Display labels live only here so they
 * can be regionalized later; the DB stores language-neutral keys.
 */
@Composable
private fun SettingsSection() {
    val settings by UserSettingsRepository.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val current = settings ?: return

    Text(
        "Settings",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    SettingRow("Week starts on", WeekStart.entries, current.weekStart, ::weekStartLabel) {
        scope.launch { UserSettingsRepository.setWeekStart(it) }
    }
    HorizontalDivider()
    SettingRow("Appearance", Appearance.entries, current.appearance, ::appearanceLabel) {
        scope.launch { UserSettingsRepository.setAppearance(it) }
    }
    HorizontalDivider()
    SettingRow("Weight units", WeightUnit.entries, current.weightUnit, ::weightUnitLabel) {
        scope.launch { UserSettingsRepository.setWeightUnit(it) }
    }
    HorizontalDivider()
}

@Composable
private fun <T> SettingRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(optionLabel(option))
                }
            }
        }
    }
}

private fun weekStartLabel(weekStart: WeekStart) = when (weekStart) {
    WeekStart.SUNDAY -> "Sun"
    WeekStart.MONDAY -> "Mon"
}

private fun appearanceLabel(appearance: Appearance) = when (appearance) {
    Appearance.LIGHT -> "Light"
    Appearance.DARK -> "Dark"
    Appearance.SYSTEM -> "System"
}

private fun weightUnitLabel(weightUnit: WeightUnit) = when (weightUnit) {
    WeightUnit.METRIC -> "Metric"
    WeightUnit.IMPERIAL -> "Imperial"
}

@Composable
private fun ProfileField(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
