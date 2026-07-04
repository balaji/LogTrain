package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.mutableStateListOf

/**
 * Minimal back stack for platforms where Compose owns navigation
 * (Android, Desktop). On iOS the SwiftUI shell owns the stack instead.
 */
class NavStack(start: TopLevelRoute) {
    val backStack = mutableStateListOf<Route>(start)

    val current: Route get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    fun push(route: Route) {
        backStack.add(route)
    }

    fun pop() {
        if (canGoBack) backStack.removeAt(backStack.lastIndex)
    }
}
