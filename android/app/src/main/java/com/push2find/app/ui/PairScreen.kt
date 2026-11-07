package com.push2find.app.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.push2find.app.ble.BleScanner
import com.push2find.app.ble.FoundDevice
import com.push2find.app.ui.theme.Connected
import com.push2find.app.ui.theme.Muted
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { P2FTopBar("Pair wristband", onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp)) {
            Text(
                "Hold the wristband button for 3 seconds until the light blinks, then tap it in the list.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            if (!btOn) {
                Button(
                    onClick = { context.tryStart(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Turn on Bluetooth") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Signal)
                    Spacer(Modifier.width(10.dp))
                    Text("Looking for wristbands nearby…", style = MaterialTheme.typography.titleSmall)
                }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))

            val sorted = devices.values.sortedWith(compareByDescending<FoundDevice> { it.isPush2find }.thenByDescending { it.rssi })
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sorted, key = { it.address }) { d ->
                    DeviceRow(d) {
                        scanner.stop()
                        onPaired(d.address, d.name ?: "Push2find wristband")
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show all Bluetooth devices", style = MaterialTheme.typography.bodyMedium)
                    Text("For testing with a prototype", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = showAll, onCheckedChange = { showAll = it })
            }
        }
    }
}

@Composable
private fun DeviceRow(d: FoundDevice, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.name ?: "Unnamed device", fontWeight = FontWeight.SemiBold)
                Text(d.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SignalBars(d.rssi)
        }
    }
}

/** Four bars: helps the user pick the band that is right next to them. */
@Composable
private fun SignalBars(rssi: Int) {
    val bars = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..4) {
            Spacer(
                Modifier.width(5.dp).height((6 + i * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= bars) Connected else Muted.copy(alpha = 0.3f))
            )
        }
    }
}
