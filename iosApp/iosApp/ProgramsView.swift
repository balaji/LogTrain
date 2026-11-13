import SwiftUI
import SharedUI

/*
 * English display text for the sample programs' language-neutral keys; the
 * shared models carry only ids. Mirrors sharedUI's sampleProgramName /
 * sampleProgramSummary / sampleRoutineName. Unknown ids fall back to the raw
 * id so a sample added to sharedLogic without a mapping is visibly
 * unlocalized, not silently mislabeled.
 */

private func sampleName(_ sample: SampleProgram) -> String {
    switch sample.id {
    case "full-body-3day": return L10n.sampleFullBody3DayName
    case "upper-lower-4day": return L10n.sampleUpperLower4DayName
    case "push-pull-legs-5day": return L10n.samplePushPullLegs5DayName
    default: return sample.id
    }
}

private func sampleSummary(_ sample: SampleProgram) -> String {
    switch sample.id {
    case "full-body-3day": return L10n.sampleFullBody3DaySummary
    case "upper-lower-4day": return L10n.sampleUpperLower4DaySummary
    case "push-pull-legs-5day": return L10n.samplePushPullLegs5DaySummary
    default: return sample.id
    }
}

private func sampleRoutineName(_ routine: SampleRoutine) -> String {
    switch routine.id {
    case "full-body-a": return L10n.sampleRoutineFullBodyA
    case "full-body-b": return L10n.sampleRoutineFullBodyB
    case "full-body-c": return L10n.sampleRoutineFullBodyC
    case "upper-a": return L10n.sampleRoutineUpperA
    case "lower-a": return L10n.sampleRoutineLowerA
    case "upper-b": return L10n.sampleRoutineUpperB
    case "lower-b": return L10n.sampleRoutineLowerB
    case "push": return L10n.sampleRoutinePush
    case "pull": return L10n.sampleRoutinePull
    case "legs": return L10n.sampleRoutineLegs
    case "upper": return L10n.sampleRoutineUpper
    case "lower": return L10n.sampleRoutineLower
    default: return routine.id
    }
}

private struct RoutineTarget: Hashable {
    let routineId: Int64
    let title: String
    var isNew: Bool = false
}

private enum TextPrompt: Identifiable {
    case newProgram
    case renameProgram(Program)
    case newRoutine(Program)
    case renameRoutine(Routine)

    var id: String {
        switch self {
        case .newProgram: return "newProgram"
        case .renameProgram(let program): return "renameProgram-\(program.id)"
        case .newRoutine(let program): return "newRoutine-\(program.id)"
        case .renameRoutine(let routine): return "renameRoutine-\(routine.id)"
        }
    }

    var title: String {
        switch self {
        case .newProgram: return L10n.newProgram
        case .renameProgram: return L10n.renameProgram
        case .newRoutine: return L10n.newRoutine
        case .renameRoutine: return L10n.renameRoutine
        }
    }

    var confirmLabel: String {
        switch self {
        case .newProgram, .newRoutine: return L10n.create
        case .renameProgram, .renameRoutine: return L10n.save
        }
    }
}

private enum DeleteTarget: Identifiable {
    case program(Program)
    case routine(Routine)

    var id: String {
        switch self {
        case .program(let program): return "program-\(program.id)"
        case .routine(let routine): return "routine-\(routine.id)"
        }
    }

    var title: String {
        switch self {
        case .program: return L10n.deleteProgramTitle
        case .routine: return L10n.deleteRoutineTitle
        }
    }

    var message: String {
        switch self {
        case .program(let program): return L10n.deleteProgramMessage(program.name)
        case .routine(let routine): return L10n.deleteRoutineMessage(routine.name)
        }
    }
}

