package dev.balaji.moonrose.logtrain

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import dev.balaji.moonrose.logtrain.navigation.LocalNativeAlertPresenter
import dev.balaji.moonrose.logtrain.navigation.LocalUseNativeNavigation
import dev.balaji.moonrose.logtrain.navigation.Route
import dev.balaji.moonrose.logtrain.navigation.ScreenContent
import dev.balaji.moonrose.logtrain.navigation.TopLevelRoute
import dev.balaji.moonrose.logtrain.navigation.UIKitNativeAlertPresenter
import dev.balaji.moonrose.logtrain.screens.AuthScreen
import platform.UIKit.UIViewController

/**
 * Per-screen entry points for the SwiftUI shell. SwiftUI owns TabView and
 * NavigationStack (iOS 26 Liquid Glass chrome); Compose renders content only.
 */
private fun composeScreen(content: @Composable () -> Unit): UIViewController {
    lateinit var controller: UIViewController
    val alertPresenter = UIKitNativeAlertPresenter { controller }
    controller = ComposeUIViewController(configure = { onFocusBehavior = OnFocusBehavior.DoNothing }) {
        PlatformTheme {
            CompositionLocalProvider(
                LocalUseNativeNavigation provides true,
                LocalNativeAlertPresenter provides alertPresenter,
            ) {
                content()
            }
        }
    }
    return controller
}

fun AuthViewController(): UIViewController = composeScreen { AuthScreen() }

fun MainViewController(
    route: TopLevelRoute,
    onNavigate: (Route) -> Unit,
): UIViewController = composeScreen {
    ScreenContent(route = route, onNavigate = onNavigate, onGoBack = {})
}

fun ScreenViewController(
    route: Route,
    onNavigate: (Route) -> Unit,
    onGoBack: () -> Unit,
): UIViewController = composeScreen {
    ScreenContent(route = route, onNavigate = onNavigate, onGoBack = onGoBack)
}
