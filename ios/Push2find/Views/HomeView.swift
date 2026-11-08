import SwiftUI
import UIKit

struct ContentView: View {
    @EnvironmentObject private var settings: SettingsStore

    var body: some View {
        NavigationStack {
            if settings.pairedID == nil {
                PairingView(isFirstRun: true)
            } else {
                HomeView()
            }
        }
    }
}

struct HomeView: View {
    @EnvironmentObject private var ble: BLEManager
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var alarm: AlarmPlayer
    @Environment(\.scenePhase) private var scenePhase
    @State private var notificationsOK = true

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                StatusHero(state: ble.linkState, ringing: alarm.isRinging)
                    .padding(.top, 12)

                VStack(spacing: 4) {
                    Text(settings.pairedName ?? "Device").font(.headline)
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                }

                BatteryCard(level: ble.battery)

                if alarm.isRinging {
                    Button { alarm.stop() } label: {
                        Text("Stop ringing").bold().frame(maxWidth: .infinity, minHeight: 52)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.signal)
                } else {
                    Button { alarm.start(fromWristband: false) } label: {
                        Text("Test ring").frame(maxWidth: .infinity, minHeight: 52)
                    }
                    .buttonStyle(.bordered)
                }

                if let last = ble.lastPress {
                    Text("Last button press: \(last.formatted(date: .omitted, time: .shortened))")
                        .font(.footnote).foregroundStyle(.secondary)
                }

                if !notificationsOK {
                    HintCard(title: "Notifications are off",
                             text: "The phone will still ring, but you won't get a Stop button on the lock screen.",
                             action: "Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                    }
                }

                HintCard(title: "Keep Push2find in the app switcher",
                         text: "If you swipe the app away, iOS stops it from listening for the device until you open it again. Closing it normally (going to the home screen) is fine.",
                         action: nil, onAction: nil)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Push2find")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            NavigationLink { SettingsView() } label: { Image(systemName: "gearshape") }
                .accessibilityLabel("Settings")
        }
        .onAppear {
            AlarmNotifier.shared.requestAuthorization { _ in refreshNotificationState() }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { refreshNotificationState() }
        }
    }

    private func refreshNotificationState() {
        AlarmNotifier.shared.isAuthorized { notificationsOK = $0 }
    }

    private var subtitle: String {
        switch ble.linkState {
        case .connected: return "Press the device button to ring this phone"
        case .connecting: return "Connecting…"
        case .searching: return "Out of range – will reconnect automatically"
        case .bluetoothOff: return "Turn on Bluetooth to connect"
        case .unauthorized: return "Allow Bluetooth for Push2find in Settings"
        case .notPaired: return "No device paired"
        }
    }
}

// MARK: - Components

struct StatusHero: View {
    let state: LinkState
    let ringing: Bool
    @State private var pulse = false

    private var color: Color {
        if ringing { return .signal }
        switch state {
        case .connected: return .connected
        case .connecting, .searching: return .searching
        default: return .gray
        }
    }

    private var label: String {
        if ringing { return "Ringing" }
        switch state {
        case .connected: return "Connected"
        case .connecting: return "Connecting"
        case .searching: return "Searching"
        case .bluetoothOff: return "Bluetooth off"
        case .unauthorized: return "No access"
        case .notPaired: return "Not paired"
        }
    }

    private var animates: Bool { ringing || state == .connected || state == .searching }

    var body: some View {
        ZStack {
            if animates {
                Circle()
                    .fill(color)
                    .frame(width: 176, height: 176)
                    .scaleEffect(pulse ? 1.22 : 1)
                    .opacity(pulse ? 0 : 0.35)
                    .animation(.easeOut(duration: ringing ? 0.7 : 1.6).repeatForever(autoreverses: false), value: pulse)
            }
            Circle()
                .fill(color.opacity(0.12))
                .overlay(Circle().stroke(color, lineWidth: 3))
                .frame(width: 176, height: 176)
            VStack(spacing: 10) {
                Circle().fill(color).frame(width: 22, height: 22)
                Text(label).font(.title2.weight(.semibold))
            }
        }
        .frame(width: 230, height: 230)
        .onAppear { pulse = true }
        .accessibilityElement(children: .combine)
    }
}

struct BatteryCard: View {
    let level: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Device battery")
                Spacer()
                Text(level.map { "\($0)%" } ?? "—").font(.headline)
            }
            ProgressView(value: Double(level ?? 0), total: 100)
                .tint(level.map { $0 <= 15 ? Color.red : Color.connected } ?? .gray)
        }
        .padding(18)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
    }
}

struct HintCard: View {
    let title: String
    let text: String
    let action: String?
    let onAction: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline.weight(.semibold))
            Text(text).font(.footnote).foregroundStyle(.secondary)
            if let action, let onAction {
                Button(action, action: onAction).font(.subheadline.weight(.semibold)).padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 20))
    }
}
