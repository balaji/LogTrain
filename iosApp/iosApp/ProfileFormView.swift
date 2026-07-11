import SwiftUI
import SharedUI

// Stored to the profile as displayed, matching the Compose form's behavior.
private let genderOptions = [
    L10n.genderMale, L10n.genderFemale, L10n.genderOther, L10n.genderPreferNotToSay,
]

/// Localized messages for the language-neutral validation errors the shared
/// form state emits; mirrors sharedUI's profileNameErrorText. The length
/// bound matches MAX_NAME_LENGTH in the shared ProfileFormState.
private func nameErrorText(_ error: ProfileNameError) -> String {
    switch error {
    case .required: return L10n.nameRequired
    case .tooLong: return L10n.profileNameTooLong(50)
    case .invalidChars: return L10n.profileNameLettersOnly
    default: return L10n.profileNameInvalid
    }
}

private enum ProfileFormField: Hashable {
    case name, age, weight, height
}

/// Native SwiftUI replacement for the Compose `ProfileForm`, bound to the
/// shared `ProfileFormState` via SKIE's Swift `StateFlow` bridge.
struct ProfileFormView: View {
    let saveLabel: String
    let onSaved: () -> Void

    @State private var formState: ProfileFormState
    @State private var uiState: ProfileFormUiState
    @FocusState private var focusedField: ProfileFormField?

    init(initial: Profile? = nil, saveLabel: String = L10n.save, onSaved: @escaping () -> Void = {}) {
        let state = ProfileFormState(initial: initial)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
        self.saveLabel = saveLabel
        self.onSaved = onSaved
    }

    var body: some View {
        Form {
            Section {
                TextField(L10n.name, text: Binding(
                    get: { uiState.name },
                    set: { formState.updateName(value: $0) }
                ))
                .focused($focusedField, equals: .name)

                if let nameError = uiState.nameError {
                    Text(nameErrorText(nameError))
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                TextField(L10n.age, text: Binding(
                    get: { uiState.age },
                    set: { formState.updateAge(value: $0) }
                ))
                .keyboardType(.numberPad)
                .focused($focusedField, equals: .age)

                if uiState.ageError {
                    // Bounds match MIN_AGE/MAX_AGE in the shared ProfileFormState.
                    Text(L10n.profileAgeRange(1, 120))
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                Picker(L10n.gender, selection: Binding(
                    get: { uiState.gender ?? "" },
                    set: { formState.updateGender(value: $0) }
                )) {
                    Text(L10n.select).tag("")
                    ForEach(genderOptions, id: \.self) { option in
                        Text(option).tag(option)
                    }
                }

                TextField(L10n.weightKgLabel, text: Binding(
                    get: { uiState.weight },
                    set: { formState.updateWeight(value: $0) }
                ))
                .keyboardType(.decimalPad)
                .focused($focusedField, equals: .weight)

                TextField(L10n.heightCmLabel, text: Binding(
                    get: { uiState.height },
                    set: { formState.updateHeight(value: $0) }
                ))
                .keyboardType(.decimalPad)
                .focused($focusedField, equals: .height)
            }

            Section {
                Button(saveLabel) {
                    formState.save(onSaved: onSaved)
                }
                .disabled(uiState.saving)
            }
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(L10n.done) {
                    focusedField = nil
                }
            }
        }
        .task {
            for await state in formState.uiState {
                uiState = state
            }
        }
    }
}
