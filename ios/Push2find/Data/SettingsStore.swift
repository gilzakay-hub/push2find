import Foundation

struct SoundOption: Identifiable, Hashable {
    let id: String
    let label: String
    let file: String   // bundled .wav, also used as the notification sound
}

enum Sounds {
    static let defaultKey = "classic"
    static let all: [SoundOption] = [
        SoundOption(id: "classic", label: "Classic beep", file: "p2f_classic.wav"),
        SoundOption(id: "siren", label: "Siren", file: "p2f_siren.wav"),
        SoundOption(id: "chime", label: "Chime", file: "p2f_chime.wav"),
    ]

    static func option(for key: String) -> SoundOption {
        all.first { $0.id == key } ?? all[0]
    }

    static func url(for key: String) -> URL? {
        let file = option(for: key).file as NSString
        return Bundle.main.url(forResource: file.deletingPathExtension, withExtension: file.pathExtension)
    }
}

/// Persistent settings. No accounts, no cloud – everything stays on the phone.
final class SettingsStore: ObservableObject {
    static let shared = SettingsStore()
    private let defaults = UserDefaults.standard

    @Published var pairedID: UUID? {
        didSet { defaults.set(pairedID?.uuidString, forKey: Keys.pairedID) }
    }
    @Published var pairedName: String? {
        didSet { defaults.set(pairedName, forKey: Keys.pairedName) }
    }
    @Published var soundKey: String {
        didSet { defaults.set(soundKey, forKey: Keys.sound) }
    }
    /// Seconds. 0 = ring until stopped.
    @Published var durationSec: Int {
        didSet { defaults.set(durationSec, forKey: Keys.duration) }
    }
    @Published var vibrate: Bool {
        didSet { defaults.set(vibrate, forKey: Keys.vibrate) }
    }

    private init() {
        pairedID = defaults.string(forKey: Keys.pairedID).flatMap(UUID.init(uuidString:))
        pairedName = defaults.string(forKey: Keys.pairedName)
        soundKey = defaults.string(forKey: Keys.sound) ?? Sounds.defaultKey
        durationSec = defaults.object(forKey: Keys.duration) as? Int ?? 30
        vibrate = defaults.object(forKey: Keys.vibrate) as? Bool ?? true
    }

    private enum Keys {
        static let pairedID = "pairedID"
        static let pairedName = "pairedName"
        static let sound = "sound"
        static let duration = "durationSec"
        static let vibrate = "vibrate"
    }
}
