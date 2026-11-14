import SwiftUI
import SharedUI

// Language-neutral keys persisted in profile.gender; matches GENDER_KEYS in
// the shared ProfileFormState. L10n.genderLabel maps them for display.
private let genderOptions = ["male", "female", "other", "prefer_not_to_say"]

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
/// shared `ProfileFormState` via SKIE's Swift `StateFlow` bridge. Weight and
/// height can be switched between metric (kg/cm) and imperial (lb + ft/in);
/// the shared form state converts back to canonical kg/cm and persists the
/// unit preference to settings on save.
struct ProfileFormView: View {
    let saveLabel: String
    let onSaved: () -> Void

    @State private var formState: ProfileFormState
    @State private var uiState: ProfileFormUiState
    @State private var heightWheelsExpanded = false
    @FocusState private var focusedField: ProfileFormField?

    init(
        initial: Profile? = nil,
        unit: WeightUnit = .metric,
        saveLabel: String = L10n.save,
        onSaved: @escaping () -> Void = {}
    ) {
        let state = ProfileFormState(initial: initial, unit: unit)
        _formState = State(initialValue: state)
        _uiState = State(initialValue: state.uiState.value)
        self.saveLabel = saveLabel
        self.onSaved = onSaved
    }

    var body: some View {
        Form {
            Section {
                LabeledContent(L10n.name) {
                    TextField("", text: Binding(
                        get: { uiState.name },
                        set: { formState.updateName(value: $0) }
                    ))
                    .multilineTextAlignment(.trailing)
                    .focused($focusedField, equals: .name)
                }

                if let nameError = uiState.nameError {
                    Text(nameErrorText(nameError))
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                LabeledContent(L10n.age) {
                    TextField("", text: Binding(
                        get: { uiState.age },
                        set: { formState.updateAge(value: $0) }
                    ))
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .focused($focusedField, equals: .age)
                }

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
                        Text(L10n.genderLabel(option)).tag(option)
                    }
                }
            }

            Section {
                Picker(L10n.weightUnits, selection: Binding(
                    get: { uiState.unit },
                    set: { newUnit in
                        if newUnit == .imperial && focusedField == .height {
                            focusedField = nil
                        }
                        if newUnit == .metric {
                            withAnimation { heightWheelsExpanded = false }
                        }
                        formState.updateUnit(unit: newUnit)
                    }
                )) {
                    Text(L10n.unitsMetric).tag(WeightUnit.metric)
                    Text(L10n.unitsImperial).tag(WeightUnit.imperial)
                }
                .pickerStyle(.segmented)

                LabeledContent("\(L10n.weight) (\(uiState.unit == .imperial ? L10n.unitLb : L10n.unitKg))") {
                    TextField("", text: Binding(
                        get: { uiState.weight },
                        set: { formState.updateWeight(value: $0) }
                    ))
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .focused($focusedField, equals: .weight)
                }

                if uiState.unit == .imperial {
                    FeetInchesField(
                        label: L10n.height,
                        feet: Binding(
                            get: { Int(uiState.heightFeet) },
                            set: { formState.updateHeightFeetInches(feet: Int32($0), inches: uiState.heightInches) }
                        ),
                        inches: Binding(
                            get: { Int(uiState.heightInches) },
                            set: { formState.updateHeightFeetInches(feet: uiState.heightFeet, inches: Int32($0)) }
                        ),
                        expanded: $heightWheelsExpanded
                    )
                } else {
                    LabeledContent("\(L10n.height) (\(L10n.unitCm))") {
                        TextField("", text: Binding(
                            get: { uiState.height },
                            set: { formState.updateHeight(value: $0) }
                        ))
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                        .focused($focusedField, equals: .height)
                    }
                }
            }

            Section {
                Button(saveLabel) {
                    formState.save(onSaved: onSaved)
                }
                .disabled(uiState.saving)
            }
        }
        .floatingWorkoutMenuClearance()
        // Typing in any text field puts the height wheels away.
        .onChange(of: focusedField) { _, newValue in
            if newValue != nil {
                withAnimation { heightWheelsExpanded = false }
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
