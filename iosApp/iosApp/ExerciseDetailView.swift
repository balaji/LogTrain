import SwiftUI
import SharedUI
import Charts

private enum HistoryMetric: String, CaseIterable {
    case oneRm, volume, duration, distance
    var label: String {
        switch self {
        case .oneRm: return L10n.exerciseHistoryMetric1rm
        case .volume: return L10n.exerciseHistoryMetricVolume
        case .duration: return L10n.exerciseHistoryMetricDuration
        case .distance: return L10n.exerciseHistoryMetricDistance
        }
    }
}

/// Native SwiftUI replacement for the Compose `ExerciseDetailScreen`: shows
/// the exercise read-only with an Edit link that swaps in `ExerciseEditForm`,
/// bound to the shared `ExerciseFormState` via SKIE's Swift `StateFlow` bridge.
struct ExerciseDetailView: View {
    @Environment(\.openURL) private var openURL
    @State private var formState: ExerciseFormState
    @State private var uiState: ExerciseFormUiState
    @State private var historyState: ExerciseHistoryState
    @State private var historyUiState = ExerciseHistoryUiState(
        loading: true, weeklyOneRm: [], weeklyVolume: [], weeklyDuration: [], weeklyDistance: [], hasAnyData: false
    )
    @State private var editing = false
    @State private var selectedMetric: HistoryMetric = .oneRm

    init(exerciseId: Int64) {
        let fs = ExerciseFormState(exerciseId: exerciseId)
        _formState = State(initialValue: fs)
        _uiState = State(initialValue: fs.uiState.value)
        _historyState = State(initialValue: ExerciseHistoryState(exerciseId: exerciseId))
    }

    private var searchUrl: URL? {
        let query = L10n.describeExerciseQuery(uiState.name)
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "https://google.com/search?q=\(query)")
    }

    private var applicableMetrics: [HistoryMetric] {
        let type = uiState.exerciseType
        let showOneRmVolume = type == nil || ["strength", "reps_duration", "reps_distance"].contains(type!)
        let showDuration = type == nil || ["duration", "reps_duration", "distance_duration"].contains(type!)
        let showDistance = type == nil || ["distance", "reps_distance", "distance_duration"].contains(type!)
        var metrics: [HistoryMetric] = []
        if showOneRmVolume { metrics += [.oneRm, .volume] }
        if showDuration { metrics.append(.duration) }
        if showDistance { metrics.append(.distance) }
        return metrics
    }

    private var selectedPoints: [SparkPoint] {
        switch selectedMetric {
        case .oneRm: return historyUiState.weeklyOneRm
        case .volume: return historyUiState.weeklyVolume
        case .duration: return historyUiState.weeklyDuration
        case .distance: return historyUiState.weeklyDistance
        }
    }

    var body: some View {
        Group {
            if uiState.loading {
                ProgressView()
            } else if editing {
                ExerciseEditForm(uiState: uiState, formState: formState, onDone: { editing = false })
            } else {
                List {
                    if let searchUrl {
                        Section {
                            Button(L10n.searchInGoogle) { openURL(searchUrl) }
                        }
                    }

                    Section {
                        LabeledContent(L10n.exerciseType, value: uiState.exerciseType.map(L10n.exerciseCatalogLabel) ?? "—")
                        LabeledContent(L10n.bodyPart, value: uiState.bodyPart.map(L10n.exerciseCatalogLabel) ?? "—")
                        LabeledContent(L10n.equipment, value: uiState.equipment.map(L10n.exerciseCatalogLabel) ?? "—")
                    }

                    if !historyUiState.loading {
                        Section(L10n.exerciseHistorySection) {
                            if !historyUiState.hasAnyData {
                                Text(L10n.exerciseHistoryNoData)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            } else {
                                let nonZeroPoints = selectedPoints.filter { $0.value > 0 }
                                if applicableMetrics.count > 1 {
                                    Picker("", selection: $selectedMetric) {
                                        ForEach(applicableMetrics, id: \.self) { metric in
                                            Text(metric.label).tag(metric)
                                        }
                                    }
                                    .pickerStyle(.segmented)
                                }
                                if nonZeroPoints.count >= 2 {
                                    Chart {
                                        ForEach(nonZeroPoints, id: \.epochDay) { point in
                                            LineMark(
                                                x: .value("Week", point.epochDay),
                                                y: .value("Value", point.value)
                                            )
                                            .interpolationMethod(.monotone)
                                        }
                                    }
                                    .chartXAxis(.hidden)
                                    .chartYAxis {
                                        AxisMarks(position: .leading, values: .automatic(desiredCount: 3))
                                    }
                                    .chartXScale(domain: xDomain(nonZeroPoints))
                                    .chartYScale(domain: yDomain(nonZeroPoints))
                                    .frame(height: 80)
                                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                                } else {
                                    Text(L10n.exerciseHistoryNoData)
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
                .toolbar {
                    if !uiState.builtin {
                        ToolbarItem(placement: .primaryAction) {
                            Button(L10n.edit) { editing = true }
                        }
                    }
                }
            }
        }
        .navigationTitle(uiState.name.isEmpty ? L10n.exercise : uiState.name)
        .toolbarTitleDisplayMode(.inline)
        .task {
            for await state in formState.uiState {
                uiState = state
            }
        }
        .task {
            for await state in historyState.uiState {
                historyUiState = state
                if !applicableMetrics.contains(selectedMetric), let first = applicableMetrics.first {
                    selectedMetric = first
                }
            }
        }
    }

    private func xDomain(_ points: [SparkPoint]) -> ClosedRange<Int64> {
        let days = points.map(\.epochDay)
        let lo = days.min() ?? 0
        let hi = days.max() ?? 1
        return lo...(hi > lo ? hi : lo + 1)
    }

    private func yDomain(_ points: [SparkPoint]) -> ClosedRange<Double> {
        let values = points.map(\.value)
        let lo = values.min() ?? 0
        let hi = values.max() ?? 1
        let pad = Swift.max((hi - lo) * 0.1, 0.5)
        return (lo - pad)...(hi + pad)
    }
}
