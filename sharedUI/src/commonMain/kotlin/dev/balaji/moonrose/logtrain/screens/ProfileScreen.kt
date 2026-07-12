package dev.balaji.moonrose.logtrain.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.calendar.WeekStart
import dev.balaji.moonrose.logtrain.db.ProfileRepository
import dev.balaji.moonrose.logtrain.db.TestDataRepository
import dev.balaji.moonrose.logtrain.db.UserSettingsRepository
import dev.balaji.moonrose.logtrain.model.Appearance
import dev.balaji.moonrose.logtrain.model.Profile
import dev.balaji.moonrose.logtrain.model.RestTimerOption
import dev.balaji.moonrose.logtrain.model.WeightUnit
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.notifications.NotificationPermissionResult
import dev.balaji.moonrose.logtrain.notifications.rememberNotificationPermissionRequester
import dev.balaji.moonrose.logtrain.notifications.rememberNotificationSettingsOpener
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.logtrain.sharedui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onGoBack: () -> Unit) {
    if (LocalUseNativeNavigation.current) {
        ProfileContent(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()))
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.profile)) },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
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
        Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(Res.string.profile))
    }
}

@Composable
private fun ProfileContent(modifier: Modifier = Modifier) {
    val profile by ProfileRepository.profile.collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }

    val current = profile ?: return

    // No verticalScroll here: ProfileForm scrolls itself, and nesting it in
    // another scrollable would give it infinite height constraints and crash.
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
    val settings by UserSettingsRepository.settings.collectAsState(initial = null)
    val unit = settings?.weightUnit ?: WeightUnit.METRIC
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProfileField(stringResource(Res.string.name), profile.name)
        HorizontalDivider()
        ProfileField(stringResource(Res.string.age), profile.age?.toString())
        HorizontalDivider()
        ProfileField(stringResource(Res.string.gender), profile.gender?.let { genderLabel(it) })
        HorizontalDivider()
        ProfileField(stringResource(Res.string.weight), profile.weight?.let { weightValueLabel(it, unit) })
        HorizontalDivider()
        ProfileField(stringResource(Res.string.height), profile.height?.let { heightValueLabel(it, unit) })
        HorizontalDivider()
        TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.edit))
        }
        ToolsSection()
        SettingsSection()
        DeveloperSection()
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
    var showNotificationsDeniedHint by remember { mutableStateOf(false) }
    val requestNotificationPermission = rememberNotificationPermissionRequester { result ->
        if (result == NotificationPermissionResult.DeniedPermanently) showNotificationsDeniedHint = true
        scope.launch {
            UserSettingsRepository.setNotificationsEnabled(result == NotificationPermissionResult.Granted)
        }
    }
    val current = settings ?: return

    Text(
        stringResource(Res.string.settings),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    SettingRow(stringResource(Res.string.week_starts_on), WeekStart.entries, current.weekStart, { weekStartLabel(it) }) {
        scope.launch { UserSettingsRepository.setWeekStart(it) }
    }
    HorizontalDivider()
    AppearanceSettingRow(current.appearance) {
        scope.launch { UserSettingsRepository.setAppearance(it) }
    }
    HorizontalDivider()
    SettingRow(stringResource(Res.string.weight_units), WeightUnit.entries, current.weightUnit, { weightUnitSettingLabel(it) }) {
        scope.launch { UserSettingsRepository.setWeightUnit(it) }
    }
    HorizontalDivider()
    SettingDropdownRow(
        label = stringResource(Res.string.default_rest_timer),
        options = RestTimerOption.entries,
        selected = current.defaultRestTimer,
        optionLabel = { restTimerLabel(it) },
    ) {
        scope.launch { UserSettingsRepository.setDefaultRestTimer(it) }
    }
    HorizontalDivider()
    SettingSwitchRow(stringResource(Res.string.send_notifications), current.notificationsEnabled) { enabled ->
        // Opting in persists only once the OS grants permission — the switch
        // renders the stored value, so a denied prompt leaves it off.
        if (enabled) {
            requestNotificationPermission()
        } else {
            scope.launch { UserSettingsRepository.setNotificationsEnabled(false) }
        }
    }
    if (showNotificationsDeniedHint) {
        val openNotificationSettings = rememberNotificationSettingsOpener()
        AlertDialog(
            onDismissRequest = { showNotificationsDeniedHint = false },
            title = { Text(stringResource(Res.string.notifications_denied_title)) },
            text = { Text(stringResource(Res.string.notifications_denied_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationsDeniedHint = false
                    openNotificationSettings()
                }) { Text(stringResource(Res.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationsDeniedHint = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/**
 * Standalone calculators that don't belong to a saved setting — 1RM and BMI
 * today, with room for more (e.g. macro or plate-math calculators) later.
 */
@Composable
private fun ToolsSection() {
    val settings by UserSettingsRepository.settings.collectAsState(initial = null)
    val profile by ProfileRepository.profile.collectAsState(initial = null)
    var showOneRmCalculator by remember { mutableStateOf(false) }
    var showBmiCalculator by remember { mutableStateOf(false) }
    val currentSettings = settings ?: return

    Text(
        stringResource(Res.string.tools),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().clickable { showOneRmCalculator = true }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.one_rm_calculator), style = MaterialTheme.typography.bodyLarge)
        Text(oneRmFormulaLabel(currentSettings.oneRmFormula), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth().clickable { showBmiCalculator = true }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.bmi_calculator), style = MaterialTheme.typography.bodyLarge)
        Text(bmiFormulaLabel(currentSettings.bmiFormula), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showOneRmCalculator) {
        OneRmCalculatorSheet(settings = currentSettings, onDismiss = { showOneRmCalculator = false })
    }
    if (showBmiCalculator) {
        BmiCalculatorSheet(settings = currentSettings, profile = profile, onDismiss = { showBmiCalculator = false })
    }
}

/**
 * Test-data tooling for development. Kept in release builds for now, tucked
 * under its own heading; generated rows carry the test_data flag so Delete
 * only ever removes what Generate created.
 */
@Composable
private fun DeveloperSection() {
    val scope = rememberCoroutineScope()
    val testSessionCount by TestDataRepository.testSessionCount.collectAsState(initial = 0)
    var showGenerateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    Text(
        stringResource(Res.string.developer),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.test_data), style = MaterialTheme.typography.bodyLarge)
        Row {
            TextButton(enabled = !working, onClick = { showGenerateDialog = true }) {
                Text(stringResource(Res.string.generate))
            }
            TextButton(
                enabled = !working && testSessionCount > 0,
                onClick = { showDeleteDialog = true },
            ) {
                Text(stringResource(Res.string.delete))
            }
        }
    }
    HorizontalDivider()

    if (showGenerateDialog) {
        var daysText by remember { mutableStateOf("90") }
        val days = daysText.toIntOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { if (!working) showGenerateDialog = false },
            title = { Text(stringResource(Res.string.generate_test_data_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.generate_test_data_message))
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(Res.string.days_of_history)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = days != null && !working,
                    onClick = {
                        working = true
                        scope.launch {
                            try {
                                TestDataRepository.generate(days!!)
                            } finally {
                                working = false
                                showGenerateDialog = false
                            }
                        }
                    },
                ) {
                    Text(if (working) stringResource(Res.string.generating) else stringResource(Res.string.generate))
                }
            },
            dismissButton = {
                TextButton(enabled = !working, onClick = { showGenerateDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!working) showDeleteDialog = false },
            title = { Text(stringResource(Res.string.delete_test_data_title)) },
            text = {
                Text(stringResource(Res.string.delete_test_data_message, testSessionCount))
            },
            confirmButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        working = true
                        scope.launch {
                            try {
                                TestDataRepository.deleteAll()
                            } finally {
                                working = false
                                showDeleteDialog = false
                            }
                        }
                    },
                ) {
                    Text(if (working) stringResource(Res.string.deleting) else stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(enabled = !working, onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun <T> SettingRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
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

/**
 * Like [SettingRow] but for option sets too wide for a segmented row: the
 * current value opens a dropdown of all options.
 */
@Composable
private fun <T> SettingDropdownRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(optionLabel(selected))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        trailingIcon = {
                            if (option == selected) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun weekStartLabel(weekStart: WeekStart) = stringResource(
    when (weekStart) {
        WeekStart.SUNDAY -> Res.string.weekday_sun_short
        WeekStart.MONDAY -> Res.string.weekday_mon_short
    },
)

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AppearanceSettingRow(selected: Appearance, onSelect: (Appearance) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.appearance), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow {
            Appearance.entries.forEachIndexed { index, appearance ->
                SegmentedButton(
                    selected = appearance == selected,
                    onClick = { onSelect(appearance) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = Appearance.entries.size),
                    icon = {},
                ) {
                    Icon(
                        imageVector = when (appearance) {
                            Appearance.LIGHT -> Icons.Filled.LightMode
                            Appearance.DARK -> Icons.Filled.DarkMode
                            Appearance.SYSTEM -> Icons.Filled.Brightness6
                        },
                        contentDescription = appearanceLabel(appearance),
                    )
                }
            }
        }
    }
}

@Composable
private fun appearanceLabel(appearance: Appearance) = stringResource(
    when (appearance) {
        Appearance.LIGHT -> Res.string.appearance_light
        Appearance.DARK -> Res.string.appearance_dark
        Appearance.SYSTEM -> Res.string.appearance_system
    },
)

// Not named weightUnitLabel: that helper (LogicLabels.kt) is the "kg"/"lb"
// value label, while this is the setting option's name.
@Composable
private fun weightUnitSettingLabel(weightUnit: WeightUnit) = stringResource(
    when (weightUnit) {
        WeightUnit.METRIC -> Res.string.units_metric
        WeightUnit.IMPERIAL -> Res.string.units_imperial
    },
)

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
