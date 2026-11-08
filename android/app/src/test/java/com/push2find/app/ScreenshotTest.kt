package com.push2find.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.push2find.app.alarm.AlarmScreen
import com.push2find.app.ble.FoundDevice
import com.push2find.app.data.AppState
import com.push2find.app.data.LinkState
import com.push2find.app.data.Prefs
import com.push2find.app.ui.HomeContent
import com.push2find.app.ui.PairContent
import com.push2find.app.ui.PermissionScreen
import com.push2find.app.ui.SettingsScreen
import com.push2find.app.ui.SetupWarning
import com.push2find.app.ui.theme.Push2FindTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each screen as a PNG under build/screenshots (run: gradle recordRoborazziDebug). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w400dp-h880dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, frameMs: Long = 450, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Push2FindTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(frameMs)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private val noop = {}

    @Test fun home_connected() = shot("1_home_connected") {
        HomeContent("P2F-SIM", LinkState.CONNECTED, false, 87, "21:04", emptyList(), noop, noop, noop)
    }

    @Test fun home_connected_dark() = shot("2_home_connected_dark", dark = true) {
        HomeContent("P2F-SIM", LinkState.CONNECTED, false, 87, "21:04", emptyList(), noop, noop, noop)
    }

    @Test fun home_ringing() = shot("3_home_ringing", frameMs = 300) {
        HomeContent("P2F-SIM", LinkState.CONNECTED, true, 87, "21:06", emptyList(), noop, noop, noop)
    }

    @Test fun home_searching() = shot("4_home_searching") {
        HomeContent(
            "P2F-SIM", LinkState.SEARCHING, false, 12, null,
            listOf(
                SetupWarning("Allow background use", "Otherwise the phone may stop Push2find while locked.", noop),
                SetupWarning("Turn on notifications", "Needed for the Stop button on the lock screen.", noop),
            ),
            noop, noop, noop
        )
    }

    @Test fun pair() = shot("5_pair") {
        PairContent(
            devices = listOf(
                FoundDevice("C4:1A:22:9B:10:3E", "P2F-SIM", -48, true),
                FoundDevice("5D:77:01:AA:42:0C", "JBL Flip 6", -71, false),
            ),
            bluetoothOn = true, error = null, showAll = true,
            onShowAllChange = {}, onTurnOnBluetooth = noop, onBack = noop, onPick = {}
        )
    }

    @Test fun settings() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs(ctx).apply { deviceName = "P2F-SIM"; deviceAddress = "C4:1A:22:9B:10:3E" }
        AppState.link.value = LinkState.CONNECTED
        AppState.battery.value = 87
        shot("6_settings") { SettingsScreen(Prefs(ctx), noop, noop, noop, flashAvailable = true) }
    }

    @Test fun alarm() = shot("7_alarm", frameMs = 300) { AlarmScreen(onStop = noop) }

    @Test fun permission() = shot("8_welcome") { PermissionScreen(noop, noop) }
}
