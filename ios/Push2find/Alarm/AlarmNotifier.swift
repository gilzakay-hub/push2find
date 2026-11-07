import UserNotifications

/// Local notification shown while ringing, with a "Stop ringing" action on the lock screen.
///
/// It is also the fallback sound path: if the audio session could not start, the
/// notification plays the alarm sound itself. With the Critical Alerts entitlement
/// (AppConfig.criticalAlertsEnabled) that sound ignores mute and Do Not Disturb and
/// plays at full volume.
final class AlarmNotifier: NSObject, UNUserNotificationCenterDelegate {
    static let shared = AlarmNotifier()

    private static let categoryID = "P2F_ALARM"
    private static let stopActionID = "P2F_STOP"
    private static let requestID = "p2f-alarm"

    private let center = UNUserNotificationCenter.current()

    func configure() {
        center.delegate = self
        let stop = UNNotificationAction(identifier: Self.stopActionID, title: "Stop ringing", options: [.destructive])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: Self.categoryID, actions: [stop], intentIdentifiers: [], options: [])
        ])
    }

    func requestAuthorization(completion: ((Bool) -> Void)? = nil) {
        var options: UNAuthorizationOptions = [.alert, .sound]
        if AppConfig.criticalAlertsEnabled { options.insert(.criticalAlert) }
        center.requestAuthorization(options: options) { granted, _ in
            DispatchQueue.main.async { completion?(granted) }
        }
    }

    func isAuthorized(completion: @escaping (Bool) -> Void) {
        center.getNotificationSettings { s in
            DispatchQueue.main.async { completion(s.authorizationStatus == .authorized) }
        }
    }

    func postAlarm(audioStarted: Bool) {
        let content = UNMutableNotificationContent()
        content.title = "Your wristband is looking for this phone"
        content.body = "Tap Stop, or press the wristband button again."
        content.categoryIdentifier = Self.categoryID

        let file = UNNotificationSoundName(Sounds.option(for: SettingsStore.shared.soundKey).file)
        if AppConfig.criticalAlertsEnabled {
            content.sound = .criticalSoundNamed(file, withAudioVolume: 1.0)
            content.interruptionLevel = .critical
        } else {
            content.sound = audioStarted ? nil : UNNotificationSound(named: file)
            content.interruptionLevel = .timeSensitive
        }
        center.add(UNNotificationRequest(identifier: Self.requestID, content: content, trigger: nil))
    }

    func clearAlarm() {
        center.removeDeliveredNotifications(withIdentifiers: [Self.requestID])
        center.removePendingNotificationRequests(withIdentifiers: [Self.requestID])
    }

    // MARK: UNUserNotificationCenterDelegate

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        // In the foreground the app's own UI shows the Stop button; the sound is already playing.
        completionHandler([.list])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if response.notification.request.identifier == Self.requestID {
            DispatchQueue.main.async { AlarmPlayer.shared.stop() }
        }
        completionHandler()
    }
}
