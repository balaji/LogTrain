import SwiftUI
import SharedUI
import AudioToolbox
import AVFoundation
import UserNotifications

private let restTimerNotificationId = "logtrain_rest_timer"

/// Plays and prepares the rest-timer completion tones for both foreground
/// alerts/previews (`AVAudioPlayer`) and background notifications
/// (`UNNotificationSound`, which searches `~/Library/Sounds` and bundle root).
enum RestTimerSoundPlayer {
    private static var audioPlayer: AVAudioPlayer?

    static func fileName(for sound: RestTimerSound) -> String? {
        guard let base = resourceBaseName(for: sound) else { return nil }
        return "\(base).wav"
    }

    private static func resourceBaseName(for sound: RestTimerSound) -> String? {
        switch sound {
        case .proxima: return "rest_proxima"
        case .mira: return "rest_mira"
        case .antares: return "rest_antares"
        case .altair: return "rest_altair"
        case .deneb: return "rest_deneb"
        default: return nil
        }
    }

    private static func bundleURL(for sound: RestTimerSound) -> URL? {
        guard let base = resourceBaseName(for: sound) else { return nil }
        return Bundle.main.url(forResource: base, withExtension: "wav")
            ?? Bundle.main.url(forResource: base, withExtension: "wav", subdirectory: "Sounds")
    }

    /// Ensures the `.wav` file is present in `~/Library/Sounds` so
    /// `UNNotificationSound(named:)` can resolve it even if Xcode nests `Sounds/`.
    static func ensureLibrarySound(for sound: RestTimerSound) {
        guard let name = fileName(for: sound),
              let src = bundleURL(for: sound),
              let libraryDir = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask).first else {
            return
        }
        let soundsDir = libraryDir.appendingPathComponent("Sounds", isDirectory: true)
        let dst = soundsDir.appendingPathComponent(name)
        if FileManager.default.fileExists(atPath: dst.path) { return }
        try? FileManager.default.createDirectory(at: soundsDir, withIntermediateDirectories: true)
        try? FileManager.default.copyItem(at: src, to: dst)
    }

    static func notificationSound(for sound: RestTimerSound) -> UNNotificationSound? {
        if sound == .none { return nil }
        if let name = fileName(for: sound) {
            ensureLibrarySound(for: sound)
            return UNNotificationSound(named: UNNotificationSoundName(name))
        }
        return .default
    }

    static func play(sound: RestTimerSound, vibrate: Bool) {
        audioPlayer?.stop()
        if sound == .none {
            audioPlayer = nil
        } else if let url = bundleURL(for: sound) {
            try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.mixWithOthers])
            try? AVAudioSession.sharedInstance().setActive(true)
            audioPlayer = try? AVAudioPlayer(contentsOf: url)
            audioPlayer?.play()
        } else {
            // System sound 1005 (alarm/bell chime).
            AudioServicesPlaySystemSound(1005)
        }
        if vibrate {
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }
}

// MARK: - Coordinators

@Observable class AppNavigationCoordinator {
    enum AppTab { case exercises, calendar, progress, profile }
    var selectedTab: AppTab = .exercises
    var showingWorkouts = false
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

// MARK: - Workouts entry point

/// Top-trailing button shown on every tab root now that Workouts no longer
/// has a tab of its own; presents the workouts/programs sheet. Detail screens
/// never show it — their nav bars belong to contextual actions.
private struct WorkoutsToolbar: ViewModifier {
    let onOpen: () -> Void

    func body(content: Content) -> some View {
        content
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onOpen) {
                        Image(systemName: "list.clipboard")
                    }
                    .accessibilityLabel(L10n.tabWorkouts)
                }
            }
    }
}

private struct WorkoutsSheet: View {
    let activeSession: ActiveWorkoutSession?
    let restRemainingSeconds: Int
    let restTotalSeconds: Int
    @State private var appearance: Appearance = .system
    @State private var systemColorScheme: ColorScheme = .light
    @State private var showDiscardConfirm = false
    @State private var showNothingToSave = false

