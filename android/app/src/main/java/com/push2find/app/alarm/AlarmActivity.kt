package com.push2find.app.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.push2find.app.data.AppState
import com.push2find.app.ui.theme.Push2FindTheme
import com.push2find.app.ui.theme.Signal
import com.push2find.app.ui.theme.SignalDeep

/** Full-screen "Stop" UI shown over the lock screen while the phone rings. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            Push2FindTheme {
                val ringing by AppState.ringing.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) { if (!ringing) finish() }
                AlarmScreen(onStop = { Alarm.stop() })
            }
        }
    }
}

@Composable
fun AlarmScreen(onStop: () -> Unit) {
    val t = rememberInfiniteTransition(label = "alarm")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "p")

    Column(
        Modifier.fillMaxSize().background(Signal).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("FOUND IT?", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp))
        Spacer(Modifier.height(10.dp))
        Text(
            "Your device is\nlooking for this phone", color = Color.White, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, lineHeight = 36.sp)
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(320.dp)) {
                val base = size.minDimension * 0.31f
                for (k in 0..1) {
                    val q = (p + k * 0.5f) % 1f
                    drawCircle(
                        Color.White.copy(alpha = 0.45f * (1f - q)),
                        radius = base + size.minDimension * 0.19f * q,
                        style = Stroke(3.dp.toPx())
                    )
                }
            }
            Box(
                Modifier.size(200.dp).clip(CircleShape).background(Color.White).clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Text("STOP", color = SignalDeep, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        }
        Text(
            "or press the device button again",
            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
    }
}
