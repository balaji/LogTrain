package dev.balaji.moonrose.trainfree.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness6
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.trainfree.calendar.WeekStart
import dev.balaji.moonrose.trainfree.db.ProfileRepository
import dev.balaji.moonrose.trainfree.db.TestDataRepository
import dev.balaji.moonrose.trainfree.db.UserSettingsRepository
import dev.balaji.moonrose.trainfree.model.Appearance
import dev.balaji.moonrose.trainfree.model.MAX_REST_TIMER_SECONDS
import dev.balaji.moonrose.trainfree.model.MIN_REST_TIMER_SECONDS
import dev.balaji.moonrose.trainfree.model.Profile
import dev.balaji.moonrose.trainfree.model.REST_TIMER_STEP_SECONDS
import dev.balaji.moonrose.trainfree.model.RestTimerSound
import dev.balaji.moonrose.trainfree.model.WeightUnit
import dev.balaji.moonrose.trainfree.model.clampRestTimerSeconds
import dev.balaji.moonrose.trainfree.navigation.LocalBottomFabPadding
import dev.balaji.moonrose.trainfree.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.trainfree.navigation.PlatformBackHandler
import dev.balaji.moonrose.trainfree.notifications.NotificationPermissionResult
import dev.balaji.moonrose.trainfree.notifications.rememberNotificationPermissionRequester
import dev.balaji.moonrose.trainfree.notifications.rememberNotificationSettingsOpener
import dev.balaji.moonrose.trainfree.notifications.rememberRestTimerNotifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.trainfree.sharedui.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    var editing by remember { mutableStateOf(false) }

    if (LocalUseNativeNavigation.current) {
        ProfileContent(
            editing = editing,
            onEditingChange = { editing = it },
            modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()),
        )
    } else {
        PlatformBackHandler(enabled = editing) { editing = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.profile)) },
                    navigationIcon = {
                        if (editing) {
                            IconButton(onClick = { editing = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            ProfileContent(
                editing = editing,
                onEditingChange = { editing = it },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ProfileContent(
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by ProfileRepository.profile.collectAsState(initial = null)
    val current = profile ?: return

    // No verticalScroll here: ProfileForm scrolls itself, and nesting it in
    // another scrollable would give it infinite height constraints and crash.
    Column(modifier = modifier.fillMaxSize()) {
        if (editing) {
            ProfileForm(
                initial = current,
                onSaved = { onEditingChange(false) },
            )
        } else {
            ProfileDetails(profile = current, onEdit = { onEditingChange(true) })
        }
    }
}

@Composable
private fun ProfileDetails(profile: Profile, onEdit: () -> Unit) {
    val settings by UserSettingsRepository.settings.collectAsState(initial = null)
    val unit = settings?.weightUnit ?: WeightUnit.METRIC
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                top = 24.dp,
                end = 24.dp,
                bottom = 24.dp + LocalBottomFabPadding.current,
            ),
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
    val restNotifier = rememberRestTimerNotifier()
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
    RestTimerDurationRow(current.restTimerDurationSeconds) { seconds ->
        scope.launch { UserSettingsRepository.setRestTimerDurationSeconds(seconds) }
    }
    HorizontalDivider()
    RestTimerSoundRow(current.restTimerSound) { sound ->
        restNotifier.previewSound(sound)
        scope.launch { UserSettingsRepository.setRestTimerSound(sound) }
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
    ToolRow(
        label = stringResource(Res.string.one_rm_calculator),
        value = oneRmFormulaLabel(currentSettings.oneRmFormula),
        onClick = { showOneRmCalculator = true },
    )
    HorizontalDivider()
    ToolRow(
        label = stringResource(Res.string.bmi_calculator),
        value = bmiFormulaLabel(currentSettings.bmiFormula),
        onClick = { showBmiCalculator = true },
    )
    if (showOneRmCalculator) {
        OneRmCalculatorSheet(settings = currentSettings, onDismiss = { showOneRmCalculator = false })
    }
    if (showBmiCalculator) {
        BmiCalculatorSheet(settings = currentSettings, profile = profile, onDismiss = { showBmiCalculator = false })
    }
}

@Composable
private fun ToolRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, color = MaterialTheme.colorScheme.primary)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

private val SETTINGS_SEGMENT_WIDTH = 208.dp

@Composable
private fun SegmentedSettingRow(
    label: String,
    content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.width(SETTINGS_SEGMENT_WIDTH),
            content = content,
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
    SegmentedSettingRow(label) {
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
    SegmentedSettingRow(stringResource(Res.string.appearance)) {
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
private fun RestTimerDurationRow(
    durationSeconds: Int,
    onChange: (Int) -> Unit,
) {
    var sliderSeconds by remember(durationSeconds) { mutableIntStateOf(durationSeconds) }
    val stepCount = (MAX_REST_TIMER_SECONDS - MIN_REST_TIMER_SECONDS) / REST_TIMER_STEP_SECONDS - 1
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.rest_timer_duration), style = MaterialTheme.typography.bodyLarge)
            Text(
                formatCountdown(sliderSeconds.toLong()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = sliderSeconds.toFloat(),
            onValueChange = { raw ->
                val snapped = clampRestTimerSeconds(raw.toInt())
                if (snapped != sliderSeconds) {
                    sliderSeconds = snapped
                    onChange(snapped)
                }
            },
            valueRange = MIN_REST_TIMER_SECONDS.toFloat()..MAX_REST_TIMER_SECONDS.toFloat(),
            steps = stepCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestTimerSoundRow(
    selected: RestTimerSound,
    onSelect: (RestTimerSound) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.rest_timer_sound), style = MaterialTheme.typography.bodyLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(restTimerSoundLabel(selected))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RestTimerSound.entries.forEach { sound ->
                    DropdownMenuItem(
                        text = { Text(restTimerSoundLabel(sound)) },
                        onClick = {
                            expanded = false
                            onSelect(sound)
                        },
                    )
                }
            }
        }
    }
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
