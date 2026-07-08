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
        discarded: false,
        startedAt: 0
    )
    @State private var showExercisePicker = false
    @State private var showDiscardConfirm = false
    @State private var showNothingToSave = false
    @State private var ownSessionObserved = false

    private var sessionStartDate: Date? {
        guard uiState.startedAt > 0 else { return nil }
        return Date(timeIntervalSince1970: Double(uiState.startedAt) / 1000)
    }

    var body: some View {
        Group {
            if let formState, !uiState.loading {
                sessionBody(formState: formState)
            } else {
                ProgressView()
            }
        }
        .task { await bootstrap() }
        .task { await followActiveSession() }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { WorkoutSessionPresentation.shared.sessionScreenDidAppear() }
        .onDisappear { WorkoutSessionPresentation.shared.sessionScreenDidDisappear() }
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text(title)
                        .font(.headline)
                    if let sessionStartDate {
                        Text(sessionStartDate, style: .timer)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .monospacedDigit()
                    }
                }
            }
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
                        if uiState.hasCompletedSets {
                            // Dismissal happens via followActiveSession() once
                            // the session leaves the in-progress state.
                            formState?.finish { }
                        } else {
                            showNothingToSave = true
                        }
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
                // Dismissal happens via followActiveSession() once the
                // session row is deleted.
                formState?.discard { }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This workout and all logged sets will be deleted.")
        }
        .alert("Nothing to save", isPresented: $showNothingToSave) {
            Button("Yes", role: .destructive) {
                formState?.discard { }
            }
            Button("No", role: .cancel) {}
        } message: {
            Text("There are no completed sets to save. Discard?")
        }
    }

    private func bootstrap() async {
        // `.task` restarts on every appearance (tab switches included); only
        // the first run may create/resume a session. Re-running resumeOrStart
        // after the session was discarded elsewhere would silently start a
        // brand-new session from a screen the user is trying to leave.
        if formState == nil {
            do {
                let id = try await WorkoutSessionRepository.shared.resumeOrStart(routineId: routineId)
                sessionId = id.int64Value
                formState = WorkoutSessionFormState(sessionId: id.int64Value)
            } catch {
                // Bootstrap failure is fatal for this screen; falling back to
                // dismiss is friendlier than showing a broken state.
                dismiss()
                return
            }
        }
        guard let formState else { return }
        for await value in formState.uiState {
            uiState = value
        }
    }

    /// Pops this screen as soon as its session stops being the in-progress
    /// one — whether it was finished or discarded here, from the mini bar on
    /// another screen, or while this instance sat off-screen in the
    /// navigation stack. Sole dismissal path for finish/discard, so the
    /// screen and the mini bar both follow the same repository state.
    /// Only acts once it has seen its own session, so an emission for an
    /// unrelated older session can't pop a screen that's still bootstrapping.
    private func followActiveSession() async {
        for await session in WorkoutSessionRepository.shared.activeSession {
            guard let sessionId else { continue }
            if session?.sessionId == sessionId {
                ownSessionObserved = true
            } else if ownSessionObserved {
                dismiss()
            }
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

                    // A set needs at least one rep before it can be completed;
                    // stays enabled while checked so stale rows can be unchecked.
                    let canComplete = set.completed || (Int(set.actualReps) ?? 0) > 0
                    Button {
                        formState.toggleCompleted(
                            exerciseIndex: Int32(exIndex),
                            setIndex: Int32(setIdx)
                        )
                    } label: {
                        Image(systemName: set.completed ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(
                                set.completed
                                    ? Color.accentColor
                                    : Color.secondary.opacity(canComplete ? 1 : 0.3)
                            )
                    }
                    .buttonStyle(.borderless)
                    .disabled(!canComplete)
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
