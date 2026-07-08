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
    @State private var showNothingToSave = false

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
                    .modifier(sessionBarInset)
                }
                Tab("Workouts", systemImage: "list.clipboard",
                    value: AppNavigationCoordinator.AppTab.programs) {
                    ProgramsView()
                        .modifier(sessionBarInset)
                }
                Tab("Calendar", systemImage: "calendar",
                    value: AppNavigationCoordinator.AppTab.calendar) {
                    NavigationStack {
                        // Title comes from CalendarView: it swaps between the
                        // grid and the workout-history list.
                        CalendarView()
                            .tabRootStyle()
                    }
                    .modifier(sessionBarInset)
                }
                Tab("Profile", systemImage: "person.crop.circle",
                    value: AppNavigationCoordinator.AppTab.profile) {
                    NavigationStack {
                        ProfileView()
                            .navigationTitle("Profile")
                            .tabRootStyle()
                    }
                    .modifier(sessionBarInset)
                }
            }
            .tabBarMinimizeBehavior(.automatic)
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
            .alert("Nothing to save", isPresented: $showNothingToSave) {
                Button("Yes", role: .destructive) {
                    if let session = activeSession {
                        Task { try? await WorkoutSessionRepository.shared.discard(sessionId: session.sessionId) }
                    }
                }
                Button("No", role: .cancel) {}
            } message: {
                Text("There are no completed sets to save. Discard?")
            }
        } else if profileState.hasProfile == false {
            ProfileSetupView()
        } else {
            ProgressView()
        }
    }

    private var sessionBarInset: ActiveSessionBarInset {
        ActiveSessionBarInset(
            session: activeSession,
            onOpen: { session in
                appCoordinator.selectedTab = .programs
                WorkoutSessionPresentation.shared.requestOpen(
                    routineId: session.routineId,
                    title: session.title
                )
            },
            onComplete: { session in
                Task {
                    let hasSets = try? await WorkoutSessionRepository.shared.hasCompletedSets(
                        sessionId: session.sessionId
                    )
                    if hasSets?.boolValue == true {
                        try? await WorkoutSessionRepository.shared.finish(sessionId: session.sessionId)
                    } else {
                        showNothingToSave = true
                    }
                }
            },
            onDiscard: { showDiscardConfirm = true }
        )
    }
}

// MARK: - Active session mini bar

/// Inserts the mini bar into a tab's bottom safe area. Applied to each
/// tab's content rather than the TabView: inside a tab the bottom safe
/// area ends above the tab bar, whereas an inset (or bottom accessory)
/// on the TabView itself either covers the floating tab bar or leaves
/// an empty capsule behind when there is no bar to show.
private struct ActiveSessionBarInset: ViewModifier {
    let session: ActiveWorkoutSession?
    let onOpen: (ActiveWorkoutSession) -> Void
    let onComplete: (ActiveWorkoutSession) -> Void
    let onDiscard: () -> Void

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            if let session, !WorkoutSessionPresentation.shared.isSessionScreenVisible {
                ActiveSessionBar(
                    session: session,
                    onOpen: { onOpen(session) },
                    onComplete: { onComplete(session) },
                    onDiscard: onDiscard
                )
            }
        }
    }
}

/// Floats above the tab bar in a glass capsule — the "mini player" look —
/// while a workout is running but its own screen isn't the one visible.
/// Tapping the label re-opens the session; the trailing actions mirror
/// the ones in `WorkoutSessionView`'s toolbar.
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
        .glassEffect()
        .padding(.horizontal, 12)
        .padding(.bottom, 6)
    }
}
