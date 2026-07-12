import SwiftUI
import SharedUI

// MARK: - Coordinators

@Observable class AppNavigationCoordinator {
    enum AppTab { case exercises, programs, calendar, progress }
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

// MARK: - Profile entry point

/// Top-leading avatar button shown on every tab root now that Profile no
/// longer has a tab of its own; presents the profile page as a sheet.
/// Detail screens never show it — their nav bars belong to contextual actions.
private struct ProfileToolbar: ViewModifier {
    @State private var showingProfile = false

    func body(content: Content) -> some View {
        content
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showingProfile = true
                    } label: {
                        Image(systemName: "person.crop.circle")
                    }
                    .accessibilityLabel(L10n.profile)
                }
            }
            .sheet(isPresented: $showingProfile) {
                ProfileSheet()
            }
    }
}

private struct ProfileSheet: View {
    @State private var appearance: Appearance = .system
    @State private var systemColorScheme: ColorScheme = .light

    var body: some View {
        NavigationStack {
            // ProfileView owns the nav-bar buttons: Done (dismisses this
            // sheet) in view mode, Back (exits edit mode only) while editing.
            ProfileView()
                .navigationTitle(L10n.profile)
                .toolbarTitleDisplayMode(.inline)
        }
        .observeSystemColorScheme($systemColorScheme)
        .preferredColorScheme(preferredScheme)
        .task {
            for await settings in UserSettingsRepository.shared.settings {
                appearance = settings.appearance
            }
        }
    }

    // Resolving to nil here doesn't reliably clear a previously-set
    // .preferredColorScheme override on a *presented* sheet (SwiftUI/UIKit
    // bug), so "system" resolves to a concrete tracked value instead; see
    // SystemColorScheme.swift.
    private var preferredScheme: ColorScheme? {
        switch appearance {
        case .light: .light
        case .dark: .dark
        case .system: systemColorScheme
        }
    }
}

extension View {
    func profileToolbar() -> some View {
        modifier(ProfileToolbar())
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
                Tab(L10n.tabExercises, systemImage: "figure.strengthtraining.traditional",
                    value: AppNavigationCoordinator.AppTab.exercises) {
                    NavigationStack {
                        ExercisesView()
                            .navigationTitle(L10n.tabExercises)
                            .profileToolbar()
                            .tabRootStyle()
                    }
                    .modifier(sessionBarInset)
                }
                Tab(L10n.tabWorkouts, systemImage: "list.clipboard",
                    value: AppNavigationCoordinator.AppTab.programs) {
                    ProgramsView()
                        .modifier(sessionBarInset)
                }
                Tab(L10n.tabHistory, systemImage: "calendar",
                    value: AppNavigationCoordinator.AppTab.calendar) {
                    NavigationStack {
                        CalendarView()
                            .profileToolbar()
                            .tabRootStyle()
                    }
                    .modifier(sessionBarInset)
                }
                Tab(L10n.tabProgress, systemImage: "chart.line.uptrend.xyaxis",
                    value: AppNavigationCoordinator.AppTab.progress) {
                    NavigationStack {
                        ProgressTabView()
                            .navigationTitle(L10n.tabProgress)
                            .profileToolbar()
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
            .alert(L10n.discardWorkoutTitle, isPresented: $showDiscardConfirm) {
                Button(L10n.discard, role: .destructive) {
                    if let session = activeSession {
                        Task { try? await WorkoutSessionRepository.shared.discard(sessionId: session.sessionId) }
                    }
                }
                Button(L10n.cancel, role: .cancel) {}
            } message: {
                Text(L10n.discardWorkoutMessage)
            }
            .alert(L10n.nothingToSaveTitle, isPresented: $showNothingToSave) {
                Button(L10n.yes, role: .destructive) {
                    if let session = activeSession {
                        Task { try? await WorkoutSessionRepository.shared.discard(sessionId: session.sessionId) }
                    }
                }
                Button(L10n.no, role: .cancel) {}
            } message: {
                Text(L10n.nothingToSaveMessage)
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
