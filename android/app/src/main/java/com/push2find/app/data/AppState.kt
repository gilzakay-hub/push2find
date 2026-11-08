package com.push2find.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.push2find.app.alarm.Sounds
import kotlinx.coroutines.flow.MutableStateFlow

enum class LinkState { NOT_PAIRED, BLUETOOTH_OFF, SEARCHING, CONNECTING, CONNECTED }

/** Live, in-memory state shared by the service and the UI. */
object AppState {
    val link = MutableStateFlow(LinkState.NOT_PAIRED)
    val battery = MutableStateFlow<Int?>(null)
    val ringing = MutableStateFlow(false)
    val lastPressAt = MutableStateFlow<Long?>(null)

    fun reset() {
        link.value = LinkState.NOT_PAIRED
        battery.value = null
        lastPressAt.value = null
    }
}

/** Persistent settings. No accounts, no cloud – everything stays on the phone. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("push2find", Context.MODE_PRIVATE)

    var deviceAddress: String?
        get() = sp.getString(K_ADDRESS, null)
        set(v) = sp.edit { putString(K_ADDRESS, v) }

    var deviceName: String?
        get() = sp.getString(K_NAME, null)
        set(v) = sp.edit { putString(K_NAME, v) }

    /** A bundled sound key ("classic", "siren", "chime") or a system ringtone URI. */
    var soundKey: String
        get() = sp.getString(K_SOUND, Sounds.DEFAULT_KEY) ?: Sounds.DEFAULT_KEY
        set(v) = sp.edit { putString(K_SOUND, v) }

    /** Ring duration in seconds. 0 = ring until stopped. */
    var durationSec: Int
        get() = sp.getInt(K_DURATION, 30)
        set(v) = sp.edit { putInt(K_DURATION, v) }

    var vibrate: Boolean
        get() = sp.getBoolean(K_VIBRATE, true)
        set(v) = sp.edit { putBoolean(K_VIBRATE, v) }

    /** Blink the camera flash while ringing. */
    var flash: Boolean
        get() = sp.getBoolean(K_FLASH, true)
        set(v) = sp.edit { putBoolean(K_FLASH, v) }

    fun clearDevice() = sp.edit {
        remove(K_ADDRESS)
        remove(K_NAME)
    }

    private companion object {
        const val K_ADDRESS = "device_address"
        const val K_NAME = "device_name"
        const val K_SOUND = "sound"
        const val K_DURATION = "duration_sec"
        const val K_VIBRATE = "vibrate"
        const val K_FLASH = "flash"
    }
}

object Permissions {
    fun bluetooth(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun allRuntime(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            bluetooth() + Manifest.permission.POST_NOTIFICATIONS
        else bluetooth()

    fun hasBluetooth(context: Context) = bluetooth().all { granted(context, it) }

    fun hasNotifications(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(context, Manifest.permission.POST_NOTIFICATIONS)

    private fun granted(context: Context, p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}
