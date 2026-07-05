import SwiftUI
import SharedUI

struct RoutineDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var formState: RoutineFormState
    @State private var uiState: RoutineDetailUiState
    @State private var showExercisePicker = false

    // Continuous long-press reorder: the overlay is a ZStack layer (not a
    // sheet) so the gesture that started on the exercise name keeps streaming
    // drag events into it without the finger lifting.
    @State private var reorderActiveIndex: Int?
    @State private var fingerY: CGFloat = 0
    @State private var startFingerY: CGFloat = 0
    @State private var overlayListTop: CGFloat = 0
    @State private var overlayBottom: CGFloat = 0
    // Keeps the anchor stable while the overlay fades out after
    // reorderActiveIndex is cleared.
    @State private var lastActiveIndex = 0

    private let reorderRowHeight: CGFloat = 56

    init(routineId: Int64, title: String) {
        let state = RoutineFormState(routineId: routineId, initialName: title)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
    }

    /// Top of the compact list, positioned so the pressed exercise's slot sits
    /// directly under the finger at activation, clamped so the whole list stays
    /// between the overlay title and the bottom of the screen.
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
            Group {
                if uiState.loading {
                    ProgressView()
                } else {
                    routineForm
                }
            }
            if reorderActiveIndex != nil {
                reorderOverlay
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .navigationTitle(uiState.name.isEmpty ? "Routine" : uiState.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if uiState.saving {
                    ProgressView()
                } else {
                    Button("Save") {
                        formState.save { dismiss() }
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
        .task {
            for await state in formState.uiState {
                uiState = state
            }
        }
    }

    private var routineForm: some View {
        List {
            // Editable routine name
            TextField(
                "Routine name",
                text: Binding(
                    get: { uiState.name },
                    set: { formState.updateName(value: $0) }
                )
            )
            .font(.title2.bold())

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

                // Floating copy of the dragged row, riding under the finger but
                // clamped to the list bounds so the rows always read as one group.
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
            // Exercise name: long-press and keep holding to reorder
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

            // Column headers
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

            // Set rows
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

            // Add set
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
                        // The drag has no location until the finger moves, so
                        // start from the header's own global position.
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
