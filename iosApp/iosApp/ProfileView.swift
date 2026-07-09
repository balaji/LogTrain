import SwiftUI
import SharedUI

/// Native SwiftUI replacement for the Compose `ProfileScreen`: shows the
/// saved profile with an Edit link that swaps in `ProfileFormView`.
struct ProfileView: View {
    @State private var profile: Profile?
    @State private var settings: UserSettings?
    @State private var editing = false

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
                            LabeledContent("Gender", value: profile.gender ?? "—")
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
