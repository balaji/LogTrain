import SwiftUI
import SharedUI

/// Native SwiftUI replacement for the Compose `ExerciseDetailScreen`: shows
/// the exercise read-only with an Edit link that swaps in `ExerciseEditForm`,
/// bound to the shared `ExerciseFormState` via SKIE's Swift `StateFlow` bridge.
struct ExerciseDetailView: View {
    @Environment(\.openURL) private var openURL
    @State private var formState: ExerciseFormState
    @State private var uiState: ExerciseFormUiState
    @State private var editing = false

    init(exerciseId: Int64) {
        let state = ExerciseFormState(exerciseId: exerciseId)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
    }

    private var searchUrl: URL? {
        let query = L10n.describeExerciseQuery(uiState.name)
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return URL(string: "https://google.com/search?q=\(query)")
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
    }
}
