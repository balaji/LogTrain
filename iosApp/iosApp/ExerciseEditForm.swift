import SwiftUI
import SharedUI

private let exerciseTypeOptions = ["strength", "cardio"]

private let bodyPartOptions = [
    "back", "biceps", "calves", "chest", "core", "forearms",
    "full body", "glutes", "hamstrings", "shoulders", "thighs", "triceps",
]

private let equipmentOptions = [
    "machine", "body weight", "cable", "barbell", "smith machine",
    "dumbbell", "kettlebell", "other", "band",
]

private enum ExerciseFormField: Hashable {
    case description
}

/// Editable fields for an exercise, bound to a shared `ExerciseFormState`
/// owned by the parent `ExerciseDetailView` (so read-only display and
/// editing share one load, matching `ExerciseDetailScreen.kt`'s Compose
/// counterpart).
struct ExerciseEditForm: View {
    let uiState: ExerciseFormUiState
    let formState: ExerciseFormState
    let onDone: () -> Void
    @FocusState private var focusedField: ExerciseFormField?

    var body: some View {
        Form {
            Section {
                TextField("Name", text: Binding(
                    get: { uiState.name },
                    set: { formState.updateName(value: $0) }
                ))

                if uiState.nameError {
                    Text("Name is required")
                        .font(.caption)
                        .foregroundStyle(.red)
                } else if uiState.duplicateNameError {
                    Text("An exercise with this name already exists")
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                TextField("Description", text: Binding(
                    get: { uiState.descriptionText },
                    set: { formState.updateDescription(value: $0) }
                ), axis: .vertical)
                .lineLimit(3...8)
                .focused($focusedField, equals: .description)

                Picker("Type", selection: Binding(
                    get: { uiState.exerciseType ?? "" },
                    set: { formState.updateExerciseType(value: $0) }
                )) {
                    Text("Select").tag("")
                    ForEach(exerciseTypeOptions, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }

                Picker("Body part", selection: Binding(
                    get: { uiState.bodyPart ?? "" },
                    set: { formState.updateBodyPart(value: $0) }
                )) {
                    Text("Select").tag("")
                    ForEach(bodyPartOptions, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }

                Picker("Equipment", selection: Binding(
                    get: { uiState.equipment ?? "" },
                    set: { formState.updateEquipment(value: $0) }
                )) {
                    Text("Select").tag("")
                    ForEach(equipmentOptions, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }
            }

            Section {
                Button("Save") {
                    formState.save(onSaved: onDone)
                }
                .disabled(uiState.saving)
            }
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") { focusedField = nil }
            }
        }
    }
}
