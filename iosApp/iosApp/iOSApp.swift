import SwiftUI
import SharedUI

@main
struct iOSApp: App {
    @State private var appearance: Appearance = .system
    @State private var systemColorScheme: ColorScheme = .light

    var body: some Scene {
        WindowGroup {
            ContentView()
                .observeSystemColorScheme($systemColorScheme)
                .preferredColorScheme(preferredScheme)
                .task {
                    seedTestDataIfRequested()
                    for await settings in UserSettingsRepository.shared.settings {
                        appearance = settings.appearance
                    }
                }
        }
    }

    // Resolves "system" to a concrete scheme (rather than nil) so it behaves
    // consistently with ProfileSheet's own resolution; see SystemColorScheme.swift.
    private var preferredScheme: ColorScheme? {
        switch appearance {
        case .light: .light
        case .dark: .dark
        case .system: systemColorScheme
        }
    }

    // Test harness hook: launching with `-seedTestData <days>` (surfaced via
    // the UserDefaults argument domain) on a fresh debug install lands the app
    // on a populated profile (see scripts/run-ios.py --seed).
    private func seedTestDataIfRequested() {
        #if DEBUG
        let days = UserDefaults.standard.integer(forKey: "seedTestData")
        if days > 0 {
            DevSeed.shared.seedIfNoProfile(days: Int32(days))
        }
        #endif
    }
}
