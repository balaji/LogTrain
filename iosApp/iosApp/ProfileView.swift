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
                            LabeledContent("Name", value: profile.name)
                            LabeledContent("Age", value: profile.age.map { "\($0)" } ?? "—")
                            LabeledContent("Gender", value: profile.gender.map(genderLabel) ?? "—")
                            LabeledContent("Weight", value: profile.weight.map { "\($0) kg" } ?? "—")
                            LabeledContent("Height", value: profile.height.map { "\($0) cm" } ?? "—")
                        }
                        // Always-editable app settings backed by user_settings;
                        // each change saves immediately, no Edit mode. Display
                        // labels live only here — the DB stores language-neutral
                        // keys, so they can be regionalized later.
                        if let settings {
                            Section("Settings") {
                                Picker("Week starts on", selection: settingBinding(settings.weekStart, save: {
                                    try await UserSettingsRepository.shared.setWeekStart(weekStart: $0)
                                })) {
                                    Text("Sunday").tag(WeekStart.sunday)
                                    Text("Monday").tag(WeekStart.monday)
                                }
                                Picker("Appearance", selection: settingBinding(settings.appearance, save: {
                                    try await UserSettingsRepository.shared.setAppearance(appearance: $0)
                                })) {
                                    Text("Light").tag(Appearance.light)
                                    Text("Dark").tag(Appearance.dark)
                                    Text("System").tag(Appearance.system)
                                }
                                Picker("Weight units", selection: settingBinding(settings.weightUnit, save: {
                                    try await UserSettingsRepository.shared.setWeightUnit(weightUnit: $0)
                                })) {
                                    Text("Metric").tag(WeightUnit.metric)
                                    Text("Imperial").tag(WeightUnit.imperial)
                                }
                                Button {
                                    showOneRmCalculator = true
                                } label: {
                                    LabeledContent("1 Rep Max Calculator", value: formulaName(settings.oneRmFormula))
                                }
                                .tint(.primary)
                                // Opting in also asks the OS for notification
                                // permission; the stored flag is the user's
                                // intent, the grant lives with the system.
                                Toggle("Send notifications", isOn: settingBinding(settings.notificationsEnabled, save: { enabled in
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
                        Section("Developer") {
                            Button("Generate test data…") { showGenerateAlert = true }
                                .disabled(working)
                            Button("Delete test data", role: .destructive) { showDeleteAlert = true }
                                .disabled(working || testSessionCount == 0)
                        }
                    }
                    .alert("Generate test data", isPresented: $showGenerateAlert) {
                        TextField("Days of history", text: $generateDaysText)
                            .keyboardType(.numberPad)
                        Button("Generate") {
                            guard let days = Int32(generateDaysText), days > 0 else { return }
                            working = true
                            Task {
                                defer { working = false }
                                try? await TestDataRepository.shared.generate(days: days)
                            }
                        }
                        Button("Cancel", role: .cancel) {}
                    } message: {
                        Text("Fills the calendar with roughly three workouts a week and regular weigh-ins, ending today.")
                    }
                    .alert("Delete test data", isPresented: $showDeleteAlert) {
                        Button("Delete", role: .destructive) {
                            working = true
                            Task {
                                defer { working = false }
                                try? await TestDataRepository.shared.deleteAll()
                            }
                        }
                        Button("Cancel", role: .cancel) {}
                    } message: {
                        Text("Removes all \(testSessionCount) generated workouts and their weigh-ins. Your own logged data is untouched.")
                    }
                    .sheet(isPresented: $showOneRmCalculator) {
                        if let settings {
                            NavigationStack {
                                OneRmCalculatorView(settings: settings)
                                    .navigationTitle("1 Rep Max Calculator")
                                    .navigationBarTitleDisplayMode(.inline)
                            }
                        }
                    }
                    .toolbar {
                        ToolbarItem(placement: .primaryAction) {
                            Button("Edit") { editing = true }
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
