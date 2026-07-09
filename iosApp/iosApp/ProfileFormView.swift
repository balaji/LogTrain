import SwiftUI
import SharedUI

private let genderOptions = ["Male", "Female", "Other", "Prefer not to say"]

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
                    Text(nameError)
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                TextField("Age", text: Binding(
                    get: { uiState.age },
                    set: { formState.updateAge(value: $0) }
                ))
                .keyboardType(.numberPad)
                .focused($focusedField, equals: .age)

                if let ageError = uiState.ageError {
                    Text(ageError)
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                Picker("Gender", selection: Binding(
                    get: { uiState.gender ?? "" },
                    set: { formState.updateGender(value: $0) }
                )) {
                    Text("Select").tag("")
                    ForEach(genderOptions, id: \.self) { option in
                        Text(option).tag(option)
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

                Toggle("Week starts on Monday", isOn: Binding(
                    get: { uiState.weekStartsMonday },
                    set: { formState.updateWeekStartsMonday(value: $0) }
                ))
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