    var body: some View {
        ProgramsView()
            .modifier(
                ActiveSessionBarInset(
                    session: activeSession,
                    restRemainingSeconds: restRemainingSeconds,
                    restTotalSeconds: restTotalSeconds,
                    onOpen: { session in
                        WorkoutSessionPresentation.shared.requestOpen(
                            routineId: session.routineId,
                            title: session.title
                        )
                    },
                    onComplete: { session in
                        completeWorkoutSession(session) { showNothingToSave = true }
                    },
                    onDiscard: { showDiscardConfirm = true }
                )
            )
            .modifier(
                ActiveSessionAlerts(
                    activeSession: activeSession,
                    showDiscardConfirm: $showDiscardConfirm,
                    showNothingToSave: $showNothingToSave
                )
            )
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

private func completeWorkoutSession(
    _ session: ActiveWorkoutSession,
    onNothingToSave: @escaping @MainActor () -> Void
) {
    Task {
        let hasSets = try? await WorkoutSessionRepository.shared.hasCompletedSets(
            sessionId: session.sessionId
        )
        if hasSets?.boolValue == true {
            try? await WorkoutSessionRepository.shared.finish(sessionId: session.sessionId)
        } else {
            await onNothingToSave()
        }
    }
}

private struct ActiveSessionAlerts: ViewModifier {
    let activeSession: ActiveWorkoutSession?
    @Binding var showDiscardConfirm: Bool
    @Binding var showNothingToSave: Bool

    func body(content: Content) -> some View {
        content
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
    }
}

extension View {
    func workoutsToolbar(onOpen: @escaping () -> Void) -> some View {
        modifier(WorkoutsToolbar(onOpen: onOpen))
    }
}

// MARK: - Navigation shell

struct ContentView: View {
    @State private var profileState = ProfileState()
    @State private var appCoordinator = AppNavigationCoordinator()
    @State private var activeSession: ActiveWorkoutSession?
    @State private var activeRestTimer: RestTimerTarget?
    @State private var restRemainingSeconds = 0
    @State private var notificationsEnabled = false
    @State private var restTimerSound: RestTimerSound = .default_
    @State private var lastStartedTimerMillis: Int64 = 0
    @State private var lastFinishedTimerMillis: Int64 = 0
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
                            .workoutsToolbar { appCoordinator.showingWorkouts = true }
                            .tabRootStyle()
                    }
                    .modifier(sessionBarInset)
                }
                Tab(L10n.tabHistory, systemImage: "calendar",
                    value: AppNavigationCoordinator.AppTab.calendar) {
                    NavigationStack {
                        CalendarView()
                            .workoutsToolbar { appCoordinator.showingWorkouts = true }
                            .tabRootStyle()
                    }
                    .modifier(sessionBarInset)
                }
                Tab(L10n.tabProgress, systemImage: "chart.line.uptrend.xyaxis",
                    value: AppNavigationCoordinator.AppTab.progress) {
                    NavigationStack {
                        ProgressTabView()
                            .navigationTitle(L10n.tabProgress)
                            .workoutsToolbar { appCoordinator.showingWorkouts = true }
                            .tabRootStyle()
                    }
                    .modifier(sessionBarInset)
                }
                Tab(L10n.profile, systemImage: "gearshape",
                    value: AppNavigationCoordinator.AppTab.profile) {
                    NavigationStack {
                        ProfileView()
                            .navigationTitle(L10n.profile)
                            .workoutsToolbar { appCoordinator.showingWorkouts = true }
                            .tabRootStyle()
                    }
                    .modifier(sessionBarInset)
                }
            }
            .tabBarMinimizeBehavior(.automatic)
            .sheet(isPresented: $appCoordinator.showingWorkouts) {
                WorkoutsSheet(
                    activeSession: activeSession,
                    restRemainingSeconds: restRemainingSeconds,
                    restTotalSeconds: Int(activeRestTimer?.durationSeconds ?? WorkoutSessionFormStateKt.REST_TIMER_DURATION_SECONDS)
                )
            }
            .task {
                for await session in WorkoutSessionRepository.shared.activeSession {
                    activeSession = session
                }
            }
            .task {
                for await timer in WorkoutSessionRepository.shared.activeRestTimer {
                    activeRestTimer = timer
                }
            }
            .task {
                for await settings in UserSettingsRepository.shared.settings {
                    notificationsEnabled = settings.notificationsEnabled
                    restTimerSound = settings.restTimerSound
                }
            }
            .task(id: activeRestTimer?.startedAtMillis) {
                await followRestTimer()
            }
            .onChange(of: notificationsEnabled) { _, enabled in
                if let timer = activeRestTimer {
                    if enabled {
                        scheduleRestNotification(endsAtMillis: timer.endsAtMillis)
                    } else {
                        cancelRestNotification()
                    }
                }
            }
            .onChange(of: restTimerSound) { _, _ in
                if let timer = activeRestTimer, notificationsEnabled {
                    scheduleRestNotification(endsAtMillis: timer.endsAtMillis)
                }
            }
            .modifier(
                ActiveSessionAlerts(
                    activeSession: activeSession,
                    showDiscardConfirm: $showDiscardConfirm,
                    showNothingToSave: $showNothingToSave
                )
            )
        } else if profileState.hasProfile == false {
            ProfileSetupView()
        } else {
            ProgressView()
        }
    }

    private var sessionBarInset: ActiveSessionBarInset {
        ActiveSessionBarInset(
            session: activeSession,
            restRemainingSeconds: restRemainingSeconds,
            restTotalSeconds: Int(activeRestTimer?.durationSeconds ?? WorkoutSessionFormStateKt.REST_TIMER_DURATION_SECONDS),
            onOpen: { session in
                appCoordinator.showingWorkouts = true
                WorkoutSessionPresentation.shared.requestOpen(
                    routineId: session.routineId,
                    title: session.title
                )
            },
            onComplete: { session in
                completeWorkoutSession(session) { showNothingToSave = true }
            },
            onDiscard: { showDiscardConfirm = true }
        )
    }

    private func followRestTimer() async {
        guard let timer = activeRestTimer else {
            restRemainingSeconds = 0
            if lastStartedTimerMillis != 0 && lastStartedTimerMillis != lastFinishedTimerMillis {
                cancelRestNotification()
            }
            lastStartedTimerMillis = 0
            return
        }
        lastStartedTimerMillis = timer.startedAtMillis
        if notificationsEnabled {
            scheduleRestNotification(endsAtMillis: timer.endsAtMillis)
        } else {
            cancelRestNotification()
        }
        while !Task.isCancelled {
            let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
            let remainingMs = timer.endsAtMillis - nowMillis
            if remainingMs <= 0 {
                restRemainingSeconds = 0
                lastFinishedTimerMillis = timer.startedAtMillis
                WorkoutSessionRepository.shared.clearActiveRestTimer()
                if remainingMs > -3000 {
                    cancelRestNotification()
                    playRestFinishedAlert()
                }
                break
            }
            restRemainingSeconds = Int((remainingMs + 999) / 1000)
            try? await Task.sleep(nanoseconds: 250_000_000)
        }
    }

    private func scheduleRestNotification(endsAtMillis: Int64) {
        let seconds = Double(endsAtMillis) / 1000.0 - Date().timeIntervalSince1970
        guard seconds > 0.5 else { return }
        let content = UNMutableNotificationContent()
        content.title = L10n.restOverTitle
        content.body = L10n.restOverMessage
        content.sound = RestTimerSoundPlayer.notificationSound(for: restTimerSound)
        content.interruptionLevel = .timeSensitive
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
        let request = UNNotificationRequest(
            identifier: restTimerNotificationId,
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(request)
    }

    private func cancelRestNotification() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [restTimerNotificationId])
        center.removeDeliveredNotifications(withIdentifiers: [restTimerNotificationId])
    }

    private func playRestFinishedAlert() {
        RestTimerSoundPlayer.play(sound: restTimerSound, vibrate: true)
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
    let restRemainingSeconds: Int
    let restTotalSeconds: Int
    let onOpen: (ActiveWorkoutSession) -> Void
    let onComplete: (ActiveWorkoutSession) -> Void
    let onDiscard: () -> Void

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            if let session, !WorkoutSessionPresentation.shared.isSessionScreenVisible {
                ActiveSessionBar(
                    session: session,
                    restRemainingSeconds: restRemainingSeconds,
                    restTotalSeconds: restTotalSeconds,
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
    let restRemainingSeconds: Int
    let restTotalSeconds: Int
    let onOpen: () -> Void
    let onComplete: () -> Void
    let onDiscard: () -> Void

    private var restProgress: Double {
        let total = Double(max(1, restTotalSeconds))
        return min(max(Double(restRemainingSeconds) / total, 0), 1)
    }

    var body: some View {
        VStack(spacing: 0) {
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

            if restRemainingSeconds > 0 {
                ProgressView(value: restProgress)
                    .progressViewStyle(.linear)
                    .tint(Color.accentColor)
                    .padding(.horizontal, 16)
            }
        }
        .glassEffect()
        .padding(.horizontal, 12)
        .padding(.bottom, 6)
    }
}
