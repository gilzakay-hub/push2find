package com.push2find.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.push2find.app.data.AppState
import com.push2find.app.data.LinkState
import com.push2find.app.data.Permissions

/**
 * Keeps a single wristband connected for as long as possible.
 *
 * Strategy:
 *  - first attempt is a direct connection (fast, ~30 s timeout);
 *  - after any disconnect we open the GATT with autoConnect = true, which lets the
 *    Bluetooth controller wait for the band in hardware, at very low power, for as
 *    long as it takes – this is what makes "walk back into range → reconnect" work;
 *  - GATT operations are serialized through a small queue (Android allows only one
 *    in flight at a time).
 *
 * All state is touched on the main thread only.
 */
@SuppressLint("MissingPermission") // every entry point checks Permissions.hasBluetooth
class WristbandLink(
    private val context: Context,
    private val onButtonEvent: (event: Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var targetAddress: String? = null
    private var retry = 0

    private var lastSeq = -1
    private var lastEventAt = 0L

    private val ops = ArrayDeque<GattOp>()
    private var opInFlight = false
    private val opTimeout = Runnable { Log.w(TAG, "GATT op timed out"); opDone() }
    private val reconnect = Runnable { targetAddress?.let { open(it, autoConnect = true) } }

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isActive: Boolean get() = targetAddress != null

    fun connect(address: String) {
        targetAddress = address
        retry = 0
        main.removeCallbacks(reconnect)
        open(address, autoConnect = false)
    }

    fun disconnect() {
        targetAddress = null
        main.removeCallbacks(reconnect)
        closeGatt()
    }

    fun onBluetoothOff() {
        main.removeCallbacks(reconnect)
        closeGatt()
        if (targetAddress != null) AppState.link.value = LinkState.BLUETOOTH_OFF
    }

    fun onBluetoothOn() {
        targetAddress?.let { connect(it) }
    }

    fun identify() {
        val c = gatt?.getService(P2FProtocol.SERVICE)?.getCharacteristic(P2FProtocol.CONTROL) ?: return
        enqueue(GattOp.Write(c, byteArrayOf(P2FProtocol.CMD_IDENTIFY)))
    }

    // ------------------------------------------------------------------ connection

    private fun open(address: String, autoConnect: Boolean) {
        closeGatt()
        val a = adapter
        if (a == null || !a.isEnabled) {
            AppState.link.value = LinkState.BLUETOOTH_OFF
            return
        }
        if (!Permissions.hasBluetooth(context)) return
        val device = try {
            a.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Bad address $address"); return
        }
        AppState.link.value = if (autoConnect) LinkState.SEARCHING else LinkState.CONNECTING
        gatt = device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun scheduleReconnect() {
        if (targetAddress == null) return
        AppState.link.value = LinkState.SEARCHING
        // Back-off only matters if the stack keeps failing immediately (e.g. status 133).
        val delay = (1_000L shl retry.coerceAtMost(5)).coerceAtMost(30_000L)
        retry++
        main.removeCallbacks(reconnect)
        main.postDelayed(reconnect, delay)
    }

    private fun closeGatt() {
        ops.clear()
        opInFlight = false
        main.removeCallbacks(opTimeout)
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }

    private fun handleConnectionState(g: BluetoothGatt, status: Int, newState: Int) {
        if (g != gatt) return
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            AppState.link.value = LinkState.CONNECTING
            // Bonded devices may still be exchanging keys; discovering too early can fail.
            val delay = if (g.device.bondState == BluetoothDevice.BOND_BONDED) 600L else 100L
            main.postDelayed({
                if (g == gatt && !g.discoverServices()) g.disconnect()
            }, delay)
        } else {
            Log.i(TAG, "Disconnected (status=$status, state=$newState)")
            closeGatt()
            scheduleReconnect()
        }
    }

    private fun handleServicesDiscovered(g: BluetoothGatt, status: Int) {
        if (g != gatt) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            g.disconnect(); return
        }
        val button = g.getService(P2FProtocol.SERVICE)?.getCharacteristic(P2FProtocol.BUTTON_EVENT)
        if (button == null) Log.w(TAG, "Push2find service not found on this device")
        button?.let { enqueue(GattOp.Subscribe(it)) }

        g.getService(P2FProtocol.BATTERY_SERVICE)?.getCharacteristic(P2FProtocol.BATTERY_LEVEL)?.let {
            if (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) enqueue(GattOp.Subscribe(it))
            enqueue(GattOp.Read(it))
        }
        retry = 0
        AppState.link.value = LinkState.CONNECTED
    }

    // ------------------------------------------------------------------ data

    private fun handleValue(uuid: java.util.UUID, value: ByteArray) {
        when (uuid) {
            P2FProtocol.BUTTON_EVENT -> handleButton(value)
            P2FProtocol.BATTERY_LEVEL -> if (value.isNotEmpty()) {
                AppState.battery.value = (value[0].toInt() and 0xFF).coerceIn(0, 100)
            }
        }
    }

    private fun handleButton(v: ByteArray) {
        if (v.isEmpty()) return
        val event = v[0].toInt() and 0xFF
        if (event == 0) return
        val now = SystemClock.elapsedRealtime()
        if (v.size >= 2) {
            val seq = v[1].toInt() and 0xFF
            if (seq == lastSeq && now - lastEventAt < P2FProtocol.DUPLICATE_WINDOW_MS) return
            lastSeq = seq
        }
        lastEventAt = now
        onButtonEvent(event)
    }

    // ------------------------------------------------------------------ op queue

    private sealed class GattOp {
        class Subscribe(val c: BluetoothGattCharacteristic) : GattOp()
        class Read(val c: BluetoothGattCharacteristic) : GattOp()
        class Write(val c: BluetoothGattCharacteristic, val value: ByteArray) : GattOp()
    }

    private fun enqueue(op: GattOp) {
        ops.addLast(op)
        if (!opInFlight) next()
    }

    private fun next() {
        val g = gatt ?: return
        val op = ops.removeFirstOrNull() ?: run { opInFlight = false; return }
        opInFlight = true
        val started = when (op) {
            is GattOp.Subscribe -> {
                g.setCharacteristicNotification(op.c, true)
                val d = op.c.getDescriptor(P2FProtocol.CCCD)
                val value = if (op.c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 &&
                    op.c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0
                ) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                d != null && writeDescriptor(g, d, value)
            }
            is GattOp.Read -> g.readCharacteristic(op.c)
            is GattOp.Write -> writeCharacteristic(g, op.c, op.value)
        }
        if (started) main.postDelayed(opTimeout, 4_000) else opDone()
    }

    private fun opDone() {
        main.removeCallbacks(opTimeout)
        opInFlight = false
        next()
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = value
            g.writeDescriptor(d)
        }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, value, type) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = type
            c.value = value
            g.writeCharacteristic(c)
        }
    }

    // ------------------------------------------------------------------ callback (binder thread → main)

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post { handleConnectionState(g, status, newState) }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post { handleServicesDiscovered(g, status) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            main.post { if (g == gatt) opDone() }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post { if (g == gatt) opDone() }
        }

        // Android 13+
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            val uuid = c.uuid
            main.post {
                if (g != gatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) handleValue(uuid, value)
                opDone()
            }
        }

        // Android 12 and lower
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION") val value = c.value?.copyOf() ?: ByteArray(0)
            val uuid = c.uuid
            main.post {
                if (g != gatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) handleValue(uuid, value)
                opDone()
            }
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val uuid = c.uuid
            main.post { if (g == gatt) handleValue(uuid, value) }
        }

        // Android 12 and lower
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION") val value = c.value?.copyOf() ?: return
            val uuid = c.uuid
            main.post { if (g == gatt) handleValue(uuid, value) }
        }
    }

    private companion object {
        const val TAG = "P2F-Link"
    }
}
