package com.push2find.app.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.push2find.app.ui.theme.Signal
import kotlin.math.abs

// ====================================================================== layout pieces

/** Flat top bar: back arrow (optional), title, trailing actions. */
@Composable
fun P2FTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
        actions()
    }
}

/** "push2find" wordmark with the orange button dot. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(Signal, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("push2find", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** Small uppercase label above a value or a group. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun P2FCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** A group in Settings: overline + card with rows. */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Overline(title, Modifier.padding(start = 6.dp, bottom = 8.dp))
        P2FCard(padding = 0.dp, content = content)
    }
}

@Composable
fun GroupDivider(inset: Dp = 18.dp) = HorizontalDivider(Modifier.padding(start = inset), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun RowItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    RowItem(title, subtitle = subtitle, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Signal, checkedThumbColor = Color.White)
        )
    }
}

/** Radio-style mark used in lists: ring, filled with an orange dot when selected. */
@Composable
fun SelectMark(selected: Boolean) {
    val line = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(22.dp)) {
        if (selected) {
            drawCircle(Signal, radius = size.minDimension / 2)
            drawCircle(Color.White, radius = size.minDimension / 5)
        } else {
            drawCircle(line, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
fun Chevron() = Icon(
    Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant
)

/** Segmented control: equal-width options in a track. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier.weight(1f).height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ====================================================================== buttons

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (color == Signal) Color.White else MaterialTheme.colorScheme.onPrimary
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

// ====================================================================== graphics

/**
 * The app's symbol – a button dot with sound waves on both sides – used as the
 * live status graphic. When [animated], the waves light up in sequence, outward.
 */
@Composable
fun Beacon(
    color: Color,
    animated: Boolean,
    modifier: Modifier = Modifier,
    periodMs: Int = 1600,
    glow: Boolean = false,
) {
    val t = rememberInfiniteTransition(label = "beacon")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val unit = size.minDimension / 2
        if (glow) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent), center = c, radius = unit * 1.05f),
                radius = unit * 1.05f, center = c
            )
        }
        val dot = unit * 0.22f
        val stroke = unit * 0.075f
        for (i in 1..3) {
            val r = dot + unit * 0.24f * i
            val strength = if (animated) {
                // a bright band travels outward through arcs 1..3
                (1f - abs(phase * 4f - i) ).coerceIn(0f, 1f)
            } else 0f
            val col = if (animated) color.copy(alpha = 0.18f + 0.82f * strength) else idle
            val tl = Offset(c.x - r, c.y - r)
            val sz = Size(r * 2, r * 2)
            drawArc(col, 180f - 38f, 76f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(col, -38f, 76f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        drawCircle(color, radius = dot, center = c)
    }
}

/** Small battery outline filled to [level]. */
@Composable
fun BatteryGlyph(level: Int?, color: Color, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(width = 30.dp, height = 15.dp)) {
        val nub = 3.dp.toPx()
        val sw = 1.6.dp.toPx()
        val body = Size(size.width - nub - sw, size.height - sw)
        drawRoundRect(line, Offset(sw / 2, sw / 2), body, CornerRadius(4.dp.toPx()), style = Stroke(sw))
        drawRoundRect(
            line, Offset(size.width - nub, size.height * 0.32f), Size(nub, size.height * 0.36f), CornerRadius(1.5.dp.toPx())
        )
        if (level != null) {
            val inset = 3.dp.toPx()
            val w = (body.width - inset * 2 + sw) * (level.coerceIn(0, 100) / 100f)
            drawRoundRect(
                color, Offset(inset, inset), Size(w, size.height - inset * 2), CornerRadius(2.dp.toPx())
            )
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) = Box(modifier.size(8.dp).background(color, CircleShape))

/** Round tinted container for a row icon. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ====================================================================== system helpers

/** Increments every time the screen resumes – use as a key to re-check system settings. */
@Composable
fun rememberResumeCount(): Int {
    val owner = LocalLifecycleOwner.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) count++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}

@Composable
fun rememberBluetoothEnabled(): Boolean {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    DisposableEffect(adapter) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                enabled = adapter?.isEnabled == true
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}

fun Context.tryStart(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
