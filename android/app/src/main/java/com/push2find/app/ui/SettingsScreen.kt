package com.push2find.app.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.push2find.app.alarm.Alarm
import com.push2find.app.alarm.Sounds
import com.push2find.app.data.AppState
import com.push2find.app.data.LinkState
import com.push2find.app.data.Prefs
import com.push2find.app.service.FinderService
import com.push2find.app.ui.theme.Signal

private val DURATIONS = listOf(10 to "10 s", 30 to "30 s", 60 to "1 min", 0 to "Until stopped")

@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit, onPairNew: () -> Unit, onForget: () -> Unit) {
    val context = LocalContext.current
    var soundKey by remember { mutableStateOf(prefs.soundKey) }
    var duration by remember { mutableIntStateOf(prefs.durationSec) }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { P2FTopBar("Settings", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------------------------------------------------------------- sound
            SectionCard {
                SectionTitle("Ringtone")
                Sounds.bundled.forEach { option ->
                    ChoiceRow(option.label, selected = soundKey == option.key) {
                        soundKey = option.key; prefs.soundKey = option.key
                    }
                }
                val customSelected = !Sounds.isBundled(soundKey)
                ChoiceRow(
                    if (customSelected) "Phone sound: ${Sounds.label(context, soundKey)}" else "Choose a phone sound…",
                    selected = customSelected
                ) {
                    systemPicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    )
                }
            }

            // ---------------------------------------------------------------- duration
            SectionCard {
                SectionTitle("Ring for")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DURATIONS.forEach { (sec, label) ->
                        FilterChip(
                            selected = duration == sec,
                            onClick = { duration = sec; prefs.durationSec = sec },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate too", Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it; prefs.vibrate = it })
                }
            }

            // ---------------------------------------------------------------- test
            if (ringing) {
                Button(
                    onClick = { Alarm.stop() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = Color.White)
                ) { Text("Stop", fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(onClick = { Alarm.start(fromWristband = false) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Test ringtone")
                }
            }

            // ---------------------------------------------------------------- device
            SectionCard {
                SectionTitle("Wristband")
                InfoRow("Name", prefs.deviceName ?: "—")
                InfoRow("Address", prefs.deviceAddress ?: "—")
                InfoRow("Status", if (link == LinkState.CONNECTED) "Connected" else "Not connected")
                InfoRow("Battery", battery?.let { "$it%" } ?: "—")
                Row {
                    TextButton(
                        onClick = { FinderService.start(context, FinderService.ACTION_IDENTIFY) },
                        enabled = link == LinkState.CONNECTED
                    ) { Text("Identify (blink)") }
                    TextButton(onClick = onPairNew) { Text("Pair another") }
                }
                TextButton(onClick = onForget) { Text("Forget this wristband", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
