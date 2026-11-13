import Foundation
import SwiftUI
import SharedUI

/// Navigation value for pushing the workout session screen. Shared by every
/// entry point (Programs list, routine detail's play button, the mini bar)
/// so they all land on the single `navigationDestination` in `ProgramsView`.
struct WorkoutSessionTarget: Hashable {
    let routineId: Int64
    let title: String
}

/// Cross-view bridge for the active-session mini bar: `WorkoutSessionView`
/// reports whether it's currently the visible screen (so `ContentView` can
/// hide the bar while it's redundant), and `ContentView` asks `ProgramsView`
/// to push the session screen when the bar is tapped from elsewhere in the
/// app. Mirrors the listener-object bridge pattern used for cross-boundary
/// UI requests elsewhere in the app (see AGENTS.md's primary-action note).
@Observable final class WorkoutSessionPresentation {
    static let shared = WorkoutSessionPresentation()
    private init() {}

    /// Number of workout session screens currently on screen. A counter
    /// rather than a boolean: during a push SwiftUI fires the incoming
    /// screen's `onAppear` before the outgoing screen's `onDisappear`, so
    /// with two session screens in flight a boolean would end up `false`
    /// while one is still visible.
    private var visibleCount = 0

    var isSessionScreenVisible: Bool { visibleCount > 0 }

    func sessionScreenDidAppear() { visibleCount += 1 }
    func sessionScreenDidDisappear() { visibleCount = max(0, visibleCount - 1) }

    private(set) var pendingTarget = WorkoutSessionTarget(routineId: 0, title: "")
    private(set) var openRequestID = 0
    private(set) var hasPendingOpen = false

    func requestOpen(routineId: Int64, title: String) {
        pendingTarget = WorkoutSessionTarget(routineId: routineId, title: title)
        hasPendingOpen = true
        openRequestID += 1
    }

    func consumePendingOpen() -> WorkoutSessionTarget? {
        guard hasPendingOpen else { return nil }
        hasPendingOpen = false
        return pendingTarget
    }
}

/// Prompts the user to dismiss or discard when starting a workout while another
/// workout is already in progress, then invokes `onProceed` after discarding.
struct WorkoutInProgressAlertModifier: ViewModifier {
    @Binding var pendingTarget: WorkoutSessionTarget?
    let onProceed: @MainActor (WorkoutSessionTarget) -> Void

    func body(content: Content) -> some View {
        content
            .alert(
                L10n.workoutInProgressTitle,
                isPresented: Binding(
                    get: { pendingTarget != nil },
                    set: { if !$0 { pendingTarget = nil } }
                ),
                presenting: pendingTarget
            ) { target in
                Button(L10n.discardWorkout, role: .destructive) {
                    pendingTarget = nil
                    Task { @MainActor in
                        if let session = WorkoutSessionRepository.shared.activeSession.value {
                            try? await WorkoutSessionRepository.shared.discard(sessionId: session.sessionId)
                        }
                        onProceed(target)
                    }
                }
                Button(L10n.cancel, role: .cancel) {
                    pendingTarget = nil
                }
            }
    }
}

extension View {
    func workoutInProgressAlert(
        pendingTarget: Binding<WorkoutSessionTarget?>,
        onProceed: @escaping @MainActor (WorkoutSessionTarget) -> Void
    ) -> some View {
        modifier(WorkoutInProgressAlertModifier(pendingTarget: pendingTarget, onProceed: onProceed))
    }
}

@MainActor
func requestStartWorkout(
    _ target: WorkoutSessionTarget,
    pendingTarget: Binding<WorkoutSessionTarget?>,
    onProceed: @MainActor (WorkoutSessionTarget) -> Void
) {
    if WorkoutSessionRepository.shared.activeSession.value != nil {
        pendingTarget.wrappedValue = target
    } else {
        onProceed(target)
    }
}
