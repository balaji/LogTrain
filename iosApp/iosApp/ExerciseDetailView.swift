import SwiftUI
import SharedUI

/// Native SwiftUI replacement for the Compose `ExerciseDetailScreen`: shows
/// the exercise read-only with an Edit link that swaps in `ExerciseEditForm`,
/// bound to the shared `ExerciseFormState` via SKIE's Swift `StateFlow` bridge.
struct ExerciseDetailView: View {
    @State private var formState: ExerciseFormState
    @State private var uiState: ExerciseFormUiState
    @State private var editing = false
    @State private var showingSearch = false

    init(exerciseId: Int64) {
        let state = ExerciseFormState(exerciseId: exerciseId)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
    }

    private var searchUrl: URL? {
        let query = "Describe the exercise \(uiState.name)"
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
                            Button("Search in Google") { showingSearch = true }
                        }
                        .sheet(isPresented: $showingSearch) {
                            SafariView(url: searchUrl)
                                .ignoresSafeArea()
                        }
                    }

                    Section {
                        LabeledContent("Type", value: uiState.exerciseType?.firstCapitalized ?? "—")
                        LabeledContent("Body part", value: uiState.bodyPart?.firstCapitalized ?? "—")
                        LabeledContent("Equipment", value: uiState.equipment?.firstCapitalized ?? "—")
                    }
                }
                .toolbar {
                    if !uiState.builtin {
                        ToolbarItem(placement: .primaryAction) {
                            Button("Edit") { editing = true }
                        }
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
