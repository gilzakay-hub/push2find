import AVFoundation
import AudioToolbox
import UIKit

/// Plays the find-my-phone alarm.
///
/// Why it rings in silent mode: the audio session uses the `.playback` category,
/// which iOS does not mute with the Ring/Silent switch. Focus / Do Not Disturb only
/// filter notifications, not audio an app is playing.
///
/// Why it works from the background: the app is woken by the BLE notification
/// (`bluetooth-central` mode) and keeps running while it plays audio (`audio` mode).
final class AlarmPlayer: ObservableObject {
    static let shared = AlarmPlayer()

    @Published private(set) var isRinging = false

    private var player: AVAudioPlayer?
    private var stopTimer: Timer?
    private var vibrateTimer: Timer?
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid

    private init() {
        NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] note in
            self?.handleInterruption(note)
        }
    }

    /// - Parameter fromWristband: true → also post the alarm notification (Stop action on the lock screen).
    func start(fromWristband: Bool) {
        if isRinging { stop() }
        let settings = SettingsStore.shared
        beginBackgroundTask()

        let audioStarted = startAudio(soundKey: settings.soundKey)
        if settings.vibrate { startVibration() }
        isRinging = true

        if settings.durationSec > 0 {
            stopTimer = Timer.scheduledTimer(withTimeInterval: TimeInterval(settings.durationSec), repeats: false) { [weak self] _ in
                self?.stop()
            }
        }
        if fromWristband {
            AlarmNotifier.shared.postAlarm(audioStarted: audioStarted)
        }
    }

    func stop() {
        stopTimer?.invalidate(); stopTimer = nil
        vibrateTimer?.invalidate(); vibrateTimer = nil
        player?.stop(); player = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        isRinging = false
        AlarmNotifier.shared.clearAlarm()
        endBackgroundTask()
    }

    // MARK: - Audio

    private func startAudio(soundKey: String) -> Bool {
        guard let url = Sounds.url(for: soundKey) ?? Sounds.url(for: Sounds.defaultKey) else { return false }
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
        } catch {
            // iOS may refuse to interrupt other audio from the background; mixing is always allowed.
            do {
                try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
                try session.setActive(true)
            } catch {
                return false
            }
        }
        do {
            let p = try AVAudioPlayer(contentsOf: url)
            p.numberOfLoops = -1
            p.volume = 1.0
            p.prepareToPlay()
            guard p.play() else { return false }
            player = p
            return true
        } catch {
            return false
        }
    }

    private func handleInterruption(_ note: Notification) {
        guard isRinging,
              let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              AVAudioSession.InterruptionType(rawValue: raw) == .ended else { return }
        try? AVAudioSession.sharedInstance().setActive(true)
        player?.play()
    }

    private func startVibration() {
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        vibrateTimer = Timer.scheduledTimer(withTimeInterval: 1.2, repeats: true) { _ in
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
    }

    // MARK: - Background time (covers the case where audio could not start)

    private func beginBackgroundTask() {
        guard backgroundTask == .invalid else { return }
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "Push2find alarm") { [weak self] in
            self?.endBackgroundTask()
        }
    }

    private func endBackgroundTask() {
        guard backgroundTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTask)
        backgroundTask = .invalid
    }
}
