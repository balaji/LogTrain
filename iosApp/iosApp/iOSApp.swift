import SwiftUI
import SharedUI

@main
struct iOSApp: App {
    @State private var appearance: Appearance = .system

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(preferredScheme)
                .task {
                    for await settings in UserSettingsRepository.shared.settings {
                        appearance = settings.appearance
                    }
                }
        }
    }

    private var preferredScheme: ColorScheme? {
        switch appearance {
        case .light: .light
        case .dark: .dark
        case .system: nil
        }
    }
}
