import SwiftUI

/// Native placeholder for logging a workout session against a routine.
struct WorkoutSessionView: View {
    let routineId: Int64
    let title: String

    var body: some View {
        ContentUnavailableView(
            "Workout logging is coming soon",
            systemImage: "figure.strengthtraining.traditional"
        )
        .navigationTitle(title)
    }
}
