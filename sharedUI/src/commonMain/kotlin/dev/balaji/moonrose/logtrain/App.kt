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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import dev.balaji.moonrose.logtrain.db.ProfileRepository
import dev.balaji.moonrose.logtrain.navigation.CalendarRoute
import dev.balaji.moonrose.logtrain.navigation.ExercisesRoute
import dev.balaji.moonrose.logtrain.navigation.NavStack
import dev.balaji.moonrose.logtrain.navigation.WorkoutsRoute
import dev.balaji.moonrose.logtrain.navigation.PlatformBackHandler
import dev.balaji.moonrose.logtrain.navigation.ProfileRoute
import dev.balaji.moonrose.logtrain.navigation.ScreenContent
import dev.balaji.moonrose.logtrain.navigation.TopLevelRoute
import dev.balaji.moonrose.logtrain.screens.ProfileSetupFlow

private data class TabSpec(val route: TopLevelRoute, val icon: ImageVector)

/**
 * Full app for platforms where Compose owns navigation (Android, Desktop).
 * iOS does not call this — its SwiftUI shell hosts screens individually.
 */
@Composable
@Preview
fun App() {
    PlatformTheme {
        val hasProfile by produceState<Boolean?>(initialValue = null) {
            ProfileRepository.profile.collect { value = it != null }
        }

        when (hasProfile) {
            null -> Box(Modifier.fillMaxSize()) // first emission arrives near-instantly
            false -> ProfileSetupFlow()
            true -> MainTabs()
        }
    }
}

@Composable
private fun MainTabs() {
    val tabs = remember {
        listOf(
            TabSpec(ExercisesRoute, Icons.Filled.FitnessCenter),
            TabSpec(WorkoutsRoute, Icons.AutoMirrored.Filled.Assignment),
            TabSpec(CalendarRoute, Icons.Filled.CalendarMonth),
            TabSpec(ProfileRoute, Icons.Filled.Person),
        )
    }
    var currentTab by remember { mutableStateOf<TopLevelRoute>(ExercisesRoute) }
    val stacks = remember { tabs.associate { it.route to NavStack(it.route) } }
    val stack = stacks.getValue(currentTab)

    PlatformBackHandler(enabled = stack.canGoBack) { stack.pop() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab.route,
                        onClick = {
                            if (currentTab == tab.route) {
                                stacks.getValue(tab.route).popToRoot()
                            } else {
                                currentTab = tab.route
                            }
                        },
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
