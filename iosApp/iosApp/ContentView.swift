import SwiftUI
import SharedUI

// MARK: - Coordinators

@Observable class AppNavigationCoordinator {
    enum AppTab { case exercises, plans, profile }
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
                    }
                }
                Tab("Plans", systemImage: "list.clipboard",
                    value: AppNavigationCoordinator.AppTab.plans) {
                    NavigationStack {
                        PlansView()
                            .navigationTitle("Plans")
                    }
                }
                Tab("Profile", systemImage: "person.crop.circle",
                    value: AppNavigationCoordinator.AppTab.profile) {
                    NavigationStack {
                        ProfileView()
                            .navigationTitle("Profile")
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
