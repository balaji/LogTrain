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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import dev.balaji.moonrose.logtrain.db.ProfileRepository
import dev.balaji.moonrose.logtrain.model.Profile
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    if (LocalUseNativeNavigation.current) {
        ProfileContent(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()))
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
            ProfileContent(Modifier.padding(padding))
        }
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
