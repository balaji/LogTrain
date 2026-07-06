import SwiftUI
import SharedUI

// MARK: - Coordinators

@Observable class AppNavigationCoordinator {
    enum AppTab { case exercises, programs, calendar, profile }
    var selectedTab: AppTab = .exercises
}

/// nil until the database's first emission, then tracks whether a profile row exists.
@Observable class ProfileState {
    var hasProfile: Bool? = nil
    private var observation: ProfileObservation?

    init() {
        observation = ProfileStore.shared.observeHasProfile { [weak self] value in
            self?.hasProfile = value.boolValue
        }
    }
}

// MARK: - Tab root style

private struct TabRootStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .navigationBarTitleDisplayMode(.inline)
            .scrollBounceBehavior(.basedOnSize)
    }
}

private extension View {
    func tabRootStyle() -> some View {
        modifier(TabRootStyle())
    }
}

// MARK: - Navigation shell

struct ContentView: View {
    @State private var profileState = ProfileState()
    @State private var appCoordinator = AppNavigationCoordinator()

    var body: some View {
        if profileState.hasProfile == true {
            TabView(selection: $appCoordinator.selectedTab) {
                Tab("Exercises", systemImage: "figure.strengthtraining.traditional",
                    value: AppNavigationCoordinator.AppTab.exercises) {
                    NavigationStack {
                        ExercisesView()
                            .navigationTitle("Exercises")
                            .tabRootStyle()
                    }
                }
                Tab("Workouts", systemImage: "list.clipboard",
                    value: AppNavigationCoordinator.AppTab.programs) {
                    ProgramsView()
                }
                Tab("Calendar", systemImage: "calendar",
                    value: AppNavigationCoordinator.AppTab.calendar) {
                    NavigationStack {
                        CalendarView()
                            .navigationTitle("Calendar")
                            .tabRootStyle()
                    }
                }
                Tab("Profile", systemImage: "person.crop.circle",
                    value: AppNavigationCoordinator.AppTab.profile) {
                    NavigationStack {
                        ProfileView()
                            .navigationTitle("Profile")
                            .tabRootStyle()
                    }
                }
            }
            .tabBarMinimizeBehavior(.automatic)
        } else if profileState.hasProfile == false {
            ProfileSetupView()
        } else {
            ProgressView()
        }
    }
}
