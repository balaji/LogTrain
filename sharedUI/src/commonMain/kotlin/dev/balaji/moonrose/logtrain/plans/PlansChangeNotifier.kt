package dev.balaji.moonrose.logtrain.plans

/**
 * Cross-screen change signal for plan/routine mutations, mirroring the
 * LoginController listener idiom. Needed because on iOS the tab-root
 * ComposeUIViewController stays alive under SwiftUI's NavigationStack, so a
 * list screen is never recomposed on pop-back and would otherwise go stale
 * after a detail screen mutates data.
 */
object PlansChangeNotifier {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.toList().forEach { it() }
    }
}
