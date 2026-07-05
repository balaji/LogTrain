import SwiftUI
import SharedUI

private struct RoutineTarget: Hashable {
    let routineId: Int64
    let title: String
}

private enum TextPrompt: Identifiable {
    case newPlan
    case renamePlan(Plan)
    case newRoutine(Plan)
    case renameRoutine(Routine)

    var id: String {
        switch self {
        case .newPlan: return "newPlan"
        case .renamePlan(let plan): return "renamePlan-\(plan.id)"
        case .newRoutine(let plan): return "newRoutine-\(plan.id)"
        case .renameRoutine(let routine): return "renameRoutine-\(routine.id)"
        }
    }

    var title: String {
        switch self {
        case .newPlan: return "New plan"
        case .renamePlan: return "Rename plan"
        case .newRoutine: return "New routine"
        case .renameRoutine: return "Rename routine"
        }
    }

    var confirmLabel: String {
        switch self {
        case .newPlan, .newRoutine: return "Create"
        case .renamePlan, .renameRoutine: return "Save"
        }
    }
}

private enum DeleteTarget: Identifiable {
    case plan(Plan)
    case routine(Routine)

    var id: String {
        switch self {
        case .plan(let plan): return "plan-\(plan.id)"
        case .routine(let routine): return "routine-\(routine.id)"
        }
    }

    var title: String {
        switch self {
        case .plan: return "Delete plan?"
        case .routine: return "Delete routine?"
        }
    }

    var message: String {
        switch self {
        case .plan(let plan): return "\"\(plan.name)\" and all of its routines will be deleted."
        case .routine(let routine): return "\"\(routine.name)\" will be deleted."
        }
    }
}

/// Native SwiftUI replacement for the Compose `PlansScreen`, bound to
/// `PlanRepository`'s shared `Flow`s via SKIE.
struct PlansView: View {
    @State private var plans: [Plan]?
    @State private var routines: [Routine]?
    @State private var expandedPlanIds: Set<Int64> = []
    @State private var prompt: TextPrompt?
    @State private var promptText = ""
    @State private var deleteTarget: DeleteTarget?
    @State private var errorMessage: String?

    private var routinesByPlan: [Int64: [Routine]] {
        Dictionary(grouping: routines ?? [], by: \.planId)
    }

    var body: some View {
        Group {
            if let plans {
                if plans.isEmpty {
                    ContentUnavailableView("No plans yet. Tap + to create one.", systemImage: "list.clipboard")
                } else {
                    List {
                        ForEach(plans, id: \.id) { plan in
                            planRows(plan)
                        }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .navigationDestination(for: RoutineTarget.self) { target in
            RoutineDetailView(routineId: target.routineId, title: target.title)
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    promptText = ""
                    prompt = .newPlan
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
            for await list in PlanRepository.shared.plans {
                plans = list
            }
        }
        .task {
            for await list in PlanRepository.shared.routines {
                routines = list
            }
        }
    }

    @ViewBuilder
    private func planRows(_ plan: Plan) -> some View {
        Button {
            toggle(plan)
        } label: {
            HStack {
                Text(plan.name)
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(.secondary)
                    .rotationEffect(.degrees(expandedPlanIds.contains(plan.id) ? 90 : 0))
            }
            .contentShape(Rectangle())
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) { deleteTarget = .plan(plan) } label: {
                Label("Delete", systemImage: "trash")
            }
            Button {
                promptText = plan.name
                prompt = .renamePlan(plan)
            } label: {
                Label("Rename", systemImage: "pencil")
            }.tint(.blue)
        }

        if expandedPlanIds.contains(plan.id) {
            let planRoutines = routinesByPlan[plan.id] ?? []
            if planRoutines.isEmpty {
                Text("No routines yet. Tap + to add one.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(planRoutines, id: \.id) { routine in
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
                prompt = .newRoutine(plan)
            } label: {
                Label("Add routine", systemImage: "plus")
            }
        }
    }

    private func toggle(_ plan: Plan) {
        if expandedPlanIds.contains(plan.id) {
            expandedPlanIds.remove(plan.id)
        } else {
            expandedPlanIds.insert(plan.id)
        }
    }

    private func submit(_ prompt: TextPrompt) {
        let name = promptText.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        Task {
            do {
                switch prompt {
                case .newPlan:
                    try await PlanRepository.shared.createPlan(name: name)
                case .renamePlan(let plan):
                    try await PlanRepository.shared.renamePlan(id: plan.id, name: name)
                case .newRoutine(let plan):
                    try await PlanRepository.shared.createRoutine(planId: plan.id, name: name)
                case .renameRoutine(let routine):
                    try await PlanRepository.shared.renameRoutine(id: routine.id, name: name)
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
                case .plan(let plan):
                    try await PlanRepository.shared.deletePlan(id: plan.id)
                case .routine(let routine):
                    try await PlanRepository.shared.deleteRoutine(id: routine.id)
                }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}
