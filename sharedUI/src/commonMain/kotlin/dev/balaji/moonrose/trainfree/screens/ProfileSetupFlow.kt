package dev.balaji.moonrose.trainfree.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import dev.balaji.moonrose.trainfree.sharedui.resources.*

/**
 * First-run gate shown until a local profile exists. Replaces the old sign-in
 * screen: a welcome step with a "Create a Profile" link that leads to the form.
 * Once the profile is saved, the app-level profile observer switches to the
 * main tabs, so this flow doesn't navigate anywhere itself.
 */
@Composable
fun ProfileSetupFlow() {
    var showForm by remember { mutableStateOf(false) }

    if (showForm) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.create_a_profile), style = MaterialTheme.typography.headlineSmall)
            ProfileForm(saveLabel = stringResource(Res.string.create_profile))
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(Res.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { showForm = true }) {
                    Text(stringResource(Res.string.create_a_profile))
                }
            }
        }
    }
}
