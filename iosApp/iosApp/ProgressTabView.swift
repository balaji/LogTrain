import SwiftUI

/// Placeholder for the Progress tab: charts and analytical views over the
/// workout history will land here. Named to avoid clashing with SwiftUI's
/// own `ProgressView`.
struct ProgressTabView: View {
    var body: some View {
        ContentUnavailableView(
            "No progress yet",
            systemImage: "chart.line.uptrend.xyaxis",
            description: Text("Charts and analytics over your workouts will appear here.")
        )
    }
}
