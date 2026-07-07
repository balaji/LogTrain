import SwiftUI
import SharedUI

private struct RoutineTarget: Hashable {
    let routineId: Int64
    let title: String
    var isNew: Bool = false
}

private struct WorkoutSessionTarget: Hashable {
    let routineId: Int64
    let title: String
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
    @State private var prompt: TextPrompt?
    @State private var promptText = ""
    @State private var deleteTarget: DeleteTarget?
    @State private var errorMessage: String?

    private var routinesByProgram: [Int64: [Routine]] {
        Dictionary(grouping: routines ?? [], by: \.programId)
    }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if let programs {
                    List {
                        Section {
                            NavigationLink {
                                WorkoutSessionView(routineId: 0, title: "Empty workout")
                            } label: {
                                Text("Start Empty Workout")
                            }
                        }
                        if programs.isEmpty {
                            Section {
                                Text("No programs yet. Tap + to create one.")
                                    .foregroundStyle(.secondary)
                            }
                        } else {
                            ForEach(programs, id: \.id) { program in
                                programRows(program)
                            }
                        }
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Programs")
            .navigationBarTitleDisplayMode(.inline)
            .scrollBounceBehavior(.basedOnSize)
            .navigationDestination(for: RoutineTarget.self) { target in
                RoutineDetailView(routineId: target.routineId, title: target.title, isNew: target.isNew)
            }
            .navigationDestination(for: WorkoutSessionTarget.self) { target in
                WorkoutSessionView(routineId: target.routineId, title: target.title)
            }
            .onChange(of: WorkoutSessionPresentation.shared.openRequestID) {
                path.append(
                    WorkoutSessionTarget(
                        routineId: WorkoutSessionPresentation.shared.pendingRoutineId,
                        title: WorkoutSessionPresentation.shared.pendingTitle
                    )
                )
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
            .alert(prompt?.title ?? "", isPresented: Binding(
                get: { prompt != nil },
                set: { if !$0 { prompt = nil } }
            ), presenting: prompt) { current in
                TextField("Name", text: $promptText)
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
            .alert("Something went wrong", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK") { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
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

    @ViewBuilder
    private func programRows(_ program: Program) -> some View {
        Button {
            toggle(program)
        } label: {
            HStack {
                Text(program.name)
                    .foregroundStyle(.primary)
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
                Label("Add routine", systemImage: "plus")
            }
        }
    }

    private func toggle(_ program: Program) {
        withAnimation {
            expandedProgramId = expandedProgramId == program.id ? nil : program.id
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
