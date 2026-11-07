import CoreBluetooth

/// Wristband BLE protocol – see PROTOCOL.md at the repository root.
/// Every UUID / value the app depends on lives here.
enum P2FProtocol {
    static let service = CBUUID(string: "7A1E0001-5C3D-4B8E-9F2A-1D6C0B7E9A10")
    static let buttonEvent = CBUUID(string: "7A1E0002-5C3D-4B8E-9F2A-1D6C0B7E9A10")
    static let control = CBUUID(string: "7A1E0003-5C3D-4B8E-9F2A-1D6C0B7E9A10")

    static let batteryService = CBUUID(string: "180F")
    static let batteryLevel = CBUUID(string: "2A19")

    static let namePrefix = "P2F"

    /// Event byte values (byte 0 of a Button Event notification). 0 = reserved.
    static let eventSingle: UInt8 = 0x01
    static let eventDouble: UInt8 = 0x02
    static let eventLong: UInt8 = 0x03

    static let commandIdentify: UInt8 = 0x01

    /// A press re-sent with the same sequence number inside this window is a duplicate.
    static let duplicateWindow: TimeInterval = 10
}

enum AppConfig {
    /// Set to true only after Apple grants the Critical Alerts entitlement
    /// (and it is enabled in Push2find.entitlements).
    static let criticalAlertsEnabled = false
}
