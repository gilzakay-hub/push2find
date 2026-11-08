import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var ble: BLEManager
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var alarm: AlarmPlayer
    @State private var confirmForget = false

    private let durations: [(Int, String)] = [(10, "10 s"), (30, "30 s"), (60, "1 min"), (0, "Until stopped")]

    var body: some View {
        Form {
            Section("Ringtone") {
                ForEach(Sounds.all) { sound in
                    Button {
                        settings.soundKey = sound.id
                    } label: {
                        HStack {
                            Text(sound.label).foregroundStyle(.primary)
                            Spacer()
                            if settings.soundKey == sound.id {
                                Image(systemName: "checkmark").foregroundStyle(Color.signal)
                            }
                        }
                    }
                }
            }

            Section {
                Picker("Ring for", selection: $settings.durationSec) {
                    ForEach(durations, id: \.0) { Text($0.1).tag($0.0) }
                }
                Toggle("Vibrate too", isOn: $settings.vibrate)
            }

            Section {
                if alarm.isRinging {
                    Button("Stop", role: .destructive) { alarm.stop() }
                } else {
                    Button("Test ringtone") { alarm.start(fromWristband: false) }
                }
            } footer: {
                Text("The ring plays even with the Silent switch on. Its loudness follows the phone's media volume.")
            }

            Section("Device") {
                LabeledContent("Name", value: settings.pairedName ?? "—")
                LabeledContent("Status", value: ble.linkState == .connected ? "Connected" : "Not connected")
                LabeledContent("Battery", value: ble.battery.map { "\($0)%" } ?? "—")
                Button("Identify (blink)") { ble.identify() }
                    .disabled(ble.linkState != .connected)
                NavigationLink("Pair another device") { PairingView() }
                Button("Forget this device", role: .destructive) { confirmForget = true }
            }
        }
        .navigationTitle("Settings")
        .confirmationDialog("Forget this device?", isPresented: $confirmForget, titleVisibility: .visible) {
            Button("Forget", role: .destructive) { ble.forget() }
        }
    }
}
