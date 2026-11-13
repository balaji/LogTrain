import SwiftUI
import SharedUI
import UserNotifications

/// Native SwiftUI replacement for the Compose `ProfileScreen`: shows the
/// saved profile with an Edit link that swaps in `ProfileFormView`. Owns the
/// Edit / Back nav-bar buttons for switching between view and edit mode.
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
    @State private var showBmiCalculator = false
    @State private var showNotificationsDeniedAlert = false

    var body: some View {
        Group {
            if let profile {
                if editing {
                    ProfileFormView(initial: profile, unit: unit, onSaved: { editing = false })
                        .toolbar {
                            // Back exits edit mode only; the profile sheet
                            // stays up. Done reappears in view mode.
                            ToolbarItem(placement: .topBarLeading) {
                                Button(L10n.back) { editing = false }
                            }
                        }
                } else {
                    List {
                        Section {
                            LabeledContent(L10n.name, value: profile.name)
                            LabeledContent(L10n.age, value: profile.age.map { "\($0)" } ?? "—")
                            LabeledContent(L10n.gender, value: profile.gender.map(L10n.genderLabel) ?? "—")
                            LabeledContent(L10n.weight, value: profile.weight.map { weightValueText($0.doubleValue) } ?? "—")
                            LabeledContent(L10n.height, value: profile.height.map { heightValueText($0.doubleValue) } ?? "—")
                        }
                        if let settings {
                            // Standalone calculators that don't belong to a
                            // saved setting — 1RM and BMI today, with room
                            // for more later. Sits above Settings so the
                            // profile's own numbers flow into the tools.
                            Section(L10n.tools) {
                                Button {
                                    showOneRmCalculator = true
                                } label: {
                                    LabeledContent(L10n.oneRmCalculator, value: formulaName(settings.oneRmFormula))
                                }
                                .tint(.primary)
                                Button {
                                    showBmiCalculator = true
                                } label: {
                                    LabeledContent(L10n.bmiCalculator, value: bmiFormulaName(settings.bmiFormula))
                                }
                                .tint(.primary)
                            }
                            // Always-editable app settings backed by
                            // user_settings; each change saves immediately, no
                            // Edit mode. Display labels live only here — the DB
                            // stores language-neutral keys, so they can be
                            // regionalized later.
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
                                    Image(systemName: "sun.max.fill").tag(Appearance.light)
                                    Image(systemName: "moon.fill").tag(Appearance.dark)
                                    Image(systemName: "circle.lefthalf.filled").tag(Appearance.system)
                                }
                                .pickerStyle(.segmented)
                                Picker(L10n.weightUnits, selection: settingBinding(settings.weightUnit, save: {
                                    try await UserSettingsRepository.shared.setWeightUnit(weightUnit: $0)
                                })) {
                                    Text(L10n.unitsMetric).tag(WeightUnit.metric)
                                    Text(L10n.unitsImperial).tag(WeightUnit.imperial)
                                }
                                VStack(alignment: .leading, spacing: 6) {
                                    LabeledContent(
                                        L10n.restTimerDuration,
                                        value: formatRestTimerDuration(Int(settings.restTimerDurationSeconds))
                                    )
                                    Slider(
                                        value: Binding(
                                            get: { Double(settings.restTimerDurationSeconds) },
                                            set: { raw in
                                                let snapped = Int32(UserSettingsKt.clampRestTimerSeconds(seconds: Int32(raw.rounded())))
                                                if snapped != settings.restTimerDurationSeconds {
                                                    Task {
                                                        try? await UserSettingsRepository.shared.setRestTimerDurationSeconds(seconds: snapped)
                                                    }
                                                }
                                            }
                                        ),
                                        in: Double(UserSettingsKt.MIN_REST_TIMER_SECONDS)...Double(UserSettingsKt.MAX_REST_TIMER_SECONDS),
                                        step: Double(UserSettingsKt.REST_TIMER_STEP_SECONDS)
                                    )
                                }
                                Picker(L10n.restTimerSound, selection: Binding(
                                    get: { settings.restTimerSound },
                                    set: { newSound in
                                        RestTimerSoundPlayer.play(sound: newSound, vibrate: false)
                                        Task {
                                            try? await UserSettingsRepository.shared.setRestTimerSound(sound: newSound)
                                        }
                                    }
                                )) {
                                    ForEach(RestTimerSound.entries, id: \.self) { sound in
                                        Text(L10n.restTimerSoundLabel(sound)).tag(sound)
                                    }
                                }
                                // Opting in first asks the OS for notification
                                // permission and only persists as on when the
                                // grant succeeds; the repository echo snaps the
                                // toggle back off after a denied prompt. When
                                // permission was already denied (iOS never
                                // re-prompts), point the user at Settings.
                                Toggle(L10n.sendNotifications, isOn: settingBinding(settings.notificationsEnabled, save: { enabled in
                                    var granted = false
                                    if enabled {
                                        let center = UNUserNotificationCenter.current()
                                        switch await center.notificationSettings().authorizationStatus {
                                        case .notDetermined:
                                            granted = (try? await center
                                                .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
                                        case .denied:
                                            showNotificationsDeniedAlert = true
                                        default:
                                            granted = true
                                        }
                                    }
                                    try await UserSettingsRepository.shared.setNotificationsEnabled(enabled: granted)
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
                    .alert(L10n.notificationsDeniedTitle, isPresented: $showNotificationsDeniedAlert) {
                        Button(L10n.openSettings) {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        }
                        Button(L10n.cancel, role: .cancel) {}
                    } message: {
                        Text(L10n.notificationsDeniedMessage)
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
                            // Partial bottom sheet, matching the Android
                            // ModalBottomSheet rather than a full-height cover.
                            .presentationDetents([.medium, .large])
                        }
                    }
                    .sheet(isPresented: $showBmiCalculator) {
                        if let settings {
                            NavigationStack {
                                BmiCalculatorView(settings: settings, profile: profile)
                                    .navigationTitle(L10n.bmiCalculator)
                                    .navigationBarTitleDisplayMode(.inline)
                            }
                            // Full-height cover: the inputs plus the formula and
                            // classification groups don't fit a partial sheet.
                            .presentationDetents([.large])
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

    private var unit: WeightUnit { settings?.weightUnit ?? .metric }

    private func formatRestTimerDuration(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let rem = seconds % 60
        return String(format: "%d:%02d", minutes, rem)
    }

    /// Stored kilograms rendered in the display unit: "82 kg" / "180.8 lb".
    private func weightValueText(_ kg: Double) -> String {
        unit == .imperial
            ? L10n.weightLbValue(InsightMath.shared.formatNumber(value: unit.fromKilograms(kg: kg), maxDecimals: 1))
            : L10n.weightKgValue(InsightMath.shared.formatNumber(value: kg, maxDecimals: 1))
    }

    /// Stored centimeters rendered in the display unit: "180 cm" / "5 ft 11 in".
    private func heightValueText(_ cm: Double) -> String {
        if unit == .imperial {
            let fi = HeightConversion.shared.cmToFeetInches(cm: cm)
            return L10n.heightFtInValue("\(fi.feet)", "\(fi.inches)")
        }
        return L10n.heightCmValue(InsightMath.shared.formatNumber(value: cm, maxDecimals: 1))
    }
}
