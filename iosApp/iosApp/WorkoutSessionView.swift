import SwiftUI
import SharedUI

/// Native SwiftUI workout session screen. Creates or resumes an in-progress
/// session on appearance, then binds to the KMP-shared `WorkoutSessionFormState`.
struct WorkoutSessionView: View {
    @Environment(\.dismiss) private var dismiss
    let routineId: Int64
    let title: String

    @State private var sessionId: Int64?
    @State private var formState: WorkoutSessionFormState?
    @State private var uiState: WorkoutSessionUiState = WorkoutSessionUiState(
        loading: true,
        exercises: [],
        saving: false,
        finished: false,
        discarded: false
    )
    @State private var showExercisePicker = false
    @State private var showDiscardConfirm = false

    var body: some View {
        Group {
            if let formState, !uiState.loading {
                sessionBody(formState: formState)
            } else {
                ProgressView()
            }
        }
        .task { await bootstrap() }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if formState != nil {
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .destructive) {
                        showDiscardConfirm = true
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .tint(.red)
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        formState?.finish { dismiss() }
                    } label: {
                        if uiState.saving {
                            ProgressView()
                        } else {
                            Image(systemName: "checkmark")
                        }
                    }
                    .disabled(uiState.saving || uiState.loading)
                }
            }
        }
        .sheet(isPresented: $showExercisePicker) {
            NavigationStack {
                ExercisesView(onExerciseSelected: { exercise in
                    formState?.addExercise(exercise: exercise)
                    showExercisePicker = false
                })
                .navigationTitle("Add Exercise")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showExercisePicker = false }
                    }
                }
            }
        }
        .alert("Discard workout?", isPresented: $showDiscardConfirm) {
            Button("Discard", role: .destructive) {
                formState?.discard { dismiss() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This workout and all logged sets will be deleted.")
        }
    }

    private func bootstrap() async {
        do {
            let id = try await WorkoutSessionRepository.shared.resumeOrStart(routineId: routineId)
            let state = WorkoutSessionFormState(sessionId: id.int64Value)
            sessionId = id.int64Value
            formState = state
            for await value in state.uiState {
                uiState = value
            }
        } catch {
            // Bootstrap failure is fatal for this screen; falling back to dismiss
            // is friendlier than showing a broken state.
            dismiss()
        }
    }

    @ViewBuilder
    private func sessionBody(formState: WorkoutSessionFormState) -> some View {
        List {
            ForEach(Array(uiState.exercises.enumerated()), id: \.element.id) { exIndex, exercise in
                Section {
                    SessionExerciseSection(
                        exIndex: exIndex,
                        exercise: exercise,
                        formState: formState
                    )
                } header: {
                    Text(exercise.exercise.name)
                        .font(.headline)
                        .foregroundStyle(Color.accentColor)
                        .textCase(nil)
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        formState.removeExercise(exerciseIndex: Int32(exIndex))
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
            }
            .onMove { source, destination in
                guard let from = source.first else { return }
                let to = destination > from ? destination - 1 : destination
                formState.moveExercise(from: Int32(from), to: Int32(to))
            }

            Section {
                Button {
                    showExercisePicker = true
                } label: {
                    Label("Add Exercise", systemImage: "plus")
                }
            }
        }
        // Reordering (and deleting, via swipeActions above) should always be
        // available on an in-progress session, matching sharedUI's Android
        // behavior — there's no separate "edit mode" to toggle into.
        .environment(\.editMode, .constant(.active))
    }
}

private struct SessionExerciseSection: View {
    let exIndex: Int
    let exercise: DraftSessionExercise
    let formState: WorkoutSessionFormState

    private var setsCount: Int { Int(exercise.sets.count) }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("SET").frame(width: 28)
                Text("KG").frame(maxWidth: .infinity)
                Text("REPS").frame(maxWidth: .infinity)
                Spacer().frame(width: 36)
                Spacer().frame(width: 24)
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            ForEach(Array(exercise.sets.enumerated()), id: \.offset) { setIdx, set in
                HStack(spacing: 8) {
                    Text("\(setIdx + 1)")
                        .frame(width: 28)
                        .foregroundStyle(.secondary)

                    TextField(
                        set.targetWeight.isEmpty ? "0" : set.targetWeight,
                        text: Binding(
                            get: { set.actualWeight },
                            set: { formState.updateActualWeight(
                                exerciseIndex: Int32(exIndex),
                                setIndex: Int32(setIdx),
                                value: $0
                            ) }
                        )
                    )
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                    TextField(
                        set.targetReps.isEmpty ? "0" : set.targetReps,
                        text: Binding(
                            get: { set.actualReps },
                            set: { formState.updateActualReps(
                                exerciseIndex: Int32(exIndex),
                                setIndex: Int32(setIdx),
                                value: $0
                            ) }
                        )
                    )
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                    Button {
                        formState.toggleCompleted(
                            exerciseIndex: Int32(exIndex),
                            setIndex: Int32(setIdx)
                        )
                    } label: {
                        Image(systemName: set.completed ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(set.completed ? Color.accentColor : Color.secondary)
                    }
                    .buttonStyle(.borderless)
                    .frame(width: 24)

                    Button {
                        formState.removeSet(
                            exerciseIndex: Int32(exIndex),
                            setIndex: Int32(setIdx)
                        )
                    } label: {
                        Image(systemName: "minus.circle")
                            .foregroundStyle(setsCount > 1 ? Color.secondary : Color.secondary.opacity(0.3))
                    }
                    .buttonStyle(.borderless)
                    .disabled(setsCount <= 1)
                    .frame(width: 32)
                }
            }

            Button {
                formState.addSet(exerciseIndex: Int32(exIndex))
            } label: {
                Label("Add Set", systemImage: "plus")
                    .font(.subheadline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(Color.accentColor)
        }
        .padding(.vertical, 4)
    }
}
