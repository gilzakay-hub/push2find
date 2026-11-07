package com.push2find.app.ble

import java.util.UUID

/**
 * Wristband BLE protocol – see PROTOCOL.md at the repository root.
 * Every UUID / value the app depends on lives here, so that changes agreed
 * with the hardware team are a one-file edit.
 */
object P2FProtocol {
    val SERVICE: UUID = UUID.fromString("7a1e0001-5c3d-4b8e-9f2a-1d6c0b7e9a10")
    val BUTTON_EVENT: UUID = UUID.fromString("7a1e0002-5c3d-4b8e-9f2a-1d6c0b7e9a10")
    val CONTROL: UUID = UUID.fromString("7a1e0003-5c3d-4b8e-9f2a-1d6c0b7e9a10")

    val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    /** Client Characteristic Configuration Descriptor – enables notifications. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val NAME_PREFIX = "P2F"

    const val EVENT_SINGLE = 0x01
    const val EVENT_DOUBLE = 0x02
    const val EVENT_LONG = 0x03

    const val CMD_IDENTIFY: Byte = 0x01

    /** A press re-sent with the same sequence number inside this window is a duplicate. */
    const val DUPLICATE_WINDOW_MS = 10_000L
}
