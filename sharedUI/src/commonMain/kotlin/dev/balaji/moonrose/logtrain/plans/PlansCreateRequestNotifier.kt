package dev.balaji.moonrose.logtrain.plans

/**
 * Lets the SwiftUI toolbar "+" button (which lives outside Compose on iOS)
 * ask the Plans list to open its create-plan dialog.
 */
object PlansCreateRequestNotifier {
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun requestCreate() {
        listeners.toList().forEach { it() }
    }
}
