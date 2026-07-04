package dev.balaji.moonrose.logtrain.screens

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
            Text("Create a Profile", style = MaterialTheme.typography.headlineSmall)
            ProfileForm(saveLabel = "Create Profile")
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("LogTrain", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Track your workouts, all on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { showForm = true }) {
                    Text("Create a Profile")
                }
            }
        }
    }
}
