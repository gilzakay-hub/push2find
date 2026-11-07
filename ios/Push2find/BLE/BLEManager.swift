import CoreBluetooth
import Foundation

enum LinkState {
    case notPaired, bluetoothOff, unauthorized, searching, connecting, connected
}

struct DiscoveredBand: Identifiable, Equatable {
    let id: UUID
    let name: String
    var rssi: Int
    let isPush2find: Bool
}

/// Owns the CoreBluetooth central and keeps the paired wristband connected.
///
/// Background behaviour:
///  - `bluetooth-central` background mode + a pending `connect()` means iOS itself
///    waits for the band and reconnects when it comes back in range – the request
///    never times out, even with the app suspended.
///  - State preservation/restoration (restore identifier) lets iOS relaunch the app
///    in the background to deliver a button press after the app was terminated by the
///    system. (Not after the *user* swipes it away – that is an iOS rule.)
///
/// Everything runs on the main queue.
final class BLEManager: NSObject, ObservableObject {
    static let shared = BLEManager()
    private static let restoreID = "com.push2find.central"

    @Published private(set) var linkState: LinkState = .notPaired
    @Published private(set) var battery: Int?
    @Published private(set) var discovered: [DiscoveredBand] = []
    @Published private(set) var lastPress: Date?

    /// Called on every (non-duplicate) button press.
    var onButtonPress: (() -> Void)?

    private enum ScanMode { case none, pairing(showAll: Bool), reconnect }

    private var central: CBCentralManager?
    private var peripheral: CBPeripheral?
    private var controlChar: CBCharacteristic?
    private var scanMode: ScanMode = .none
    private var candidates: [UUID: CBPeripheral] = [:]
    private var lastSeq: UInt8?
    private var lastEventAt = Date.distantPast
    private let settings = SettingsStore.shared

    private override init() { super.init() }

    /// Creates the central (triggers the Bluetooth permission prompt the first time).
    func activate() {
        guard central == nil else { return }
        central = CBCentralManager(delegate: self, queue: .main, options: [
            CBCentralManagerOptionRestoreIdentifierKey: Self.restoreID,
            CBCentralManagerOptionShowPowerAlertKey: true,
        ])
    }

    // MARK: - Pairing

    func startPairingScan(showAll: Bool) {
        activate()
        discovered = []
        candidates = [:]
        scanMode = .pairing(showAll: showAll)
        startScanIfPossible()
    }

    func stopScan() {
        scanMode = .none
        central?.stopScan()
    }

    func pair(_ band: DiscoveredBand) {
        guard let central, let p = candidates[band.id] else { return }
        stopScan()
        if let old = peripheral, old.identifier != p.identifier {
            central.cancelPeripheralConnection(old)
        }
        battery = nil
        settings.pairedID = p.identifier
        settings.pairedName = band.name
        attach(p)
        linkState = .connecting
        central.connect(p, options: nil)
    }

    func forget() {
        if let p = peripheral { central?.cancelPeripheralConnection(p) }
        peripheral = nil
        controlChar = nil
        settings.pairedID = nil
        settings.pairedName = nil
        battery = nil
        lastPress = nil
        linkState = .notPaired
    }

    func identify() {
        guard let p = peripheral, let c = controlChar, p.state == .connected else { return }
        let type: CBCharacteristicWriteType = c.properties.contains(.write) ? .withResponse : .withoutResponse
        p.writeValue(Data([P2FProtocol.commandIdentify]), for: c, type: type)
    }

    // MARK: - Internals

    private func attach(_ p: CBPeripheral) {
        peripheral = p
        p.delegate = self
    }

    private func startScanIfPossible() {
        guard let central, central.state == .poweredOn else { return }
        switch scanMode {
        case .pairing:
            // Foreground only, so an unfiltered scan is fine; results are filtered below.
            central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
        case .reconnect:
            // Background scans must filter by service UUID.
            central.scanForPeripherals(withServices: [P2FProtocol.service], options: nil)
        case .none:
            break
        }
    }

    /// (Re)establishes the pending connection to the paired band.
    private func connectToPaired() {
        guard let central else { return }
        guard let id = settings.pairedID else { linkState = .notPaired; return }
        guard central.state == .poweredOn else { return }

        if let p = peripheral, p.identifier == id {
            switch p.state {
            case .connected:
                linkState = .connecting
                p.discoverServices([P2FProtocol.service, P2FProtocol.batteryService])
            case .disconnected:
                linkState = .searching
                central.connect(p, options: nil)
            default:
                break
            }
            return
        }
        if let p = central.retrievePeripherals(withIdentifiers: [id]).first {
            attach(p)
            linkState = .searching
            central.connect(p, options: nil)
        } else {
            // iOS no longer knows this peripheral (e.g. Bluetooth reset) – look for it.
            linkState = .searching
            scanMode = .reconnect
            startScanIfPossible()
        }
    }

