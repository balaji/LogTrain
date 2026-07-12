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
        startedAt: 0,
        restSeconds: 0
    )
    @State private var restTimer: RestTimerUiState = RestTimerController.shared.uiState.value
    @State private var showExercisePicker = false
    @State private var showDiscardConfirm = false
    @State private var showNothingToSave = false
    @State private var ownSessionObserved = false
    @State private var reorder = ExerciseReorderModel()
    @State private var plateKeyboard: BarbellKeyboardState?
    @State private var plateNumericMode = false
    @State private var weightUnit: WeightUnit = .metric

    private var sessionStartDate: Date? {
        guard uiState.startedAt > 0 else { return nil }
        return Date(timeIntervalSince1970: Double(uiState.startedAt) / 1000)
    }

    var body: some View {
        ZStack {
            if let formState, !uiState.loading {
                sessionBody(formState: formState)
            } else {
                ProgressView()
            }
            if reorder.activeIndex != nil {
                ExerciseReorderOverlay(model: $reorder, names: uiState.exercises.map { $0.exercise.name })
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .task { await bootstrap() }
        .task { await followActiveSession() }
        .task(id: plateKeyboard != nil) { await followPlateKeyboard() }
        .task {
            for await settings in UserSettingsRepository.shared.settings {
                weightUnit = settings.weightUnit
            }
        }
        .task {
            for await state in RestTimerController.shared.uiState {
                withAnimation(.easeOut(duration: 0.2)) { restTimer = state }
            }
        }
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
                // Session-only rest-timer override, persisted on the session
                // row; the routine and the user default are untouched.
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Picker(L10n.restTimer, selection: Binding(
                            get: {
                                RestTimerOption.allCases.first { $0.seconds == uiState.restSeconds } ?? .s90
                            },
                            set: { formState?.setSessionRestTimer(option: $0) }
                        )) {
                            ForEach(RestTimerOption.allCases, id: \.self) { option in
                                Text(RestTimerFormat.label(option)).tag(option)
                            }
                        }
                    } label: {
                        Image(systemName: "stopwatch")
                    }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .destructive) {
                        // The plate keyboard's weight field is a raw UIKit
                        // UITextField (opaque to SwiftUI's focus system), so
                        // presenting .alert() while it's still first
                        // responder can leave the alert stuck behind the
                        // custom keyboard, unresponsive to taps. Resign
                        // first responder first so the alert always shows on top.
                        dismissKeyboard()
                        showDiscardConfirm = true
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .tint(.red)
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        dismissKeyboard()
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
                .navigationTitle(L10n.addExercise)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(L10n.cancel) { showExercisePicker = false }
                    }
                }
            }
        }
        .alert(L10n.discardWorkoutTitle, isPresented: $showDiscardConfirm) {
            Button(L10n.discard, role: .destructive) {
                // Dismissal happens via followActiveSession() once the
                // session row is deleted.
                formState?.discard { }
            }
            Button(L10n.cancel, role: .cancel) {}
        } message: {
            Text(L10n.discardWorkoutMessage)
        }
        .alert(L10n.nothingToSaveTitle, isPresented: $showNothingToSave) {
            Button(L10n.yes, role: .destructive) {
                formState?.discard { }
            }
            Button(L10n.no, role: .cancel) {}
        } message: {
            Text(L10n.nothingToSaveMessage)
        }
    }

    /// Resigns whatever text field is currently first responder — including
    /// the plate keyboard's UIKit-backed field, which SwiftUI's own focus
    /// system doesn't know about and so won't resign on its own before an
    /// `.alert()` presents.
    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
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
                let state = WorkoutSessionFormState(sessionId: id.int64Value)
                formState = state
                plateKeyboard = BarbellKeyboardState(formState: state)
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

    /// Mirrors the plate keyboard's mode so each `PlateWeightField` swaps
    /// between the plate panel and the system decimal pad.
    private func followPlateKeyboard() async {
        guard let plateKeyboard else { return }
        for await state in plateKeyboard.uiState {
            plateNumericMode = state.numericMode
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
                        formState: formState,
                        plateKeyboard: plateKeyboard,
                        plateNumericMode: plateNumericMode,
                        weightUnit: weightUnit,
                        restTimer: restTimer
                    )
                } header: {
                    HStack {
                        Text(exercise.exercise.name)
                            .font(.headline)
                            .foregroundStyle(Color.accentColor)
                            .textCase(nil)
                            .reorderHandle(
                                onStart: { startY in
                                    withAnimation(.easeOut(duration: 0.15)) {
                                        reorder.begin(index: exIndex, fingerY: startY)
                                    }
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                },
                                onChanged: { y in reorder.update(fingerY: y) },
                                onEnded: { commitReorder() }
                            )
                        Spacer()
                        Menu {
                            Button(role: .destructive) {
                                formState.removeExercise(exerciseIndex: Int32(exIndex))
                            } label: {
                                Label(L10n.deleteExercise, systemImage: "trash")
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .foregroundStyle(.secondary)
                                // Enlarges the tap target without growing the header.
                                .contentShape(Rectangle())
                        }
                        .textCase(nil)
                    }
                }
            }

            Section {
                Button {
                    showExercisePicker = true
                } label: {
                    Label(L10n.addExercise, systemImage: "plus")
                }
            }
        }
        .scrollDisabled(reorder.activeIndex != nil)
    }

    private func commitReorder() {
        if let from = reorder.activeIndex,
           let to = reorder.targetIndex(count: uiState.exercises.count),
           from != to {
            formState?.moveExercise(from: Int32(from), to: Int32(to))
        }
        withAnimation(.easeIn(duration: 0.2)) {
            reorder.activeIndex = nil
        }
    }
}

