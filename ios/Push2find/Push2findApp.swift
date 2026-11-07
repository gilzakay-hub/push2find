import SwiftUI
import UIKit

@main
struct Push2findApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(BLEManager.shared)
                .environmentObject(SettingsStore.shared)
                .environmentObject(AlarmPlayer.shared)
                .tint(.signal)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        AlarmNotifier.shared.configure()

        BLEManager.shared.onButtonPress = {
            // Any press toggles: silent → ring, ringing → stop.
            let alarm = AlarmPlayer.shared
            if alarm.isRinging { alarm.stop() } else { alarm.start(fromWristband: true) }
        }

        // Create the central immediately when already paired, and always when iOS relaunched
        // us in the background to deliver a Bluetooth event (state restoration).
        if SettingsStore.shared.pairedID != nil || launchOptions?[.bluetoothCentrals] != nil {
            BLEManager.shared.activate()
        }
        return true
    }
}

extension Color {
    static let signal = Color(red: 1.0, green: 0.357, blue: 0.122)     // #FF5B1F
    static let connected = Color(red: 0.118, green: 0.62, blue: 0.353)  // #1E9E5A
    static let searching = Color(red: 0.89, green: 0.608, blue: 0.071)  // #E39B12
}
