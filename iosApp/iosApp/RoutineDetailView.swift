import SwiftUI
import SharedUI

struct RoutineDetailView: View {
    @Environment(\.dismiss) private var dismiss
    let routineId: Int64
    let isNew: Bool
    @State private var isEditing: Bool
    @State private var formState: RoutineFormState
    @State private var uiState: RoutineDetailUiState
    @State private var formVersion = 0
    @State private var nameBeforeEdit = ""
    @State private var showExercisePicker = false

    @State private var reorderActiveIndex: Int?
    @State private var fingerY: CGFloat = 0
    @State private var startFingerY: CGFloat = 0
    @State private var overlayListTop: CGFloat = 0
    @State private var overlayBottom: CGFloat = 0
    @State private var lastActiveIndex = 0

    private let reorderRowHeight: CGFloat = 56

    init(routineId: Int64, title: String, isNew: Bool = false) {
        self.routineId = routineId
        self.isNew = isNew
        let state = RoutineFormState(routineId: routineId, initialName: title)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
        _isEditing = State(initialValue: isNew)
    }

    private var anchoredListTop: CGFloat {
        let active = reorderActiveIndex ?? lastActiveIndex
        let count = uiState.workSets.count
        let maxTop = max(overlayListTop, overlayBottom - CGFloat(count) * reorderRowHeight)
        let ideal = startFingerY - (CGFloat(active) + 0.5) * reorderRowHeight
        return min(max(ideal, overlayListTop), maxTop)
    }

    private var reorderTargetIndex: Int? {
        guard reorderActiveIndex != nil, !uiState.workSets.isEmpty else { return nil }
        let slot = Int(((fingerY - anchoredListTop) / reorderRowHeight).rounded(.down))
        return min(max(slot, 0), uiState.workSets.count - 1)
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
            if isEditing && reorderActiveIndex != nil {
                reorderOverlay
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .navigationTitle(uiState.name.isEmpty ? "Routine" : uiState.name)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(isEditing && !isNew)
        .toolbar {
            if isEditing {
                if !isNew {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
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
                        Button("Save") {
                            let trimmed = uiState.name.trimmingCharacters(in: .whitespaces)
                            let savedName = trimmed.isEmpty ? "Routine" : trimmed
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
                    Button("Edit") {
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
                .navigationTitle("Add Exercise")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showExercisePicker = false }
                    }
                }
            }
        }
        .task(id: formVersion) {
            for await state in formState.uiState {
                uiState = state
            }
        }
    }

    // MARK: - Read-only view

    private var routineReadOnly: some View {
        ZStack(alignment: .bottomTrailing) {
            List {
                if uiState.workSets.isEmpty {
                    Text("No exercises yet. Tap Edit to add exercises.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(Array(uiState.workSets.enumerated()), id: \.element.id) { _, workSet in
                        Text("\(workSet.sets.count) x \(workSet.exercise.name)")
                    }
                }
            }

            if !uiState.workSets.isEmpty {
                NavigationLink {
                    WorkoutSessionView(
                        routineId: routineId,
                        title: uiState.name.isEmpty ? "Workout" : uiState.name
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
                .padding(24)
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
                    onReorderStart: { startY in
                        fingerY = startY
                        startFingerY = startY
                        lastActiveIndex = wsIndex
                        withAnimation(.easeOut(duration: 0.15)) {
                            reorderActiveIndex = wsIndex
                        }
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    },
                    onReorderChanged: { y in fingerY = y },
                    onReorderEnded: { commitReorder() }
                )
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        formState.removeExercise(workSetIndex: Int32(wsIndex))
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
            }

            Button {
                showExercisePicker = true
            } label: {
                Label("Add Exercise", systemImage: "plus")
            }
        }
        .scrollDisabled(reorderActiveIndex != nil)
    }

    private func commitReorder() {
        if let from = reorderActiveIndex, let to = reorderTargetIndex, from != to {
            formState.moveExercise(from: Int32(from), to: Int32(to))
        }
        withAnimation(.easeIn(duration: 0.2)) {
            reorderActiveIndex = nil
        }
    }

    private var reorderOverlay: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Reorder exercises")
                .font(.headline)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

            let columnShift = anchoredListTop - overlayListTop
            ZStack(alignment: .top) {
                VStack(spacing: 0) {
                    ForEach(Array(uiState.workSets.enumerated()), id: \.element.id) { index, workSet in
                        reorderRow(workSet.exercise.name)
                            .opacity(index == reorderActiveIndex ? 0 : 1)
                            .offset(y: reorderShift(for: index))
                            .animation(.default, value: reorderTargetIndex)
                    }
                }
                .offset(y: columnShift)

                if let active = reorderActiveIndex {
                    let maxY = CGFloat(uiState.workSets.count - 1) * reorderRowHeight
                    let floatY = min(max(fingerY - anchoredListTop - reorderRowHeight / 2, 0), maxY)
                    reorderRow(uiState.workSets[active].exercise.name)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .shadow(radius: 8)
                        .offset(y: floatY + columnShift)
                }
            }
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.frame(in: .global).minY
            } action: { top in
                overlayListTop = top
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
        .onGeometryChange(for: CGFloat.self) { proxy in
            proxy.frame(in: .global).maxY
        } action: { bottom in
            overlayBottom = bottom
        }
    }

    private func reorderShift(for index: Int) -> CGFloat {
        guard let active = reorderActiveIndex,
              let target = reorderTargetIndex,
              index != active else { return 0 }
        if active < index && index <= target { return -reorderRowHeight }
        if target <= index && index < active { return reorderRowHeight }
        return 0
    }

    private func reorderRow(_ name: String) -> some View {
        HStack {
            Text(name)
                .foregroundStyle(Color.accentColor)
            Spacer()
            Image(systemName: "line.3.horizontal")
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 16)
        .frame(maxWidth: .infinity)
        .frame(height: reorderRowHeight)
    }
}

// MARK: - Editable exercise card

private struct ExerciseCardView: View {
    let wsIndex: Int
    let workSet: DraftWorkSet
    let formState: RoutineFormState
    var onReorderStart: (CGFloat) -> Void
    var onReorderChanged: (CGFloat) -> Void
    var onReorderEnded: () -> Void

