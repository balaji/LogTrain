import SwiftUI
import SharedUI

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
        case .newProgram: return "New program"
        case .renameProgram: return "Rename program"
        case .newRoutine: return "New routine"
        case .renameRoutine: return "Rename routine"
        }
    }

    var confirmLabel: String {
        switch self {
        case .newProgram, .newRoutine: return "Create"
        case .renameProgram, .renameRoutine: return "Save"
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
        case .program: return "Delete program?"
        case .routine: return "Delete routine?"
        }
    }

    var message: String {
        switch self {
        case .program(let program): return "\"\(program.name)\" and all of its routines will be deleted."
        case .routine(let routine): return "\"\(routine.name)\" will be deleted."
        }
    }
}

struct ProgramsView: View {
    @State private var path = NavigationPath()
    @State private var programs: [Program]?
    @State private var routines: [Routine]?
    @State private var expandedProgramId: Int64?
    @State private var expandedSampleId: String?
    @State private var prompt: TextPrompt?
    @State private var promptText = ""
    @State private var deleteTarget: DeleteTarget?
    @State private var copyTarget: SampleProgram?
    @State private var routineInfo: SampleRoutine?
    @State private var errorMessage: String?

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
            .navigationTitle("Programs")
            .navigationBarTitleDisplayMode(.inline)
            .profileToolbar()
            .scrollBounceBehavior(.basedOnSize)
            .navigationDestination(for: RoutineTarget.self) { target in
                RoutineDetailView(routineId: target.routineId, title: target.title, isNew: target.isNew)
            }
            .navigationDestination(for: WorkoutSessionTarget.self) { target in
                WorkoutSessionView(routineId: target.routineId, title: target.title)
            }
            .onChange(of: WorkoutSessionPresentation.shared.openRequestID) {
                // Replace the whole stack rather than appending: a stale
                // session screen may still be pushed here from an earlier
                // visit, and stacking a second one on top of it is what
                // used to break the mini bar's visibility tracking.
                path = NavigationPath([WorkoutSessionPresentation.shared.pendingTarget])
            }
            .toolbar {
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
                        NavigationLink(value: WorkoutSessionTarget(routineId: 0, title: "Empty workout")) {
                            Text("Start Empty Workout")
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
            TextField("Name", text: Binding(
                get: { promptText },
                set: { promptText = FormValidationKt.filterProgramName(value: $0) }
            ))
            Button(current.confirmLabel) { submit(current) }
                .disabled(promptText.trimmingCharacters(in: .whitespaces).isEmpty)
            Button("Cancel", role: .cancel) {}
        }
        .alert(deleteTarget?.title ?? "", isPresented: Binding(
            get: { deleteTarget != nil },
            set: { if !$0 { deleteTarget = nil } }
        ), presenting: deleteTarget) { target in
            Button("Delete", role: .destructive) { delete(target) }
            Button("Cancel", role: .cancel) {}
        } message: { target in
            Text(target.message)
        }
        .alert("Add to your programs?", isPresented: Binding(
            get: { copyTarget != nil },
            set: { if !$0 { copyTarget = nil } }
        ), presenting: copyTarget) { sample in
            Button("Add") { copy(sample) }
            Button("Cancel", role: .cancel) {}
        } message: { sample in
            Text("\"\(sample.name)\" will be copied into your programs, where you can edit it.")
        }
        .alert(routineInfo?.name ?? "", isPresented: Binding(
            get: { routineInfo != nil },
            set: { if !$0 { routineInfo = nil } }
        ), presenting: routineInfo) { _ in
            Button("OK") { routineInfo = nil }
        } message: { routine in
            Text(routine.workSets.map { "\($0.exerciseName) — \($0.repsSummary)" }.joined(separator: "\n"))
        }
        .alert("Something went wrong", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("OK") { errorMessage = nil }
        } message: {
            Text(errorMessage ?? "")
        }
    }

    @ViewBuilder
    private func userProgramsSection(_ programs: [Program]) -> some View {
        Section("My Programs") {
            if programs.isEmpty {
                Text("No programs yet. Tap + to create one.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(programs, id: \.id) { program in
                    programRows(program)
                }
            }
        }
    }

    @ViewBuilder
    private var sampleProgramsSection: some View {
        Section("Sample Programs") {
            ForEach(samplePrograms, id: \.id) { sample in
                sampleRows(sample)
            }
        }
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
                Label("Delete", systemImage: "trash")
            }
            Button {
                promptText = program.name
                prompt = .renameProgram(program)
            } label: {
                Label("Rename", systemImage: "pencil")
            }.tint(.blue)
        }

        if expandedProgramId == program.id {
            let programRoutines = routinesByProgram[program.id] ?? []
            if programRoutines.isEmpty {
                Text("No routines yet. Tap + to add one.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(programRoutines, id: \.id) { routine in
                    NavigationLink(value: RoutineTarget(routineId: routine.id, title: routine.name)) {
                        Text(routine.name)
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) { deleteTarget = .routine(routine) } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        Button {
                            promptText = routine.name
                            prompt = .renameRoutine(routine)
                        } label: {
                            Label("Rename", systemImage: "pencil")
                        }.tint(.blue)
                    }
                }
            }
            Button {
                promptText = ""
                prompt = .newRoutine(program)
            } label: {
                Label("Add Routine", systemImage: "plus")
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
                    Text(sample.name)
                        .foregroundStyle(.primary)
                    Text("\(sample.daysPerWeek) days a week · \(sample.summary)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
                    .rotationEffect(.degrees(expandedSampleId == sample.id ? 90 : 0))
            }
            .contentShape(Rectangle())
        }

        if expandedSampleId == sample.id {
            ForEach(sample.routines, id: \.name) { routine in
                Button {
                    routineInfo = routine
                } label: {
                    HStack {
                        Text(routine.name)
                            .foregroundStyle(.primary)
                        Spacer()
                        Text("\(routine.workSets.count) exercises")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .contentShape(Rectangle())
                }
            }
            Button {
                copyTarget = sample
            } label: {
                Label("Copy to my programs", systemImage: "plus")
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
                _ = try await ProgramRepository.shared.addSampleProgram(sample: sample)
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
