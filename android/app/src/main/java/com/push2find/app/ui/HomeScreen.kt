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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.push2find.app.ui.theme.Muted
import com.push2find.app.ui.theme.Searching
import com.push2find.app.ui.theme.Signal
import java.util.Date

@Composable
fun HomeScreen(prefs: Prefs, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val link by AppState.link.collectAsStateWithLifecycle()
    val battery by AppState.battery.collectAsStateWithLifecycle()
    val ringing by AppState.ringing.collectAsStateWithLifecycle()
    val lastPress by AppState.lastPressAt.collectAsStateWithLifecycle()
    val btOn = rememberBluetoothEnabled()
    val state = if (!btOn) LinkState.BLUETOOTH_OFF else link

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            P2FTopBar("Push2find", actions = {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            })
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            StatusHero(state, ringing)
            Spacer(Modifier.height(12.dp))
            Text(prefs.deviceName ?: "Wristband", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            BatteryCard(battery, connected = state == LinkState.CONNECTED)
            Spacer(Modifier.height(16.dp))

            if (ringing) {
                Button(
                    onClick = { Alarm.stop() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = Color.White)
                ) { Text("Stop ringing", fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(
                    onClick = { Alarm.start(fromWristband = false) },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Test ring") }
            }

            lastPress?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Last button press: ${DateFormat.getTimeFormat(context).format(Date(it))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
            SetupWarnings(btOn)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun subtitle(state: LinkState) = when (state) {
    LinkState.CONNECTED -> "Press the wristband button to ring this phone"
    LinkState.CONNECTING -> "Connecting…"
    LinkState.SEARCHING -> "Out of range – will reconnect automatically"
    LinkState.BLUETOOTH_OFF -> "Turn on Bluetooth to connect"
    LinkState.NOT_PAIRED -> "No wristband paired"
}

@Composable
private fun StatusHero(state: LinkState, ringing: Boolean) {
    val (color, label) = when {
        ringing -> Signal to "Ringing"
        state == LinkState.CONNECTED -> Connected to "Connected"
        state == LinkState.CONNECTING -> Searching to "Connecting"
        state == LinkState.SEARCHING -> Searching to "Searching"
        state == LinkState.BLUETOOTH_OFF -> Muted to "Bluetooth off"
        else -> Muted to "Not paired"
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, 1.22f, infiniteRepeatable(tween(if (ringing) 700 else 1600), RepeatMode.Restart), label = "scale")
    val ringAlpha by pulse.animateFloat(0.35f, 0f, infiniteRepeatable(tween(if (ringing) 700 else 1600), RepeatMode.Restart), label = "alpha")
    val animate = ringing || state == LinkState.CONNECTED || state == LinkState.SEARCHING

    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        if (animate) {
            Box(
                Modifier.size(176.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = ringAlpha }
                    .background(color, CircleShape)
            )
        }
        Box(
            Modifier.size(176.dp)
                .background(color.copy(alpha = 0.12f), CircleShape)
                .border(3.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(22.dp).background(color, CircleShape))
                Spacer(Modifier.height(10.dp))
                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BatteryCard(battery: Int?, connected: Boolean) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Wristband battery", style = MaterialTheme.typography.bodyLarge)
            Text(
                battery?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))
        val level = (battery ?: 0) / 100f
        LinearProgressIndicator(
            progress = { level },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = when {
                battery == null -> Muted
                battery <= 15 -> MaterialTheme.colorScheme.error
                else -> Connected
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        if (!connected && battery != null) {
            Spacer(Modifier.height(6.dp))
            Text("Last known level", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Things that would silently break background operation, each with a one-tap fix. */
@SuppressLint("BatteryLife")
@Composable
private fun SetupWarnings(btOn: Boolean) {
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!btOn) WarningCard(
            "Bluetooth is off", "The wristband can't reach this phone while Bluetooth is off.", "Turn on"
        ) { context.tryStart(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }

        if (!batteryOk) WarningCard(
            "Allow background use",
            "Without this, some phones stop Push2find in the background and the wristband won't ring the phone.",
            "Allow"
        ) { context.tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))) }

        if (!notificationsOk) WarningCard(
            "Notifications are off",
            "The phone will still ring, but you won't get a Stop button on the lock screen.",
            "Settings"
        ) {
            context.tryStart(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        }

        if (!fullScreenOk) WarningCard(
            "Allow full-screen alerts",
            "Shows a large Stop button over the lock screen while the phone is ringing.",
            "Allow"
        ) { context.tryStart(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg"))) }
    }
}
