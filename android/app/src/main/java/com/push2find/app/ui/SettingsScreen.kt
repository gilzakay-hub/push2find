package com.push2find.app.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.push2find.app.alarm.Alarm
import com.push2find.app.alarm.Sounds
import com.push2find.app.alarm.Torch
import com.push2find.app.data.AppState
import com.push2find.app.data.LinkState
import com.push2find.app.data.Prefs
import com.push2find.app.service.FinderService
import com.push2find.app.ui.theme.Connected
import com.push2find.app.ui.theme.Danger
import com.push2find.app.ui.theme.Muted
import com.push2find.app.ui.theme.Signal

private val DURATIONS = listOf(10 to "10 s", 30 to "30 s", 60 to "1 min", 0 to "Until stop")

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onPairNew: () -> Unit,
    onForget: () -> Unit,
    flashAvailable: Boolean = run { val c = LocalContext.current; remember { Torch.isAvailable(c) } },
) {
    val context = LocalContext.current
    var soundKey by remember { mutableStateOf(prefs.soundKey) }
    var duration by remember { mutableIntStateOf(prefs.durationSec) }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
    var flash by remember { mutableStateOf(prefs.flash) }
    var confirmForget by remember { mutableStateOf(false) }
    val ringing by AppState.ringing.collectAsStateWithLifecycle()
    val link by AppState.link.collectAsStateWithLifecycle()
    val battery by AppState.battery.collectAsStateWithLifecycle()

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        if (uri != null) {
            soundKey = uri.toString(); prefs.soundKey = soundKey
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        P2FTopBar("Settings", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            // ---------------------------------------------------------------- sound
            SettingsGroup("Ringtone") {
                Sounds.bundled.forEachIndexed { i, option ->
                    if (i > 0) GroupDivider()
                    RowItem(option.label, onClick = { soundKey = option.key; prefs.soundKey = option.key }) {
                        SelectMark(soundKey == option.key)
                    }
                }
                GroupDivider()
                val custom = !Sounds.isBundled(soundKey)
                RowItem(
                    title = if (custom) Sounds.label(context, soundKey) else "Phone sound…",
                    subtitle = if (custom) "From your phone" else "Pick any sound on this phone",
                    onClick = {
                        systemPicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        )
                    }
                ) { if (custom) SelectMark(true) else Chevron() }
            }
            Spacer(Modifier.height(24.dp))

            // ---------------------------------------------------------------- duration
            Overline("Ring for", Modifier.padding(start = 6.dp, bottom = 8.dp))
            Segmented(DURATIONS, duration, onSelect = { duration = it; prefs.durationSec = it })
            Spacer(Modifier.height(24.dp))

            // ---------------------------------------------------------------- while ringing
            SettingsGroup("While ringing") {
                SwitchRow("Vibrate", vibrate, { vibrate = it; prefs.vibrate = it })
                if (flashAvailable) {
                    GroupDivider()
                    SwitchRow(
                        "Blink flashlight", flash, { flash = it; prefs.flash = it },
                        subtitle = "Helps in the dark or under a pillow"
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (ringing) PrimaryButton("Stop", { Alarm.stop() }, color = Signal)
            else SecondaryButton("Test ringtone", { Alarm.start(fromWristband = false) })
            Spacer(Modifier.height(28.dp))

            // ---------------------------------------------------------------- device
            val connected = link == LinkState.CONNECTED
            SettingsGroup("Wristband") {
                RowItem(prefs.deviceName ?: "—", subtitle = prefs.deviceAddress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (connected) Connected else Muted)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (connected) "Connected" else "Not connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                GroupDivider()
                RowItem("Battery") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(battery?.let { "$it%" } ?: "—", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(10.dp))
                        BatteryGlyph(battery, if ((battery ?: 100) <= 15) Danger else Connected)
                    }
                }
                GroupDivider()
                RowItem(
                    "Identify",
                    subtitle = if (connected) "Make the band blink" else "Available when connected",
                    titleColor = if (connected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = if (connected) ({ FinderService.start(context, FinderService.ACTION_IDENTIFY) }) else null,
                )
                GroupDivider()
                RowItem("Pair another wristband", onClick = onPairNew) { Chevron() }
                GroupDivider()
                RowItem("Forget this wristband", titleColor = Danger, onClick = { confirmForget = true })
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this wristband?") },
            text = { Text("The phone will stop listening to it until you pair again.") },
            confirmButton = {
                TextButton(onClick = { confirmForget = false; onForget() }) { Text("Forget", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } }
        )
    }
}
