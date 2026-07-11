import SwiftUI
import SharedUI

private let exerciseTypeOptions = ExerciseType.allCases.map(\.value)

/// Display-only capitalization for catalog values (body part, equipment,
/// type), which are stored lowercase and matched exactly by the filters.
extension String {
    var firstCapitalized: String { prefix(1).uppercased() + dropFirst() }
}

private let bodyPartOptions = [
    "back", "biceps", "calves", "chest", "core", "forearms",
    "full body", "glutes", "hamstrings", "shoulders", "thighs", "triceps",
]

private let equipmentOptions = [
    "machine", "body weight", "cable", "barbell", "smith machine",
    "dumbbell", "kettlebell", "other", "band",
]

/// Editable fields for an exercise, bound to a shared `ExerciseFormState`
/// owned by the parent `ExerciseDetailView` (so read-only display and
/// editing share one load, matching `ExerciseDetailScreen.kt`'s Compose
/// counterpart).
struct ExerciseEditForm: View {
    let uiState: ExerciseFormUiState
    let formState: ExerciseFormState
    let onDone: () -> Void

    var body: some View {
        Form {
            Section {
                TextField(L10n.name, text: Binding(
                    get: { uiState.name },
                    set: { formState.updateName(value: $0) }
                ))

                if uiState.nameError {
                    Text(L10n.nameRequired)
                        .font(.caption)
                        .foregroundStyle(.red)
                } else if uiState.duplicateNameError {
                    Text(L10n.duplicateExerciseName)
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                Picker(L10n.exerciseType, selection: Binding(
                    get: { uiState.exerciseType ?? "" },
                    set: { formState.updateExerciseType(value: $0) }
                )) {
                    Text(L10n.select).tag("")
                    ForEach(exerciseTypeOptions, id: \.self) { option in
                        Text(L10n.exerciseCatalogLabel(option)).tag(option)
                    }
                }

                Picker(L10n.bodyPart, selection: Binding(
                    get: { uiState.bodyPart ?? "" },
                    set: { formState.updateBodyPart(value: $0) }
                )) {
                    Text(L10n.select).tag("")
                    ForEach(bodyPartOptions, id: \.self) { option in
                        Text(L10n.exerciseCatalogLabel(option)).tag(option)
                    }
                }

                Picker(L10n.equipment, selection: Binding(
                    get: { uiState.equipment ?? "" },
                    set: { formState.updateEquipment(value: $0) }
                )) {
                    Text(L10n.select).tag("")
                    ForEach(equipmentOptions, id: \.self) { option in
                        Text(L10n.exerciseCatalogLabel(option)).tag(option)
                    }
                }
            }

            Section {
                Button(L10n.save) {
                    formState.save(onSaved: onDone)
                }
                .disabled(uiState.saving)
            }
        }
    }
}
