package dev.balaji.moonrose.logtrain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import dev.balaji.moonrose.logtrain.auth.rememberIsSignedIn
import dev.balaji.moonrose.logtrain.navigation.NavStack
import dev.balaji.moonrose.logtrain.navigation.PlatformBackHandler
import dev.balaji.moonrose.logtrain.navigation.ProfileRoute
import dev.balaji.moonrose.logtrain.navigation.ScreenContent
import dev.balaji.moonrose.logtrain.navigation.TopLevelRoute
import dev.balaji.moonrose.logtrain.navigation.WorkoutsRoute
import dev.balaji.moonrose.logtrain.screens.AuthScreen

private data class TabSpec(val route: TopLevelRoute, val icon: ImageVector)

/**
 * Full app for platforms where Compose owns navigation (Android, Desktop).
 * iOS does not call this — its SwiftUI shell hosts screens individually.
 */
@Composable
@Preview
fun App() {
    PlatformTheme {
        val isSignedIn by rememberIsSignedIn()

        if (!isSignedIn) {
            AuthScreen()
        } else {
            MainTabs()
        }
    }
}

@Composable
private fun MainTabs() {
    val tabs = remember {
        listOf(
            TabSpec(WorkoutsRoute, Icons.AutoMirrored.Filled.List),
            TabSpec(ProfileRoute, Icons.Filled.Person),
        )
    }
    var currentTab by remember { mutableStateOf<TopLevelRoute>(WorkoutsRoute) }
    val stacks = remember { tabs.associate { it.route to NavStack(it.route) } }
    val stack = stacks.getValue(currentTab)

    PlatformBackHandler(enabled = stack.canGoBack) { stack.pop() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab.route,
                        onClick = { currentTab = tab.route },
                        icon = { Icon(tab.icon, contentDescription = tab.route.title) },
                        label = { Text(tab.route.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            ScreenContent(
                route = stack.current,
                onNavigate = stack::push,
                onGoBack = stack::pop,
            )
        }
    }
}