struct ProgramsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var path = NavigationPath()
    @State private var programs: [Program]?
    @State private var routines: [Routine]?
    @State private var expandedProgramId: Int64?
    @State private var expandedSampleId: String?
    @State private var myProgramsExpanded = true
    @State private var sampleProgramsExpanded = true
    @State private var prompt: TextPrompt?
    @State private var promptText = ""
    @State private var deleteTarget: DeleteTarget?
    @State private var copyTarget: SampleProgram?
    @State private var routineInfo: SampleRoutine?
    @State private var errorMessage: String?
    @State private var pendingWorkoutTarget: WorkoutSessionTarget?

    /// Static, code-defined catalog: unlike programs/routines there is nothing to observe.
    private let samplePrograms = SamplePrograms.shared.all

    private var routinesByProgram: [Int64: [Routine]] {
        Dictionary(grouping: routines ?? [], by: \.programId)
    }

    var body: some View {
        NavigationStack(path: $path) {
            // Split from the navigation chrome below at an explicit `some View`
            // boundary: as one chain, body took over 11s to type-check.
            listWithAlerts
            .navigationTitle(L10n.programsTitle)
            .navigationBarTitleDisplayMode(.inline)
            .scrollBounceBehavior(.basedOnSize)
            .navigationDestination(for: RoutineTarget.self) { target in
                RoutineDetailView(
                    routineId: target.routineId,
                    title: target.title,
                    isNew: target.isNew,
                    onStartWorkout: { path.append($0) }
                )
            }
            .navigationDestination(for: WorkoutSessionTarget.self) { target in
                WorkoutSessionView(routineId: target.routineId, title: target.title)
            }
            .onAppear {
                if let target = WorkoutSessionPresentation.shared.consumePendingOpen() {
                    path = NavigationPath([target])
                }
            }
            .onChange(of: WorkoutSessionPresentation.shared.openRequestID) {
                if let target = WorkoutSessionPresentation.shared.consumePendingOpen() {
                    path = NavigationPath([target])
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(L10n.done) { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        promptText = ""
                        prompt = .newProgram
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .task {
                for await list in ProgramRepository.shared.programs {
                    programs = list
                }
            }
            .task {
                for await list in ProgramRepository.shared.routines {
                    routines = list
                }
            }
        }
    }

    private var listWithAlerts: some View {
        Group {
            if let programs {
                List {
                    Section {
                        Button {
                            requestStartWorkout(
                                WorkoutSessionTarget(routineId: 0, title: L10n.emptyWorkoutTitle),
                                pendingTarget: $pendingWorkoutTarget
                            ) { target in
                                path.append(target)
                            }
                        } label: {
                            HStack {
                                Text(L10n.startEmptyWorkout)
                                    .foregroundStyle(.primary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.footnote.weight(.semibold))
                                    .foregroundStyle(.tertiary)
                            }
                            .contentShape(Rectangle())
                        }
                    }
                    userProgramsSection(programs)
                    sampleProgramsSection
                }
            } else {
                ProgressView()
            }
        }
        .alert(prompt?.title ?? "", isPresented: Binding(
            get: { prompt != nil },
            set: { if !$0 { prompt = nil } }
        ), presenting: prompt) { current in
            TextField(L10n.name, text: Binding(
                get: { promptText },
                set: { promptText = FormValidationKt.filterProgramName(value: $0) }
            ))
            Button(current.confirmLabel) { submit(current) }
                .disabled(promptText.trimmingCharacters(in: .whitespaces).isEmpty)
            Button(L10n.cancel, role: .cancel) {}
        }
        .alert(deleteTarget?.title ?? "", isPresented: Binding(
            get: { deleteTarget != nil },
            set: { if !$0 { deleteTarget = nil } }
        ), presenting: deleteTarget) { target in
            Button(L10n.delete, role: .destructive) { delete(target) }
            Button(L10n.cancel, role: .cancel) {}
        } message: { target in
            Text(target.message)
        }
        .alert(L10n.addSampleTitle, isPresented: Binding(
            get: { copyTarget != nil },
            set: { if !$0 { copyTarget = nil } }
        ), presenting: copyTarget) { sample in
            Button(L10n.add) { copy(sample) }
            Button(L10n.cancel, role: .cancel) {}
        } message: { sample in
            Text(L10n.addSampleMessage(sampleName(sample)))
        }
        .alert(routineInfo.map(sampleRoutineName) ?? "", isPresented: Binding(
            get: { routineInfo != nil },
            set: { if !$0 { routineInfo = nil } }
        ), presenting: routineInfo) { _ in
            Button(L10n.ok) { routineInfo = nil }
        } message: { routine in
            Text(routine.workSets.map { "\($0.exerciseName) — \($0.repsSummary)" }.joined(separator: "\n"))
        }
        .alert(L10n.somethingWentWrong, isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button(L10n.ok) { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
        .workoutInProgressAlert(pendingTarget: $pendingWorkoutTarget) { target in
            path.append(target)
        }
    }

    @ViewBuilder
    private func userProgramsSection(_ programs: [Program]) -> some View {
        Section {
            if myProgramsExpanded && programs.isEmpty {
                Text(L10n.noProgramsYet)
                    .foregroundStyle(.secondary)
            } else if myProgramsExpanded {
                ForEach(programs, id: \.id) { program in
                    programRows(program)
                }
            }
        } header: {
            collapsibleHeader(L10n.myPrograms, isExpanded: $myProgramsExpanded)
        }
    }

    @ViewBuilder
    private var sampleProgramsSection: some View {
        Section {
            if sampleProgramsExpanded {
                ForEach(samplePrograms, id: \.id) { sample in
                    sampleRows(sample)
                }
            }
        } header: {
            collapsibleHeader(L10n.samplePrograms, isExpanded: $sampleProgramsExpanded)
        }
    }

    /// Section header that folds/unfolds its section when tapped.
    private func collapsibleHeader(_ title: String, isExpanded: Binding<Bool>) -> some View {
        Button {
            withAnimation { isExpanded.wrappedValue.toggle() }
        } label: {
            HStack {
                Text(title)
                Spacer()
                Image(systemName: "chevron.right")
                    .rotationEffect(.degrees(isExpanded.wrappedValue ? 90 : 0))
            }
            .foregroundStyle(.secondary)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint(isExpanded.wrappedValue ? L10n.collapse : L10n.expand)
    }

    @ViewBuilder
    private func programRows(_ program: Program) -> some View {
        Button {
            toggle(program)
        } label: {
            HStack {
                Text(program.name)
                    .foregroundStyle(Color.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
                    .rotationEffect(.degrees(expandedProgramId == program.id ? 90 : 0))
            }
            .contentShape(Rectangle())
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) { deleteTarget = .program(program) } label: {
                Label(L10n.delete, systemImage: "trash")
            }
            Button {
                promptText = program.name
                prompt = .renameProgram(program)
            } label: {
                Label(L10n.rename, systemImage: "pencil")
            }.tint(.blue)
        }

        if expandedProgramId == program.id {
            let programRoutines = routinesByProgram[program.id] ?? []
            if programRoutines.isEmpty {
                Text(L10n.noRoutinesYet)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(programRoutines, id: \.id) { routine in
                    NavigationLink(value: RoutineTarget(routineId: routine.id, title: routine.name)) {
                        Text(routine.name)
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) { deleteTarget = .routine(routine) } label: {
                            Label(L10n.delete, systemImage: "trash")
                        }
                        Button {
                            promptText = routine.name
                            prompt = .renameRoutine(routine)
                        } label: {
                            Label(L10n.rename, systemImage: "pencil")
                        }.tint(.blue)
                    }
                }
            }
            Button {
                promptText = ""
                prompt = .newRoutine(program)
            } label: {
                Label(L10n.addRoutine, systemImage: "plus")
            }
        }
    }

    /// Read-only counterpart to `programRows`: no swipe actions, so samples cannot be
    /// renamed or deleted. The user copies one to get an editable program.
    @ViewBuilder
    private func sampleRows(_ sample: SampleProgram) -> some View {
        Button {
            toggle(sample)
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(sampleName(sample))
                        .foregroundStyle(Color.primary)
                    Text(L10n.sampleDaysSummary(Int(sample.daysPerWeek), sampleSummary(sample)))
                        .font(.caption)
                        .foregroundStyle(Color.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(Color.secondary)
                    .rotationEffect(.degrees(expandedSampleId == sample.id ? 90 : 0))
            }
            .contentShape(Rectangle())
        }

        if expandedSampleId == sample.id {
            ForEach(sample.routines, id: \.id) { routine in
                Button {
                    routineInfo = routine
                } label: {
                    HStack {
                        Text(sampleRoutineName(routine))
                            .foregroundStyle(Color.primary)
                        Spacer()
                        Text(L10n.exerciseCount(routine.workSets.count))
                            .font(.caption)
                            .foregroundStyle(Color.secondary)
                    }
                    .contentShape(Rectangle())
                }
            }
            Button {
                copyTarget = sample
            } label: {
                Label(L10n.copyToMyPrograms, systemImage: "plus")
            }
        }
    }

    private func toggle(_ program: Program) {
        withAnimation {
            expandedProgramId = expandedProgramId == program.id ? nil : program.id
        }
    }

    private func toggle(_ sample: SampleProgram) {
        withAnimation {
            expandedSampleId = expandedSampleId == sample.id ? nil : sample.id
        }
    }

    private func copy(_ sample: SampleProgram) {
        Task {
            do {
                // The English names shown for the sample's keys are what get persisted.
                _ = try await ProgramRepository.shared.addSampleProgram(
                    sample: sample,
                    programName: sampleName(sample),
                    routineNames: sample.routines.map(sampleRoutineName)
                )
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func submit(_ prompt: TextPrompt) {
        let name = promptText.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        Task {
            do {
                switch prompt {
                case .newProgram:
                    try await ProgramRepository.shared.createProgram(name: name)
                case .renameProgram(let program):
                    try await ProgramRepository.shared.renameProgram(id: program.id, name: name)
                case .newRoutine(let program):
                    let routineId = try await ProgramRepository.shared.createRoutine(programId: program.id, name: name)
                    path.append(RoutineTarget(routineId: routineId.int64Value, title: name, isNew: true))
                case .renameRoutine(let routine):
                    try await ProgramRepository.shared.renameRoutine(id: routine.id, name: name)
                }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    private func delete(_ target: DeleteTarget) {
        Task {
            do {
                switch target {
                case .program(let program):
                    try await ProgramRepository.shared.deleteProgram(id: program.id)
                case .routine(let routine):
                    try await ProgramRepository.shared.deleteRoutine(id: routine.id)
                }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}
