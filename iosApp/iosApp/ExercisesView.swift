import SwiftUI
import SharedUI

/// Native SwiftUI replacement for the Compose `ExercisesScreen`, bound to
/// `ExerciseRepository`'s shared `Flow<List<Exercise>>` via SKIE.
struct ExercisesView: View {
    var onExerciseSelected: ((Exercise) -> Void)? = nil

    @State private var exercises: [Exercise]?
    @State private var searchText = ""
    @State private var selectedBodyPart: String?
    @State private var selectedEquipment: String?
    @State private var showingCreateSheet = false

    private var bodyParts: [String] {
        Array(Set((exercises ?? []).compactMap(\.bodyPart))).sorted()
    }

    private var equipmentOptions: [String] {
        Array(Set((exercises ?? []).compactMap(\.equipment))).sorted()
    }

    private var filtered: [Exercise] {
        (exercises ?? []).filter { exercise in
            (selectedBodyPart == nil || exercise.bodyPart == selectedBodyPart) &&
                (selectedEquipment == nil || exercise.equipment == selectedEquipment) &&
                (searchText.isEmpty || exercise.name.localizedCaseInsensitiveContains(searchText))
        }
    }

    var body: some View {
        Group {
            if exercises != nil {
                if filtered.isEmpty {
                    ContentUnavailableView("No exercises match your filters.", systemImage: "magnifyingglass")
                } else {
                    List(filtered, id: \.id) { exercise in
                        if let onSelect = onExerciseSelected {
                            Button {
                                onSelect(exercise)
                            } label: {
                                ExerciseRowLabel(exercise: exercise)
                            }
                            .foregroundStyle(.primary)
                        } else {
                            NavigationLink(value: exercise.id) {
                                ExerciseRowLabel(exercise: exercise)
                            }
                        }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .navigationDestination(for: Int64.self) { id in
            ExerciseDetailView(exerciseId: id)
        }
        .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search exercises")
        .toolbar {
            if onExerciseSelected == nil {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showingCreateSheet = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add Exercise")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Menu("Body part") {
                        Button("Any") { selectedBodyPart = nil }
                        ForEach(bodyParts, id: \.self) { option in
                            Button {
                                selectedBodyPart = option
                            } label: {
                                if selectedBodyPart == option {
                                    Label(option, systemImage: "checkmark")
                                } else {
                                    Text(option)
                                }
                            }
                        }
                    }
                    Menu("Equipment") {
                        Button("Any") { selectedEquipment = nil }
                        ForEach(equipmentOptions, id: \.self) { option in
                            Button {
                                selectedEquipment = option
                            } label: {
                                if selectedEquipment == option {
                                    Label(option, systemImage: "checkmark")
                                } else {
                                    Text(option)
                                }
                            }
                        }
                    }
                } label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                }
            }
        }
        .sheet(isPresented: $showingCreateSheet) {
            ExerciseCreateView()
        }
        .task {
            for await list in ExerciseRepository.shared.exercises {
                exercises = list
            }
        }
    }
}

/// Modal creation form presented from the "+" toolbar button, backed by a
/// shared `ExerciseFormState` in create mode (no exercise id).
private struct ExerciseCreateView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var formState: ExerciseFormState
    @State private var uiState: ExerciseFormUiState

    init() {
        let state = ExerciseFormState()
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
    }

    var body: some View {
        NavigationStack {
            ExerciseEditForm(uiState: uiState, formState: formState, onDone: { dismiss() })
                .navigationTitle("New Exercise")
                .toolbarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                }
        }
        .task {
            for await state in formState.uiState {
                uiState = state
            }
        }
    }
}

private struct ExerciseRowLabel: View {
    let exercise: Exercise

    var body: some View {
        VStack(alignment: .leading) {
            Text(exercise.name)
            let details = [exercise.bodyPart, exercise.equipment].compactMap { $0 }
                .map(\.firstCapitalized)
            if !details.isEmpty {
                Text(details.joined(separator: " • "))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