    private func handleButton(_ data: Data) {
        guard let event = data.first, event != 0 else { return }
        let now = Date()
        if data.count >= 2 {
            let seq = data[data.startIndex + 1]
            if seq == lastSeq, now.timeIntervalSince(lastEventAt) < P2FProtocol.duplicateWindow { return }
            lastSeq = seq
        }
        lastEventAt = now
        lastPress = now
        onButtonPress?()
    }
}

// MARK: - CBCentralManagerDelegate

extension BLEManager: CBCentralManagerDelegate {
    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        guard let restored = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] else { return }
        if let p = restored.first(where: { $0.identifier == settings.pairedID }) {
            attach(p)
        }
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            if case .pairing = scanMode { startScanIfPossible() }
            connectToPaired()
        case .poweredOff, .resetting:
            linkState = settings.pairedID == nil ? .notPaired : .bluetoothOff
        case .unauthorized:
            linkState = .unauthorized
        default:
            break
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover p: CBPeripheral,
                        advertisementData ad: [String: Any], rssi RSSI: NSNumber) {
        switch scanMode {
        case .pairing(let showAll):
            let rssi = RSSI.intValue
            guard rssi != 127 else { return } // 127 = unavailable
            let name = (ad[CBAdvertisementDataLocalNameKey] as? String) ?? p.name
            let uuids = ad[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []
            let match = uuids.contains(P2FProtocol.service) || (name?.hasPrefix(P2FProtocol.namePrefix) ?? false)
            guard match || showAll else { return }
            candidates[p.identifier] = p
            let band = DiscoveredBand(id: p.identifier, name: name ?? "Unnamed device", rssi: rssi, isPush2find: match)
            if let i = discovered.firstIndex(where: { $0.id == band.id }) {
                discovered[i].rssi = rssi
            } else {
                discovered.append(band)
            }
            discovered.sort { ($0.isPush2find ? 1 : 0, $0.rssi) > ($1.isPush2find ? 1 : 0, $1.rssi) }

        case .reconnect:
            // Peripheral identifiers are stable per phone, so this finds our band again.
            guard p.identifier == settings.pairedID else { return }
            stopScan()
            attach(p)
            central.connect(p, options: nil)

        case .none:
            break
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect p: CBPeripheral) {
        guard p.identifier == settings.pairedID else { central.cancelPeripheralConnection(p); return }
        linkState = .connecting
        p.discoverServices([P2FProtocol.service, P2FProtocol.batteryService])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect p: CBPeripheral, error: Error?) {
        guard p.identifier == settings.pairedID else { return }
        linkState = .searching
        central.connect(p, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral p: CBPeripheral, error: Error?) {
        controlChar = nil
        guard p.identifier == settings.pairedID else { return }
        linkState = central.state == .poweredOn ? .searching : .bluetoothOff
        // A pending connect never times out: iOS reconnects as soon as the band is back in range.
        if central.state == .poweredOn { central.connect(p, options: nil) }
    }
}

// MARK: - CBPeripheralDelegate

extension BLEManager: CBPeripheralDelegate {
    func peripheral(_ p: CBPeripheral, didDiscoverServices error: Error?) {
        guard error == nil else { central?.cancelPeripheralConnection(p); return }
        for s in p.services ?? [] {
            switch s.uuid {
            case P2FProtocol.service:
                p.discoverCharacteristics([P2FProtocol.buttonEvent, P2FProtocol.control], for: s)
            case P2FProtocol.batteryService:
                p.discoverCharacteristics([P2FProtocol.batteryLevel], for: s)
            default:
                break
            }
        }
    }

    func peripheral(_ p: CBPeripheral, didDiscoverCharacteristicsFor s: CBService, error: Error?) {
        guard error == nil else { return }
        for c in s.characteristics ?? [] {
            switch c.uuid {
            case P2FProtocol.buttonEvent:
                p.setNotifyValue(true, for: c)
            case P2FProtocol.control:
                controlChar = c
            case P2FProtocol.batteryLevel:
                if c.properties.contains(.notify) { p.setNotifyValue(true, for: c) }
                p.readValue(for: c)
            default:
                break
            }
        }
    }

    func peripheral(_ p: CBPeripheral, didUpdateNotificationStateFor c: CBCharacteristic, error: Error?) {
        if c.uuid == P2FProtocol.buttonEvent, c.isNotifying {
            linkState = .connected
        }
    }

    func peripheral(_ p: CBPeripheral, didUpdateValueFor c: CBCharacteristic, error: Error?) {
        guard error == nil, let data = c.value else { return }
        switch c.uuid {
        case P2FProtocol.buttonEvent:
            handleButton(data)
        case P2FProtocol.batteryLevel:
            if let level = data.first { battery = min(Int(level), 100) }
        default:
            break
        }
    }
}
