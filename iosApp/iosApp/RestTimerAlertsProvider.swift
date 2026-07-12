import AudioToolbox
import Foundation
import SharedUI
import UIKit
import UserNotifications

/// Native delivery for rest-timer expiry, injected into the shared
/// `RestTimerController` at app start (see iOSApp.swift). In-app expiry plays
/// a system bell and haptic; a local notification scheduled at timer start
/// covers backgrounded/locked expiry — iOS suspends the controller's tick
/// loop in the background, so `timerFinished()` only ever fires foregrounded.
final class RestTimerAlertsProvider: RestTimerAlerts {
    private static let notificationIdentifier = "rest_timer"

    func timerFinished() -> Bool {
        // 1007: short "ding" used by stock notification sounds — no bundled
        // asset needed. iOS suspends the tick loop in background, so this
        // only runs foregrounded — always delivered.
        AudioServicesPlaySystemSound(1007)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        return true
    }

    func scheduleExpiryNotification(endsAtMillis: Int64) {
        let interval = Double(endsAtMillis) / 1000 - Date().timeIntervalSince1970
        guard interval > 0 else { return }
        let center = UNUserNotificationCenter.current()
        center.getNotificationSettings { settings in
            guard settings.authorizationStatus == .authorized else { return }
            let content = UNMutableNotificationContent()
            content.title = L10n.restTimerNotificationTitle
            content.body = L10n.restTimerNotificationBody
            content.sound = .default
            // Fixed identifier: rescheduling (start/±10s) replaces the
            // pending request instead of stacking a second one.
            let request = UNNotificationRequest(
                identifier: Self.notificationIdentifier,
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(1, interval), repeats: false)
            )
            center.add(request)
        }
    }

    func cancelExpiryNotification() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.notificationIdentifier])
        center.removeDeliveredNotifications(withIdentifiers: [Self.notificationIdentifier])
    }
}

/// Display label for a rest-timer option: "Off" or the duration as digits.
enum RestTimerFormat {
    static func label(_ option: RestTimerOption) -> String {
        option == .off ? L10n.restTimerOff : format(seconds: Int(option.seconds))
    }

    static func format(seconds: Int) -> String {
        String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}