    @State private var headerMidY: CGFloat = 0
    @State private var reorderStarted = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(workSet.exercise.name)
                .font(.headline)
                .foregroundStyle(Color.accentColor)
                .padding(.vertical, 6)
                .onGeometryChange(for: CGFloat.self) { proxy in
                    proxy.frame(in: .global).midY
                } action: { midY in
                    headerMidY = midY
                }
                .gesture(reorderGesture)

            HStack {
                Text("SET")
                    .frame(width: 28)
                Text("KG")
                    .frame(maxWidth: .infinity)
                Text("REPS")
                    .frame(maxWidth: .infinity)
                Spacer().frame(width: 36)
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.bottom, 4)

            ForEach(Array(workSet.sets.enumerated()), id: \.offset) { setIdx, set in
                HStack(spacing: 8) {
                    Text("\(setIdx + 1)")
                        .frame(width: 28)
                        .foregroundStyle(.secondary)

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

    private var reorderGesture: some Gesture {
        LongPressGesture(minimumDuration: 0.35)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .global))
            .onChanged { value in
                switch value {
                case .second(true, let drag):
                    if !reorderStarted {
                        reorderStarted = true
                        onReorderStart(drag?.location.y ?? headerMidY)
                    }
                    if let drag {
                        onReorderChanged(drag.location.y)
                    }
                default:
                    break
                }
            }
            .onEnded { _ in
                if reorderStarted {
                    reorderStarted = false
                    onReorderEnded()
                }
            }
    }
}
