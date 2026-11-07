import SwiftUI

struct PairingView: View {
    var isFirstRun: Bool = false

    @EnvironmentObject private var ble: BLEManager
    @Environment(\.dismiss) private var dismiss
    @State private var showAll = false

    var body: some View {
        List {
            Section {
                Text("Hold the wristband button for 3 seconds until the light blinks, then tap it below.")
                    .foregroundStyle(.secondary)
            }

            Section {
                if ble.linkState == .bluetoothOff {
                    Label("Bluetooth is off", systemImage: "exclamationmark.triangle")
                } else if ble.linkState == .unauthorized {
                    Label("Allow Bluetooth for Push2find in Settings", systemImage: "lock")
                } else if ble.discovered.isEmpty {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("Looking for wristbands nearby…")
                    }
                }
                ForEach(ble.discovered) { band in
                    Button {
                        ble.pair(band)
                        if !isFirstRun { dismiss() }
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(band.name).font(.body.weight(.semibold)).foregroundStyle(.primary)
                                if !band.isPush2find {
                                    Text("Not a Push2find device").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            SignalBars(rssi: band.rssi)
                        }
                    }
                }
            } header: {
                Text("Nearby")
            }

            Section {
                Toggle("Show all Bluetooth devices", isOn: $showAll)
            } footer: {
                Text("For testing with a prototype that doesn't advertise the Push2find service yet.")
            }
        }
        .navigationTitle("Pair wristband")
        .onAppear { ble.startPairingScan(showAll: showAll) }
        .onDisappear { ble.stopScan() }
        .onChange(of: showAll) { ble.startPairingScan(showAll: $0) }
    }
}

struct SignalBars: View {
    let rssi: Int

    private var bars: Int {
        switch rssi {
        case (-55)...: return 4
        case (-67)...: return 3
        case (-80)...: return 2
        default: return 1
        }
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: 3) {
            ForEach(1...4, id: \.self) { i in
                RoundedRectangle(cornerRadius: 2)
                    .fill(i <= bars ? Color.connected : Color.gray.opacity(0.3))
                    .frame(width: 5, height: CGFloat(6 + i * 4))
            }
        }
        .accessibilityLabel("Signal \(bars) of 4")
    }
}