private struct SessionExerciseSection: View {
    let exIndex: Int
    let exercise: DraftSessionExercise
    let formState: WorkoutSessionFormState
    let plateKeyboard: BarbellKeyboardState?
    let plateNumericMode: Bool
    let weightUnit: WeightUnit
    let restTimer: RestTimerUiState

    private var setsCount: Int { Int(exercise.sets.count) }

    /// Only normal sets take a number; warm-up and drop sets show a marker.
    private func workingSetNumber(_ setIdx: Int) -> Int {
        exercise.sets.prefix(setIdx + 1).filter { $0.setType == .normal }.count
    }

    private func previousLabel(_ setIdx: Int) -> String? {
        guard setIdx < exercise.previousSets.count else { return nil }
        let value = exercise.previousSets[setIdx]
        return value.isEmpty ? nil : value
    }

    private var fields: [SetField] { SetFieldColumns.fields(for: exercise.type) }

    var body: some View {
        Group {
            HStack(spacing: 8) {
                Text(L10n.columnSet).frame(width: 28)
                Text(L10n.columnPrevious).frame(maxWidth: .infinity)
                Text(weightUnit == .imperial ? L10n.columnLb : L10n.columnKg).frame(maxWidth: .infinity)
                ForEach(fields, id: \.self) { field in
                    Text(SetFieldColumns.label(for: field, weightUnit: weightUnit))
                        .frame(maxWidth: .infinity)
                }
                Spacer().frame(width: 28)
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .listRowSeparator(.hidden)

            ForEach(Array(exercise.sets.enumerated()), id: \.offset) { setIdx, set in
                SessionSetRow(
                    workingSetNumber: workingSetNumber(setIdx),
                    previous: previousLabel(setIdx),
                    set: set,
                    fields: fields,
                    readyToComplete: exercise.isSetReadyToComplete(setIndex: Int32(setIdx)),
                    plateKeyboard: exercise.usesPlateKeyboard ? plateKeyboard : nil,
                    plateNumericMode: plateNumericMode,
                    onOpenPlateKeypad: {
                        plateKeyboard?.open(
                            exerciseIndex: Int32(exIndex),
                            setIndex: Int32(setIdx),
                            exerciseId: exercise.exercise.id,
                            currentValue: set.actualWeight
                        )
                    },
                    onSetTypeChange: { setType in
                        formState.setSetType(
                            exerciseIndex: Int32(exIndex),
                            setIndex: Int32(setIdx),
                            setType: setType
                        )
                    },
                    onWeightChange: {
                        formState.updateActualWeight(exerciseIndex: Int32(exIndex), setIndex: Int32(setIdx), value: $0)
                    },
                    onRepsChange: {
                        formState.updateActualReps(exerciseIndex: Int32(exIndex), setIndex: Int32(setIdx), value: $0)
                    },
                    onDurationChange: {
                        formState.updateActualDuration(
                            exerciseIndex: Int32(exIndex),
                            setIndex: Int32(setIdx),
                            value: $0
                        )
                    },
                    onDistanceChange: {
                        formState.updateActualDistance(
                            exerciseIndex: Int32(exIndex),
                            setIndex: Int32(setIdx),
                            value: $0
                        )
                    },
                    onToggleCompleted: {
                        formState.toggleCompleted(exerciseIndex: Int32(exIndex), setIndex: Int32(setIdx))
                    }
                )
                .listRowSeparator(.hidden)
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    // The last remaining set can't be deleted; delete the
                    // exercise instead via its kebab menu.
                    if setsCount > 1 {
                        Button(role: .destructive) {
                            formState.removeSet(exerciseIndex: Int32(exIndex), setIndex: Int32(setIdx))
                        } label: {
                            Label(L10n.delete, systemImage: "trash")
                        }
                    }
                }

                if restTimer.running,
                   restTimer.anchorExerciseId == exercise.id,
                   restTimer.anchorSetIndex == Int32(setIdx) {
                    RestTimerRowView(state: restTimer)
                        .listRowSeparator(.hidden)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }

            Button {
                formState.addSet(exerciseIndex: Int32(exIndex))
            } label: {
                Label(L10n.addSet, systemImage: "plus")
                    .font(.subheadline)
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(Color.accentColor)
            .listRowSeparator(.hidden)
        }
    }
}

/// One set row: set-type cell, previous performance, weight and reps inputs,
/// and a completion checkbox. Turns green and briefly pops when checked.
private struct SessionSetRow: View {
    let workingSetNumber: Int
    let previous: String?
    let set: DraftSessionSet
    /// Measured columns for the owning exercise's type, in display order.
    let fields: [SetField]
    /// Whether the set holds every value its type requires (from the shared
    /// `DraftSessionExercise.isSetReadyToComplete`, so the gating rule lives
    /// in Kotlin).
    let readyToComplete: Bool
    /// Non-nil only for barbell exercises; switches the weight input to the
    /// custom plate keyboard.
    let plateKeyboard: BarbellKeyboardState?
    let plateNumericMode: Bool
    let onOpenPlateKeypad: () -> Void
    let onSetTypeChange: (SetType) -> Void
    let onWeightChange: (String) -> Void
    let onRepsChange: (String) -> Void
    let onDurationChange: (String) -> Void
    let onDistanceChange: (String) -> Void
    let onToggleCompleted: () -> Void

    @State private var pop = false

    var body: some View {
        HStack(spacing: 8) {
            SetTypeCell(
                setType: set.setType,
                workingSetNumber: workingSetNumber,
                width: 28,
                onSetTypeChange: onSetTypeChange
            )

            Text(previous ?? "—")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .frame(maxWidth: .infinity)

            Group {
                if let plateKeyboard {
                    PlateWeightField(
                        placeholder: set.targetWeight.isEmpty ? "0" : set.targetWeight,
                        text: set.actualWeight,
                        numericMode: plateNumericMode,
                        keyboard: plateKeyboard,
                        onOpen: onOpenPlateKeypad,
                        onTextChange: onWeightChange
                    )
                } else {
                    TextField(
                        set.targetWeight.isEmpty ? "0" : set.targetWeight,
                        text: Binding(get: { set.actualWeight }, set: onWeightChange)
                    )
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 6))

            ForEach(fields, id: \.self) { field in
                fieldInput(field)
            }

            // A set needs every field its exercise type requires before it
            // can be completed; stays enabled while checked so stale rows
            // can be unchecked.
            let canComplete = set.completed || readyToComplete
            Button(action: onToggleCompleted) {
                Image(systemName: set.completed ? "checkmark.square.fill" : "square")
                    .foregroundStyle(
                        set.completed
                            ? Color.green
                            : Color.secondary.opacity(canComplete ? 1 : 0.3)
                    )
            }
            .buttonStyle(.borderless)
            .disabled(!canComplete)
            .frame(width: 28)
        }
        .scaleEffect(pop ? 1.04 : 1)
        .listRowBackground(set.completed ? Color.green.opacity(0.15) : nil)
        .onChange(of: set.completed) { _, completed in
            guard completed else { return }
            withAnimation(.easeOut(duration: 0.12)) { pop = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.easeOut(duration: 0.18)) { pop = false }
            }
        }
    }

    /// The input cell for one measured column. Duration is a single h:mm box
    /// — the colon is auto-inserted by the shared filter, so the number pad
    /// suffices; the routine's target shows as the placeholder.
    @ViewBuilder
    private func fieldInput(_ field: SetField) -> some View {
        switch field {
        case .duration:
            actualBox(
                placeholder: set.targetDuration.isEmpty ? L10n.durationPlaceholder : set.targetDuration,
                value: set.actualDuration,
                keyboard: .numberPad,
                onChange: onDurationChange
            )
        case .distance:
            actualBox(
                placeholder: set.targetDistance.isEmpty ? "0" : set.targetDistance,
                value: set.actualDistance,
                keyboard: .decimalPad,
                onChange: onDistanceChange
            )
        default:
            actualBox(
                placeholder: set.targetReps.isEmpty ? "0" : set.targetReps,
                value: set.actualReps,
                keyboard: .numberPad,
                onChange: onRepsChange
            )
        }
    }

    private func actualBox(
        placeholder: String,
        value: String,
        keyboard: UIKeyboardType,
        onChange: @escaping (String) -> Void
    ) -> some View {
        TextField(placeholder, text: Binding(get: { value }, set: onChange))
            .keyboardType(keyboard)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}
