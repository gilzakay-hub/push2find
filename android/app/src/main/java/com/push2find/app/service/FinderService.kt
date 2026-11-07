package com.push2find.app.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.push2find.app.alarm.Alarm
import com.push2find.app.alarm.Notifications
import com.push2find.app.ble.WristbandLink
import com.push2find.app.data.AppState
import com.push2find.app.data.LinkState
import com.push2find.app.data.Permissions
import com.push2find.app.data.Prefs
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground service (type connectedDevice) that owns the BLE link.
 * It keeps the process alive with the screen locked and the app closed, so a
 * button press is handled within milliseconds of arriving.
 */
class FinderService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.push2find.action.START"
        const val ACTION_RECONNECT = "com.push2find.action.RECONNECT"
        const val ACTION_IDENTIFY = "com.push2find.action.IDENTIFY"

        fun start(context: Context, action: String = ACTION_START) {
            if (!Permissions.hasBluetooth(context) || Prefs(context).deviceAddress == null) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, FinderService::class.java).setAction(action))
            }.onFailure { Log.w(TAG, "Could not start service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FinderService::class.java))
        }

        private const val TAG = "P2F-Service"
    }

    private lateinit var prefs: Prefs
    private lateinit var link: WristbandLink

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> link.onBluetoothOn()
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> link.onBluetoothOff()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        link = WristbandLink(this, ::onButtonEvent)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, Notifications.ID_STATUS, Notifications.status(this, "Starting…"), type)
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return
        }

        ContextCompat.registerReceiver(
            this, bluetoothReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        lifecycleScope.launch {
            combine(AppState.link, AppState.battery) { state, battery -> statusText(state, battery) }
                .distinctUntilChanged()
                .collect { Notifications.updateStatus(this@FinderService, it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val address = prefs.deviceAddress
        if (address == null || !Permissions.hasBluetooth(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_RECONNECT -> link.connect(address)
            ACTION_IDENTIFY -> link.identify()
            else -> if (!link.isActive) link.connect(address) // includes a null intent after a system restart
        }
        return START_STICKY
    }

    private fun onButtonEvent(event: Int) {
        AppState.lastPressAt.value = System.currentTimeMillis()
        // Any press toggles: silent → ring, ringing → stop.
        if (AppState.ringing.value) Alarm.stop() else Alarm.start(fromWristband = true)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(bluetoothReceiver) }
        if (::link.isInitialized) link.disconnect()
        AppState.link.value = if (prefs.deviceAddress == null) LinkState.NOT_PAIRED else LinkState.SEARCHING
        super.onDestroy()
    }

    private fun statusText(state: LinkState, battery: Int?): String = when (state) {
        LinkState.CONNECTED -> "Wristband connected" + (battery?.let { " · battery $it%" } ?: "")
        LinkState.CONNECTING -> "Connecting to wristband…"
        LinkState.SEARCHING -> "Waiting for wristband to come in range"
        LinkState.BLUETOOTH_OFF -> "Bluetooth is off"
        LinkState.NOT_PAIRED -> "No wristband paired"
    }
}

/** Restarts the service after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            FinderService.start(context)
        }
    }
}
