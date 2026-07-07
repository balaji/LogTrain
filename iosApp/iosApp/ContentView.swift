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
    @State private var activeSession: ActiveWorkoutSession?
    @State private var showDiscardConfirm = false

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
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if let session = activeSession, !WorkoutSessionPresentation.shared.isVisible {
                    ActiveSessionBar(
                        session: session,
                        onOpen: {
                            appCoordinator.selectedTab = .programs
                            WorkoutSessionPresentation.shared.requestOpen(
                                routineId: session.routineId,
                                title: session.title
                            )
                        },
                        onComplete: {
                            Task { try? await WorkoutSessionRepository.shared.finish(sessionId: session.sessionId) }
                        },
                        onDiscard: { showDiscardConfirm = true }
                    )
                }
            }
            .task {
                for await session in WorkoutSessionRepository.shared.activeSession {
                    activeSession = session
                }
            }
            .alert("Discard workout?", isPresented: $showDiscardConfirm) {
                Button("Discard", role: .destructive) {
                    if let session = activeSession {
                        Task { try? await WorkoutSessionRepository.shared.discard(sessionId: session.sessionId) }
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This workout and all logged sets will be deleted.")
            }
        } else if profileState.hasProfile == false {
            ProfileSetupView()
        } else {
            ProgressView()
        }
    }
}

// MARK: - Active session mini bar

/// Sits directly above the tab bar via `safeAreaInset` — the standard
/// "mini player" placement — while a workout is running but its own screen
/// isn't the one visible. Tapping the label re-opens the session; the
/// trailing actions mirror the ones in `WorkoutSessionView`'s toolbar.
private struct ActiveSessionBar: View {
    let session: ActiveWorkoutSession
    let onOpen: () -> Void
    let onComplete: () -> Void
    let onDiscard: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "figure.strengthtraining.traditional")
                    .foregroundStyle(Color.accentColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text(session.title)
                        .font(.subheadline.weight(.semibold))
                    Text(Date(timeIntervalSince1970: Double(session.startedAt) / 1000), style: .timer)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)

            Button(action: onDiscard) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(.red)
            }
            .buttonStyle(.plain)

            Button(action: onComplete) {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Color.accentColor)
            }
            .buttonStyle(.plain)
        }
        .font(.title3)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.bar)
    }
}
