package com.push2find.app.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.push2find.app.alarm.Alarm
import com.push2find.app.data.AppState
import com.push2find.app.data.LinkState
import com.push2find.app.data.Permissions
import com.push2find.app.data.Prefs
import com.push2find.app.ui.theme.Connected
import com.push2find.app.ui.theme.Danger
import com.push2find.app.ui.theme.Muted
import com.push2find.app.ui.theme.Searching
import com.push2find.app.ui.theme.Signal
import java.util.Date

/** Something that would silently break background operation, with a one-tap fix. */
data class SetupWarning(val title: String, val body: String, val onFix: () -> Unit)

@Composable
fun HomeScreen(prefs: Prefs, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val link by AppState.link.collectAsStateWithLifecycle()
    val battery by AppState.battery.collectAsStateWithLifecycle()
    val ringing by AppState.ringing.collectAsStateWithLifecycle()
    val lastPress by AppState.lastPressAt.collectAsStateWithLifecycle()
    val btOn = rememberBluetoothEnabled()

    HomeContent(
        deviceName = prefs.deviceName ?: "Wristband",
        state = if (!btOn) LinkState.BLUETOOTH_OFF else link,
        ringing = ringing,
        battery = battery,
        lastPress = lastPress?.let { DateFormat.getTimeFormat(context).format(Date(it)) },
        warnings = rememberSetupWarnings(btOn),
        onTestRing = { Alarm.start(fromWristband = false) },
        onStop = { Alarm.stop() },
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun HomeContent(
    deviceName: String,
    state: LinkState,
    ringing: Boolean,
    battery: Int?,
    lastPress: String?,
    warnings: List<SetupWarning>,
    onTestRing: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val look = statusLook(state, ringing)

    Column(
        Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(Modifier.weight(1f).padding(start = 4.dp))
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }

        // ---- beacon + status
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
            Beacon(
                color = look.color,
                animated = look.animated,
                periodMs = if (ringing) 700 else 1800,
                glow = ringing,
                modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
            )
        }
        Overline("Wristband · $deviceName", Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(6.dp))
        Text(look.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 2.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            look.subtitle, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(24.dp))

        // ---- tiles
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            P2FCard(Modifier.weight(1f)) {
                Overline("Battery")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(battery?.let { "$it%" } ?: "—", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    BatteryGlyph(battery, color = when {
                        battery == null -> Muted
                        battery <= 15 -> Danger
                        else -> Connected
                    })
                }
            }
            P2FCard(Modifier.weight(1f)) {
                Overline("Last press")
                Spacer(Modifier.height(10.dp))
                Text(lastPress ?: "—", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(20.dp))

        // ---- action
        if (ringing) PrimaryButton("Stop ringing", onStop, color = Signal)
        else SecondaryButton("Test ring", onTestRing)

        // ---- setup warnings
        if (warnings.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Overline("Needs attention", Modifier.padding(start = 6.dp, bottom = 8.dp))
            P2FCard(padding = 0.dp) {
                warnings.forEachIndexed { i, w ->
                    if (i > 0) GroupDivider()
                    RowItem(
                        title = w.title, subtitle = w.body, onClick = w.onFix,
                        leading = { IconBadge(Icons.Filled.Warning, Searching) },
                        trailing = { Chevron() }
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private data class StatusLook(val title: String, val subtitle: String, val color: Color, val animated: Boolean)

private fun statusLook(state: LinkState, ringing: Boolean): StatusLook = when {
    ringing -> StatusLook("Ringing", "Press the wristband again or tap Stop", Signal, true)
    state == LinkState.CONNECTED -> StatusLook("Connected", "Press the wristband button to ring this phone", Connected, true)
    state == LinkState.CONNECTING -> StatusLook("Connecting", "Talking to your wristband…", Searching, true)
    state == LinkState.SEARCHING -> StatusLook("Searching", "Out of range – reconnects automatically", Searching, true)
    state == LinkState.BLUETOOTH_OFF -> StatusLook("Bluetooth off", "Turn on Bluetooth to connect", Muted, false)
    else -> StatusLook("Not paired", "Pair a wristband to get started", Muted, false)
}

@SuppressLint("BatteryLife")
@Composable
private fun rememberSetupWarnings(btOn: Boolean): List<SetupWarning> {
    val context = LocalContext.current
    val resume = rememberResumeCount()
    val pkg = context.packageName

    val notificationsOk = remember(resume) { Permissions.hasNotifications(context) }
    val batteryOk = remember(resume) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)
    }
    val fullScreenOk = remember(resume) {
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    return buildList {
        if (!btOn) add(SetupWarning("Turn on Bluetooth", "The wristband can't reach this phone while it's off.") {
            context.tryStart(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        })
        if (!batteryOk) add(SetupWarning("Allow background use", "Otherwise the phone may stop Push2find while locked.") {
            context.tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
        })
        if (!notificationsOk) add(SetupWarning("Turn on notifications", "Needed for the Stop button on the lock screen.") {
            context.tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        })
        if (!fullScreenOk) add(SetupWarning("Allow full-screen alerts", "Shows a big Stop button over the lock screen.") {
            context.tryStart(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
        })
    }
}
