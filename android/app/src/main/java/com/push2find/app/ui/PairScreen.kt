package com.push2find.app.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.push2find.app.ble.BleScanner
import com.push2find.app.ble.FoundDevice
import com.push2find.app.ui.theme.Connected
import com.push2find.app.ui.theme.Signal

@Composable
fun PairScreen(onBack: (() -> Unit)?, onPaired: (address: String, name: String) -> Unit) {
    val context = LocalContext.current
    val scanner = remember { BleScanner(context) }
    val devices = remember { mutableStateMapOf<String, FoundDevice>() }
    var showAll by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val btOn = rememberBluetoothEnabled()

    DisposableEffect(showAll, btOn) {
        devices.clear()
        error = null
        if (btOn) {
            val ok = scanner.start(showAll, onFound = { devices[it.address] = it }, onError = { error = "Scan failed (code $it). Try again." })
            if (!ok) error = "Couldn't start scanning."
        }
        onDispose { scanner.stop() }
    }

    PairContent(
        devices = devices.values.toList(),
        bluetoothOn = btOn,
        error = error,
        showAll = showAll,
        onShowAllChange = { showAll = it },
        onTurnOnBluetooth = { context.tryStart(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        onBack = onBack,
        onPick = { d ->
            scanner.stop()
            onPaired(d.address, d.name ?: "Push2find wristband")
        }
    )
}

@Composable
fun PairContent(
    devices: List<FoundDevice>,
    bluetoothOn: Boolean,
    error: String?,
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit,
    onTurnOnBluetooth: () -> Unit,
    onBack: (() -> Unit)?,
    onPick: (FoundDevice) -> Unit,
) {
    val sorted = devices.sortedWith(compareByDescending<FoundDevice> { it.isPush2find }.thenByDescending { it.rssi })

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        P2FTopBar(if (onBack == null) "" else "Pair wristband", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) { HoldIllustration() }
            Text("Pair your wristband", style = MaterialTheme.typography.displaySmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.15f))
            Spacer(Modifier.height(18.dp))
            Step(1, "Hold the button for 3 seconds", "until the light on the band blinks.")
            Spacer(Modifier.height(12.dp))
            Step(2, "Tap your wristband below", "It's usually the one at the top.")
            Spacer(Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)) {
                Overline("Nearby", Modifier.weight(1f))
                if (bluetoothOn) ScanningDots()
            }
            P2FCard(padding = 0.dp) {
                when {
                    !bluetoothOn -> RowItem(
                        "Bluetooth is off", subtitle = "Turn it on to look for your wristband",
                        onClick = onTurnOnBluetooth, trailing = { Chevron() }
                    )
                    sorted.isEmpty() -> RowItem(
                        "Looking for wristbands…",
                        subtitle = error ?: "Keep the band close to the phone",
                        titleColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> sorted.forEachIndexed { i, d ->
                        if (i > 0) GroupDivider(inset = 68.dp)
                        RowItem(
                            title = d.name ?: "Unnamed device",
                            subtitle = if (d.isPush2find) "Push2find wristband" else d.address,
                            onClick = { onPick(d) },
                            leading = { DeviceMark(d.isPush2find) },
                            trailing = { SignalBars(d.rssi) }
                        )
                    }
                }
            }
            if (error != null && sorted.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            }
            Spacer(Modifier.height(20.dp))
            P2FCard(padding = 0.dp) {
                SwitchRow("Show all Bluetooth devices", showAll, onShowAllChange, subtitle = "For testing with a prototype")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) { Text("$n", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A wristband seen from above, its button pulsing as if held down. */
@Composable
private fun HoldIllustration() {
    val t = rememberInfiniteTransition(label = "hold")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val strap = MaterialTheme.colorScheme.onSurface
    val face = MaterialTheme.colorScheme.surface
    Canvas(Modifier.size(width = 260.dp, height = 150.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val strapH = size.height * 0.34f
        drawRoundRect(strap, Offset(0f, c.y - strapH / 2), Size(size.width, strapH), CornerRadius(strapH / 2))
        val faceR = size.height * 0.40f
        drawCircle(strap, faceR + 6.dp.toPx(), c)
        drawCircle(face, faceR, c)
        val btn = faceR * 0.46f
        // ripple
        drawCircle(Signal.copy(alpha = 0.35f * (1f - p)), btn + faceR * 0.5f * p, c, style = Stroke(3.dp.toPx()))
        drawCircle(Signal, btn, c)
    }
}

@Composable
private fun ScanningDots() {
    val t = rememberInfiniteTransition(label = "scan")
    val p by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 6.dp)) {
        for (i in 0..2) {
            val on = p.toInt() == i
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(if (on) Signal else Signal.copy(alpha = 0.25f)))
        }
    }
}

@Composable
private fun DeviceMark(isPush2find: Boolean) {
    val line = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(36.dp)) {
        drawCircle(if (isPush2find) Signal.copy(alpha = 0.14f) else line.copy(alpha = 0.5f))
        drawCircle(if (isPush2find) Signal else Color.Gray, radius = size.minDimension * 0.16f)
    }
}

/** Four bars: helps the user pick the band that is right next to them. */
@Composable
fun SignalBars(rssi: Int) {
    val bars = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    val off = MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..4) {
            Box(
                Modifier.width(5.dp).height((6 + i * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= bars) Connected else off)
            )
        }
    }
}
