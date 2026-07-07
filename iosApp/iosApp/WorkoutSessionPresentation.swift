import Foundation

/// Cross-view bridge for the active-session mini bar: `WorkoutSessionView`
/// reports whether it's currently the visible screen (so `ContentView` can
/// hide the bar while it's redundant), and `ContentView` asks `ProgramsView`
/// to push the session screen when the bar is tapped from elsewhere in the
/// app. Mirrors the listener-object bridge pattern used for cross-boundary
/// UI requests elsewhere in the app (see AGENTS.md's primary-action note).
@Observable final class WorkoutSessionPresentation {
    static let shared = WorkoutSessionPresentation()
    private init() {}

    var isVisible = false

    private(set) var pendingRoutineId: Int64 = 0
    private(set) var pendingTitle: String = ""
    private(set) var openRequestID = 0

    func requestOpen(routineId: Int64, title: String) {
        pendingRoutineId = routineId
        pendingTitle = title
        openRequestID += 1
    }
}
