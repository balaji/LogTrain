import SwiftUI
import SharedUI

// Language-neutral keys persisted in profile.gender; matches GENDER_KEYS in
// the shared ProfileFormState.
private let genderOptions = ["male", "female", "other", "prefer_not_to_say"]

/// English labels for the language-neutral gender keys the DB stores; mirrors
/// sharedUI's genderLabel. Unknown values are shown verbatim rather than hidden.
func genderLabel(_ key: String) -> String {
    switch key {
    case "male": return "Male"
    case "female": return "Female"
    case "other": return "Other"
    case "prefer_not_to_say": return "Prefer not to say"
    default: return key
    }
}

/// English messages for the language-neutral validation errors the shared
/// form state emits; mirrors sharedUI's profileNameErrorText. The length
/// bound matches MAX_NAME_LENGTH in the shared ProfileFormState.
private func nameErrorText(_ error: ProfileNameError) -> String {
    switch error {
    case .required: return "Name is required"
    case .tooLong: return "Name must be at most 50 characters"
    case .invalidChars: return "Name must contain only letters"
    default: return "Invalid name"
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

    init(initial: Profile? = nil, saveLabel: String = "Save", onSaved: @escaping () -> Void = {}) {
        let state = ProfileFormState(initial: initial)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
        self.saveLabel = saveLabel
        self.onSaved = onSaved
    }

    var body: some View {
        Form {
            Section {
                TextField("Name", text: Binding(
                    get: { uiState.name },
                    set: { formState.updateName(value: $0) }
                ))
                .focused($focusedField, equals: .name)

                if let nameError = uiState.nameError {
                    Text(nameErrorText(nameError))
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                TextField("Age", text: Binding(
                    get: { uiState.age },
                    set: { formState.updateAge(value: $0) }
                ))
                .keyboardType(.numberPad)
                .focused($focusedField, equals: .age)

                if uiState.ageError {
                    // Bounds match MIN_AGE/MAX_AGE in the shared ProfileFormState.
                    Text("Age must be between 1 and 120")
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                Picker("Gender", selection: Binding(
                    get: { uiState.gender ?? "" },
                    set: { formState.updateGender(value: $0) }
                )) {
                    Text("Select").tag("")
                    ForEach(genderOptions, id: \.self) { option in
                        Text(genderLabel(option)).tag(option)
                    }
                }

                TextField("Weight (kg)", text: Binding(
                    get: { uiState.weight },
                    set: { formState.updateWeight(value: $0) }
                ))
                .keyboardType(.decimalPad)
                .focused($focusedField, equals: .weight)

                TextField("Height (cm)", text: Binding(
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
                Button("Done") {
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
