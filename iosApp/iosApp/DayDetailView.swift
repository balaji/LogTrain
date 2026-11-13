import SwiftUI
import SharedUI

/// Read-only summary of the completed workout session(s) performed on a given
/// calendar day, bound to the shared `DayDetailState` via SKIE's Swift
/// `Flow` bridge. The flow is cold and collected in `.task`, so the page
/// observes the database while visible and refreshes when it reappears —
/// e.g. after a session is finished from the mini bar on another tab.
struct DayDetailView: View {
    @Environment(ChatCoordinator.self) private var chatCoordinator
    let epochDay: Int64
    @State private var state: DayDetailState
    @State private var uiState = DayDetailUiState(loading: true, sessions: [], weightUnit: .metric)

    init(epochDay: Int64) {
        self.epochDay = epochDay
        _state = State(initialValue: DayDetailState(epochDay: epochDay))
    }

    var body: some View {
        Group {
            if uiState.loading {
                ProgressView()
            } else if uiState.sessions.isEmpty {
                ContentUnavailableView(L10n.noCompletedWorkouts, systemImage: "calendar")
            } else {
                List {
                    if chatCoordinator.isEntryVisible {
                        Section {
                            Button {
                                chatCoordinator.openForDay(epochDay: epochDay)
                            } label: {
                                Label(L10n.chatAskWorkout, systemImage: "sparkles")
                            }
                        }
                    }

                    ForEach(uiState.sessions, id: \.id) { session in
                        Section(session.routineName ?? L10n.workout) {
                            ForEach(Array(session.exercises.enumerated()), id: \.offset) { _, exercise in
                                ExerciseSummaryRow(exercise: exercise, weightUnit: uiState.weightUnit)
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
    let weightUnit: WeightUnit

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(exercise.name)
                .font(.body)
            ForEach(Array(exercise.sets.enumerated()), id: \.offset) { index, set in
                HStack {
                    Text(label(at: index, set))
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

    /// Only normal sets take a number; warm-up and drop sets are named instead,
    /// matching the Compose day detail.
    private func label(at index: Int, _ set: DaySessionSet) -> String {
        guard set.setType == .normal else { return L10n.setTypeLabel(set.setType) }
        let workingNumber = exercise.sets.prefix(index + 1).filter { $0.setType == .normal }.count
        return L10n.setNumber(workingNumber)
    }

    private func summary(for set: DaySessionSet) -> String {
        let weight = set.actualWeight.isEmpty ? set.targetWeight : set.actualWeight
        let reps = set.actualReps.isEmpty ? set.targetReps : set.actualReps
        let duration = set.actualDuration.isEmpty ? set.targetDuration : set.actualDuration
        let distance = set.actualDistance.isEmpty ? set.targetDistance : set.actualDistance
        var parts: [String] = []
        if !weight.isEmpty {
            parts.append(weightUnit == .metric ? L10n.weightKgValue(weight) : L10n.weightLbValue(weight))
        }
        if !reps.isEmpty { parts.append(L10n.repsValue(reps)) }
        if !duration.isEmpty { parts.append(L10n.durationValue(duration)) }
        if !distance.isEmpty {
            parts.append(weightUnit == .metric ? L10n.distanceKmValue(distance) : L10n.distanceMiValue(distance))
        }
        return parts.isEmpty ? "—" : parts.joined(separator: " · ")
    }
}
