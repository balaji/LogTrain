import SwiftUI
import SharedUI

/// Read-only summary of the completed workout session(s) performed on a given
/// calendar day, bound to the shared `DayDetailState` via SKIE's Swift
/// `StateFlow` bridge.
struct DayDetailView: View {
    let epochDay: Int64
    @State private var state: DayDetailState
    @State private var uiState: DayDetailUiState

    init(epochDay: Int64) {
        self.epochDay = epochDay
        let state = DayDetailState(epochDay: epochDay)
        _state = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
    }

    var body: some View {
        Group {
            if uiState.loading {
                ProgressView()
            } else if uiState.sessions.isEmpty {
                ContentUnavailableView("No completed workouts", systemImage: "calendar")
            } else {
                List {
                    ForEach(uiState.sessions, id: \.id) { session in
                        Section(session.routineName ?? "Workout") {
                            ForEach(Array(session.exercises.enumerated()), id: \.offset) { _, exercise in
                                ExerciseSummaryRow(exercise: exercise)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(dayTitle)
        .toolbarTitleDisplayMode(.inline)
        .task {
            for await value in state.uiState {
                uiState = value
            }
        }
    }

    private var dayTitle: String {
        let date = Date(timeIntervalSince1970: Double(epochDay) * 86400)
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter.string(from: date)
    }
}

private struct ExerciseSummaryRow: View {
    let exercise: DaySessionExercise

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(exercise.name)
                .font(.body)
            ForEach(Array(exercise.sets.enumerated()), id: \.offset) { index, set in
                HStack {
                    Text("Set \(index + 1)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(summary(for: set))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(.vertical, 2)
    }

    private func summary(for set: DaySessionSet) -> String {
        let weight = set.actualWeight.isEmpty ? set.targetWeight : set.actualWeight
        let reps = set.actualReps.isEmpty ? set.targetReps : set.actualReps
        var parts: [String] = []
        if !weight.isEmpty { parts.append("\(weight) kg") }
        if !reps.isEmpty { parts.append("\(reps) reps") }
        return parts.isEmpty ? "—" : parts.joined(separator: " · ")
    }
}
