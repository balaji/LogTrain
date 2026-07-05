import SwiftUI
import SharedUI

/// Native SwiftUI replacement for the Compose `RoutineDetailScreen`.
struct RoutineDetailView: View {
    let routineId: Int64
    let title: String

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.title2.bold())
            Text("Routine #\(routineId) — details coming soon")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationTitle(title)
        .toolbarTitleDisplayMode(.inline)
    }
}
