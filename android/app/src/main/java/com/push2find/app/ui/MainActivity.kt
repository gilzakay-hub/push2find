package com.push2find.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.push2find.app.data.AppState
import com.push2find.app.data.Permissions
import com.push2find.app.data.Prefs
import com.push2find.app.service.FinderService
import com.push2find.app.ui.theme.Push2FindTheme
import com.push2find.app.ui.theme.Signal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Push2FindTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Safe place to (re)start the foreground service: the app is visible.
        FinderService.start(this)
    }
}

private enum class Screen { Home, Pair, Settings }

@Composable
private fun App() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var paired by remember { mutableStateOf(prefs.deviceAddress != null) }
    var screen by rememberSaveable { mutableStateOf(if (paired) Screen.Home else Screen.Pair) }

    var hasBt by remember { mutableStateOf(Permissions.hasBluetooth(context)) }
    val resume = rememberResumeCount()
    LaunchedEffect(resume) { hasBt = Permissions.hasBluetooth(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasBt = Permissions.hasBluetooth(context)
        if (hasBt) FinderService.start(context)
    }

    BackHandler(enabled = screen == Screen.Settings || (screen == Screen.Pair && paired)) { screen = Screen.Home }

    when {
        !hasBt -> PermissionScreen(
            onRequest = { permissionLauncher.launch(Permissions.allRuntime()) },
            onOpenSettings = {
                context.tryStart(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
        )

        screen == Screen.Pair -> PairScreen(
            onBack = if (paired) ({ screen = Screen.Home }) else null,
            onPaired = { address, name ->
                FinderService.stop(context)
                AppState.reset()
                prefs.deviceAddress = address
                prefs.deviceName = name
                paired = true
                FinderService.start(context, FinderService.ACTION_RECONNECT)
                screen = Screen.Home
            }
        )

        screen == Screen.Settings -> SettingsScreen(
            prefs = prefs,
            onBack = { screen = Screen.Home },
            onPairNew = { screen = Screen.Pair },
            onForget = {
                FinderService.stop(context)
                prefs.clearDevice()
                AppState.reset()
                paired = false
                screen = Screen.Pair
            }
        )

        else -> HomeScreen(prefs = prefs, onOpenSettings = { screen = Screen.Settings })
    }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark()
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Beacon(color = Signal, animated = true, modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f))
        }
        Text("Never lose\nyour phone again", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(14.dp))
        Text(
            "Push2find listens for your wristband over Bluetooth (“Nearby devices”). " +
                "Notifications put a Stop button on your lock screen.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Allow access", onRequest)
        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Open app settings", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
