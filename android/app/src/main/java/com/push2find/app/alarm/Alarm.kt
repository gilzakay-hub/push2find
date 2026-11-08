package com.push2find.app.alarm

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.push2find.app.R
import com.push2find.app.data.AppState
import com.push2find.app.data.Prefs

data class SoundOption(val key: String, val label: String, val resId: Int)

object Sounds {
    const val DEFAULT_KEY = "classic"

    val bundled = listOf(
        SoundOption("classic", "Classic beep", R.raw.p2f_classic),
        SoundOption("siren", "Siren", R.raw.p2f_siren),
        SoundOption("chime", "Chime", R.raw.p2f_chime),
    )

    fun isBundled(key: String) = bundled.any { it.key == key }

    fun uri(context: Context, key: String): Uri {
        bundled.firstOrNull { it.key == key }?.let { return resUri(context, it.resId) }
        return runCatching { Uri.parse(key) }.getOrNull() ?: defaultUri(context)
    }

    fun defaultUri(context: Context): Uri = resUri(context, bundled.first().resId)

    fun label(context: Context, key: String): String =
        bundled.firstOrNull { it.key == key }?.label
            ?: runCatching { RingtoneManager.getRingtone(context, Uri.parse(key))?.getTitle(context) }.getOrNull()
            ?: "System sound"

    private fun resUri(context: Context, resId: Int) =
        Uri.parse("android.resource://${context.packageName}/$resId")
}

/**
 * Plays the find-my-phone alarm.
 *
 * Why it rings in silent mode: the sound is played on the ALARM stream
 * (USAGE_ALARM). Android's ringer/silent switch only mutes the ring and
 * notification streams; alarms keep playing, and by default are also allowed
 * through Do Not Disturb. We raise the alarm stream to max for the duration of
 * the alarm and restore the user's volume afterwards.
 *
 * Must be called on the main thread.
 */
@SuppressLint("StaticFieldLeak") // holds the application context only
object Alarm {
    private lateinit var app: Context
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    private var player: MediaPlayer? = null
    private var savedAlarmVolume: Int? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** @param fromWristband true → also show the lock-screen alarm UI. */
    fun start(fromWristband: Boolean) {
        if (AppState.ringing.value) stop()
        val prefs = Prefs(app)
        val audio = app.getSystemService(AudioManager::class.java)

        wakeLock = app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "push2find:alarm")
            .apply { acquire(if (prefs.durationSec > 0) prefs.durationSec * 1000L + 5_000 else 10 * 60_000L) }

        savedAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }.onFailure { Log.w(TAG, "Could not raise alarm volume", it) }

        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
            .also { audio.requestAudioFocus(it) }

        player = createPlayer(Sounds.uri(app, prefs.soundKey)) ?: createPlayer(Sounds.defaultUri(app))
        player?.start()

        if (prefs.vibrate) vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0), attrs)
        if (prefs.flash) Torch.start(app)

        AppState.ringing.value = true
        if (prefs.durationSec > 0) handler.postDelayed(autoStop, prefs.durationSec * 1000L)
        if (fromWristband) Notifications.showAlarm(app)
    }

    fun stop() {
        handler.removeCallbacks(autoStop)
        player?.let { p -> runCatching { p.stop() }; p.release() }
        player = null
        runCatching { vibrator().cancel() }
        Torch.stop()

        val audio = app.getSystemService(AudioManager::class.java)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        savedAlarmVolume?.let { v -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedAlarmVolume = null

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        Notifications.cancelAlarm(app)
        AppState.ringing.value = false
    }

    private fun createPlayer(uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(app, uri)
            isLooping = true
            prepare()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Cannot play $uri", e); null
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            app.getSystemService(VibratorManager::class.java).defaultVibrator
        else
            @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)

    private const val TAG = "P2F-Alarm"
}

/** "Stop" action on the alarm notification. */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Alarm.stop()
}
