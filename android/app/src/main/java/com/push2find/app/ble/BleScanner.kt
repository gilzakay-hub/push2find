package com.push2find.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context

data class FoundDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val isPush2find: Boolean,
)

/** Foreground-only scanner used by the pairing screen. */
@SuppressLint("MissingPermission")
class BleScanner(private val context: Context) {
    private var callback: ScanCallback? = null
    private val scanner get() = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner

    /** @param showAll also report devices that don't look like a Push2find band (developer option). */
    fun start(showAll: Boolean, onFound: (FoundDevice) -> Unit, onError: (Int) -> Unit): Boolean {
        stop()
        val s = scanner ?: return false
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull()
                val advertisesService = r.scanRecord?.serviceUuids?.any { it.uuid == P2FProtocol.SERVICE } == true
                val match = advertisesService || name?.startsWith(P2FProtocol.NAME_PREFIX) == true
                if (match || showAll) onFound(FoundDevice(r.device.address, name, r.rssi, match))
            }

            override fun onScanFailed(errorCode: Int) = onError(errorCode)
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return runCatching { s.startScan(null, settings, cb) }
            .onSuccess { callback = cb }
            .isSuccess
    }

    fun stop() {
        val cb = callback ?: return
        runCatching { scanner?.stopScan(cb) }
        callback = null
    }
}
