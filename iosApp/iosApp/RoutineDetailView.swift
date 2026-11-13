import SwiftUI
import SharedUI

struct RoutineDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let routineId: Int64
    let isNew: Bool
    let onStartWorkout: (WorkoutSessionTarget) -> Void
    @State private var isEditing: Bool
    @State private var formState: RoutineFormState
    @State private var uiState: RoutineDetailUiState
    @State private var formVersion = 0
    @State private var nameBeforeEdit = ""
    @State private var showExercisePicker = false
    @State private var weightUnit: WeightUnit = .metric
    @State private var pendingWorkoutTarget: WorkoutSessionTarget?
    @State private var hasActiveSession = WorkoutSessionRepository.shared.activeSession.value != nil

    @State private var reorder = ExerciseReorderModel()

    init(
        routineId: Int64,
        title: String,
        isNew: Bool = false,
        onStartWorkout: @escaping (WorkoutSessionTarget) -> Void = { _ in }
    ) {
        self.routineId = routineId
        self.isNew = isNew
        self.onStartWorkout = onStartWorkout
        let state = RoutineFormState(routineId: routineId, initialName: title)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
        _isEditing = State(initialValue: isNew)
    }

    var body: some View {
        ZStack {
            if uiState.loading {
                ProgressView()
            } else if isEditing {
                routineForm
            } else {
                routineReadOnly
            }
            if isEditing && reorder.activeIndex != nil {
                ExerciseReorderOverlay(model: $reorder, names: uiState.workSets.map { $0.exercise.name })
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .navigationTitle(uiState.name.isEmpty ? L10n.routine : uiState.name)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(isEditing && !isNew)
        .workoutInProgressAlert(pendingTarget: $pendingWorkoutTarget, onProceed: onStartWorkout)
        .toolbar {
            if isEditing {
                if !isNew {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(L10n.cancel) {
                            formState = RoutineFormState(routineId: routineId, initialName: nameBeforeEdit)
                            formVersion += 1
                            isEditing = false
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if uiState.saving {
                        ProgressView()
                    } else {
                        Button(L10n.save) {
                            let trimmed = uiState.name.trimmingCharacters(in: .whitespaces)
                            let savedName = trimmed.isEmpty ? L10n.routine : trimmed
                            formState.save {
                                formState = RoutineFormState(routineId: routineId, initialName: savedName)
                                formVersion += 1
                                isEditing = false
                            }
                        }
                    }
                }
            } else {
                ToolbarItem(placement: .primaryAction) {
                    Button(L10n.edit) {
                        nameBeforeEdit = uiState.name
                        isEditing = true
                    }
                }
            }
        }
        .sheet(isPresented: $showExercisePicker) {
            NavigationStack {
                ExercisesView(onExerciseSelected: { exercise in
                    formState.addExercise(exercise: exercise)
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
        .task(id: formVersion) {
            for await state in formState.uiState {
                uiState = state
            }
        }
        .task {
            for await settings in UserSettingsRepository.shared.settings {
                weightUnit = settings.weightUnit
            }
        }
        .task {
            for await session in WorkoutSessionRepository.shared.activeSession {
                hasActiveSession = (session != nil)
            }
        }
    }

    // MARK: - Read-only view

    private var routineReadOnly: some View {
        ZStack(alignment: .bottom) {
            List {
                if uiState.workSets.isEmpty {
                    Text(L10n.routineNoExercises)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(Array(uiState.workSets.enumerated()), id: \.element.id) { _, workSet in
                        Text(L10n.setsByExercise(workSet.sets.count, workSet.exercise.name))
                    }
                }
            }

            if !uiState.workSets.isEmpty {
                Button {
                    requestStartWorkout(
                        WorkoutSessionTarget(
                            routineId: routineId,
                            title: uiState.name.isEmpty ? L10n.workout : uiState.name
                        ),
                        pendingTarget: $pendingWorkoutTarget,
                        onProceed: onStartWorkout
                    )
                } label: {
                    Image(systemName: "play.fill")
                        .font(.title2)
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(Color.blue)
                        .clipShape(Circle())
                        .shadow(color: .blue.opacity(0.3), radius: 8, y: 4)
                }
                .padding(.bottom, hasActiveSession ? 92 : 24)
                .animation(.easeInOut(duration: 0.2), value: hasActiveSession)
            }
        }
    }

    // MARK: - Editable form

    private var routineForm: some View {
        List {
            ForEach(Array(uiState.workSets.enumerated()), id: \.element.id) { wsIndex, workSet in
                ExerciseCardView(
                    wsIndex: wsIndex,
                    workSet: workSet,
                    formState: formState,
                    weightUnit: weightUnit,
                    onReorderStart: { startY in
                        withAnimation(.easeOut(duration: 0.15)) {
                            reorder.begin(index: wsIndex, fingerY: startY)
                        }
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    },
                    onReorderChanged: { y in reorder.update(fingerY: y) },
                    onReorderEnded: { commitReorder() }
                )
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        formState.removeExercise(workSetIndex: Int32(wsIndex))
                    } label: {
                        Label(L10n.delete, systemImage: "trash")
                    }
                }
            }

            Button {
                showExercisePicker = true
            } label: {
                Label(L10n.addExercise, systemImage: "plus")
            }
        }
        .scrollDisabled(reorder.activeIndex != nil)
    }

    private func commitReorder() {
        if let from = reorder.activeIndex,
           let to = reorder.targetIndex(count: uiState.workSets.count),
           from != to {
            formState.moveExercise(from: Int32(from), to: Int32(to))
        }
        withAnimation(.easeIn(duration: 0.2)) {
            reorder.activeIndex = nil
        }
    }
}

// MARK: - Editable exercise card

private struct ExerciseCardView: View {
    let wsIndex: Int
    let workSet: DraftWorkSet
    let formState: RoutineFormState
    let weightUnit: WeightUnit
    var onReorderStart: (CGFloat) -> Void
    var onReorderChanged: (CGFloat) -> Void
    var onReorderEnded: () -> Void

    /// Only normal sets take a number; warm-up and drop sets show a marker.
    private func workingSetNumber(_ setIdx: Int) -> Int {
        workSet.sets.prefix(setIdx + 1).filter { $0.setType == .normal }.count
    }

    private var fields: [SetField] { SetFieldColumns.fields(for: workSet.type) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(workSet.exercise.name)
                .font(.headline)
                .foregroundStyle(Color.accentColor)
                .padding(.vertical, 6)
                .reorderHandle(
                    onStart: onReorderStart,
                    onChanged: onReorderChanged,
                    onEnded: onReorderEnded
                )

            HStack {
                Text(L10n.columnSet)
                    .frame(width: 28)
                Text(weightUnit == .imperial ? L10n.columnLb : L10n.columnKg)
                    .frame(maxWidth: .infinity)
                ForEach(fields, id: \.self) { field in
                    Text(SetFieldColumns.label(for: field, weightUnit: weightUnit))
                        .frame(maxWidth: .infinity)
                }
                Spacer().frame(width: 36)
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.bottom, 4)

            ForEach(Array(workSet.sets.enumerated()), id: \.offset) { setIdx, set in
                HStack(spacing: 8) {
                    SetTypeCell(
                        setType: set.setType,
                        workingSetNumber: workingSetNumber(setIdx),
                        width: 28,
                        onSetTypeChange: { setType in
                            formState.setSetType(
                                workSetIndex: Int32(wsIndex),
                                setIndex: Int32(setIdx),
                                setType: setType
                            )
                        }
                    )

                    TextField(
                        "0",
                        text: Binding(
                            get: { set.weight },
                            set: {
                                formState.updateWeight(
                                    workSetIndex: Int32(wsIndex),
                                    setIndex: Int32(setIdx),
                                    value: $0
                                )
                            }
                        )
                    )
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                    ForEach(fields, id: \.self) { field in
                        fieldInput(field, setIdx: setIdx, set: set)
                    }

                    Button {
                        formState.removeSet(
                            workSetIndex: Int32(wsIndex),
                            setIndex: Int32(setIdx)
                        )
                    } label: {
                        Image(systemName: "minus.circle")
                            .foregroundStyle(
                                workSet.sets.count > 1 ? Color.secondary : Color.secondary.opacity(0.3)
                            )
                    }
                    .buttonStyle(.borderless)
                    .disabled(workSet.sets.count <= 1)
                    .frame(width: 32)
                }
                .padding(.vertical, 4)
            }

            Button {
                formState.addSet(workSetIndex: Int32(wsIndex))
            } label: {
                Label(L10n.addSet, systemImage: "plus")
                    .font(.subheadline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(Color.accentColor)
        }
        .padding(.vertical, 4)
    }

    /// The input cell for one measured column of a set row. Duration is a
    /// single h:mm box — the colon is auto-inserted by the shared filter, so
    /// the number pad suffices.
    @ViewBuilder
    private func fieldInput(_ field: SetField, setIdx: Int, set: DraftSet) -> some View {
        switch field {
        case .duration:
            TextField(
                L10n.durationPlaceholder,
                text: Binding(
                    get: { set.duration },
                    set: {
                        formState.updateDuration(
                            workSetIndex: Int32(wsIndex),
                            setIndex: Int32(setIdx),
                            value: $0
                        )
                    }
                )
            )
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 6))
        case .distance:
            TextField(
                "0",
                text: Binding(
                    get: { set.distance },
                    set: {
                        formState.updateDistance(
                            workSetIndex: Int32(wsIndex),
                            setIndex: Int32(setIdx),
                            value: $0
                        )
                    }
                )
            )
            .keyboardType(.decimalPad)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 6))
        default:
            TextField(
                "0",
                text: Binding(
                    get: { set.reps },
                    set: {
                        formState.updateReps(
                            workSetIndex: Int32(wsIndex),
                            setIndex: Int32(setIdx),
                            value: $0
                        )
                    }
                )
            )
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 6))
        }
    }

}
