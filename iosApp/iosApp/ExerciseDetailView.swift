import SwiftUI
import SharedUI

/// Native SwiftUI replacement for the Compose `ExerciseDetailScreen`: shows
/// the exercise read-only with an Edit link that swaps in `ExerciseEditForm`,
/// bound to the shared `ExerciseFormState` via SKIE's Swift `StateFlow` bridge.
struct ExerciseDetailView: View {
    @State private var formState: ExerciseFormState
    @State private var uiState: ExerciseFormUiState
    @State private var editing = false

    init(exerciseId: Int64) {
        let state = ExerciseFormState(exerciseId: exerciseId)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
    }

    var body: some View {
        Group {
            if uiState.loading {
                ProgressView()
            } else if editing {
                ExerciseEditForm(uiState: uiState, formState: formState, onDone: { editing = false })
            } else {
                List {
                    Section {
                        HStack {
                            Spacer()
                            Image(systemName: "dumbbell")
                                .font(.system(size: 48))
                                .foregroundStyle(.secondary)
                            Spacer()
                        }
                        .frame(height: 160)
                        .listRowBackground(Color(.secondarySystemBackground))
                    }

                    if !uiState.descriptionText.isEmpty {
                        Section {
                            Text(uiState.descriptionText)
                        }
                    }

                    Section {
                        LabeledContent("Type", value: uiState.exerciseType ?? "—")
                        LabeledContent("Body part", value: uiState.bodyPart ?? "—")
                        LabeledContent("Equipment", value: uiState.equipment ?? "—")
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        Button("Edit") { editing = true }
                    }
                }
            }
        }
        .navigationTitle(uiState.name.isEmpty ? "Exercise" : uiState.name)
        .toolbarTitleDisplayMode(.inline)
        .task {
            for await state in formState.uiState {
                uiState = state
            }
        }
    }
}
