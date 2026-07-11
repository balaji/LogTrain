import SwiftUI
import SharedUI
import UserNotifications

/// Native SwiftUI replacement for the Compose `ProfileScreen`: shows the
/// saved profile with an Edit link that swaps in `ProfileFormView`.
struct ProfileView: View {
    @State private var profile: Profile?
    @State private var settings: UserSettings?
    @State private var editing = false
    @State private var testSessionCount = 0
    @State private var showGenerateAlert = false
    @State private var showDeleteAlert = false
    @State private var generateDaysText = "90"
    @State private var working = false
    @State private var showOneRmCalculator = false

    var body: some View {
        Group {
            if let profile {
                if editing {
                    ProfileFormView(initial: profile, onSaved: { editing = false })
                } else {
                    List {
                        Section {
                            LabeledContent(L10n.name, value: profile.name)
                            LabeledContent(L10n.age, value: profile.age.map { "\($0)" } ?? "—")
                            LabeledContent(L10n.gender, value: profile.gender ?? "—")
                            LabeledContent(L10n.weight, value: profile.weight.map { L10n.weightKgValue("\($0)") } ?? "—")
                            LabeledContent(L10n.height, value: profile.height.map { L10n.heightCmValue("\($0)") } ?? "—")
                        }
                        // Always-editable app settings backed by user_settings;
                        // each change saves immediately, no Edit mode. Display
                        // labels live only here — the DB stores language-neutral
                        // keys, so they can be regionalized later.
                        if let settings {
                            Section(L10n.settings) {
                                Picker(L10n.weekStartsOn, selection: settingBinding(settings.weekStart, save: {
                                    try await UserSettingsRepository.shared.setWeekStart(weekStart: $0)
                                })) {
                                    Text(L10n.weekdaySunday).tag(WeekStart.sunday)
                                    Text(L10n.weekdayMonday).tag(WeekStart.monday)
                                }
                                Picker(L10n.appearance, selection: settingBinding(settings.appearance, save: {
                                    try await UserSettingsRepository.shared.setAppearance(appearance: $0)
                                })) {
                                    Text(L10n.appearanceLight).tag(Appearance.light)
                                    Text(L10n.appearanceDark).tag(Appearance.dark)
                                    Text(L10n.appearanceSystem).tag(Appearance.system)
                                }
                                Picker(L10n.weightUnits, selection: settingBinding(settings.weightUnit, save: {
                                    try await UserSettingsRepository.shared.setWeightUnit(weightUnit: $0)
                                })) {
                                    Text(L10n.unitsMetric).tag(WeightUnit.metric)
                                    Text(L10n.unitsImperial).tag(WeightUnit.imperial)
                                }
                                Button {
                                    showOneRmCalculator = true
                                } label: {
                                    LabeledContent(L10n.oneRmCalculator, value: formulaName(settings.oneRmFormula))
                                }
                                .tint(.primary)
                                // Opting in also asks the OS for notification
                                // permission; the stored flag is the user's
                                // intent, the grant lives with the system.
                                Toggle(L10n.sendNotifications, isOn: settingBinding(settings.notificationsEnabled, save: { enabled in
                                    if enabled {
                                        _ = try? await UNUserNotificationCenter.current()
                                            .requestAuthorization(options: [.alert, .badge, .sound])
                                    }
                                    try await UserSettingsRepository.shared.setNotificationsEnabled(enabled: enabled)
                                }))
                            }
                        }
                        // Test-data tooling for development. Kept in release
                        // builds for now; generated rows carry the test_data
                        // flag so Delete only removes what Generate created.
                        Section(L10n.developer) {
                            Button(L10n.generateTestDataButton) { showGenerateAlert = true }
                                .disabled(working)
                            Button(L10n.deleteTestDataTitle, role: .destructive) { showDeleteAlert = true }
                                .disabled(working || testSessionCount == 0)
                        }
                    }
                    .alert(L10n.generateTestDataTitle, isPresented: $showGenerateAlert) {
                        TextField(L10n.daysOfHistory, text: $generateDaysText)
                            .keyboardType(.numberPad)
                        Button(L10n.generate) {
                            guard let days = Int32(generateDaysText), days > 0 else { return }
                            working = true
                            Task {
                                defer { working = false }
                                try? await TestDataRepository.shared.generate(days: days)
                            }
                        }
                        Button(L10n.cancel, role: .cancel) {}
                    } message: {
                        Text(L10n.generateTestDataMessage)
                    }
                    .alert(L10n.deleteTestDataTitle, isPresented: $showDeleteAlert) {
                        Button(L10n.delete, role: .destructive) {
                            working = true
                            Task {
                                defer { working = false }
                                try? await TestDataRepository.shared.deleteAll()
                            }
                        }
                        Button(L10n.cancel, role: .cancel) {}
                    } message: {
                        Text(L10n.deleteTestDataMessage(testSessionCount))
                    }
                    .sheet(isPresented: $showOneRmCalculator) {
                        if let settings {
                            NavigationStack {
                                OneRmCalculatorView(settings: settings)
                                    .navigationTitle(L10n.oneRmCalculator)
                                    .navigationBarTitleDisplayMode(.inline)
                            }
                        }
                    }
                    .toolbar {
                        ToolbarItem(placement: .primaryAction) {
                            Button(L10n.edit) { editing = true }
                        }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .task {
            for await value in ProfileRepository.shared.profile {
                profile = value
            }
        }
        .task {
            for await value in UserSettingsRepository.shared.settings {
                settings = value
            }
        }
        .task {
            for await value in TestDataRepository.shared.testSessionCount {
                testSessionCount = value.intValue
            }
        }
    }

    /// Binding whose setter persists the new value; the repository flow echoes
    /// the change back into `settings`, which refreshes the getter.
    private func settingBinding<T>(_ current: T, save: @escaping (T) async throws -> Void) -> Binding<T> {
        Binding(
            get: { current },
            set: { newValue in Task { try? await save(newValue) } }
        )
    }
}
