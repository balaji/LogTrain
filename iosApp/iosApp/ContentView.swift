import SwiftUI
import SharedUI

// MARK: - Route bridging

/// Wraps a Kotlin Route for SwiftUI's NavigationStack path. UUID identity is
/// intentional: pushing the same route twice creates distinct stack entries.
struct RouteWrapper: Hashable, Identifiable {
    let id = UUID()
    let route: Route

    static func == (lhs: RouteWrapper, rhs: RouteWrapper) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

// MARK: - Coordinators

@Observable class TabNavigationCoordinator {
    var path: [RouteWrapper] = []
    func push(_ route: Route) { path.append(RouteWrapper(route: route)) }
    func pop() { if !path.isEmpty { path.removeLast() } }
}

@Observable class AppNavigationCoordinator {
    enum AppTab { case workouts, plans, profile }
    var selectedTab: AppTab = .workouts
    let workoutsCoordinator = TabNavigationCoordinator()
    let plansCoordinator = TabNavigationCoordinator()
    let profileCoordinator = TabNavigationCoordinator()
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

// MARK: - Compose hosting

struct TabRootComposeView: UIViewControllerRepresentable {
    let route: TopLevelRoute
    let coordinator: TabNavigationCoordinator

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(route: route) { route in
            coordinator.push(route)
        }
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct DetailComposeView: UIViewControllerRepresentable {
    let route: Route
    let coordinator: TabNavigationCoordinator

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.ScreenViewController(
            route: route,
            onNavigate: { route in coordinator.push(route) },
            onGoBack: { coordinator.pop() }
        )
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - Navigation shell

struct TabContentView: View {
    let route: TopLevelRoute
    let title: String
    @Bindable var coordinator: TabNavigationCoordinator

    var body: some View {
        NavigationStack(path: $coordinator.path) {
            TabRootComposeView(route: route, coordinator: coordinator)
                .ignoresSafeArea(.all)
                .navigationTitle(title)
                .toolbar {
                    if route is PlansRoute {
                        ToolbarItem(placement: .primaryAction) {
                            Button {
                                PlansCreateRequestNotifier.shared.requestCreate()
                            } label: {
                                Image(systemName: "plus")
                            }
                        }
                    }
                }
                .navigationDestination(for: RouteWrapper.self) { wrapper in
                    DetailComposeView(route: wrapper.route, coordinator: coordinator)
                        .ignoresSafeArea(.all)
                        .navigationTitle(wrapper.route.title)
                        .navigationSubtitle(wrapper.route.subtitle ?? "")
                        .toolbarTitleDisplayMode(.inline)
                }
        }
    }
}

struct ContentView: View {
    @State private var profileState = ProfileState()
    @State private var appCoordinator = AppNavigationCoordinator()

    var body: some View {
        if profileState.hasProfile == true {
            TabView(selection: $appCoordinator.selectedTab) {
                Tab("Workouts", systemImage: "figure.strengthtraining.traditional",
                    value: AppNavigationCoordinator.AppTab.workouts) {
                    TabContentView(
                        route: WorkoutsRoute.shared,
                        title: "Workouts",
                        coordinator: appCoordinator.workoutsCoordinator
                    )
                }
                Tab("Plans", systemImage: "list.clipboard",
                    value: AppNavigationCoordinator.AppTab.plans) {
                    TabContentView(
                        route: PlansRoute.shared,
                        title: "Plans",
                        coordinator: appCoordinator.plansCoordinator
                    )
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
