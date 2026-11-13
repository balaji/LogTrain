package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.mutableStateListOf

/**
 * Minimal back stack for platforms where Compose owns navigation
 * (Android). On iOS the SwiftUI shell owns the stack instead.
 */
class NavStack(start: Route) {
    val backStack = mutableStateListOf<Route>(start)

    val current: Route get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    fun push(route: Route) {
        backStack.add(route)
    }

    fun pop() {
        if (canGoBack) backStack.removeAt(backStack.lastIndex)
    }

    fun popToRoot() {
        if (backStack.size > 1) backStack.removeRange(1, backStack.size)
    }

    /** Removes every matching entry; the root is never removed. */
    fun removeAll(predicate: (Route) -> Boolean) {
        for (i in backStack.lastIndex downTo 1) {
            if (predicate(backStack[i])) backStack.removeAt(i)
        }
    }
}
