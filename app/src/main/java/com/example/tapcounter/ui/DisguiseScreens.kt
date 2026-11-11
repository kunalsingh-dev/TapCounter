package com.example.tapcounter.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Names of the stealth-screen looks (index is stored). 0 = plain black. */
val DisguiseLabels = listOf("Black", "Alarm", "Clock", "Update", "Charge")

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val Dim = Color(0xFF8A8A8A)
private val Soft = Color(0xFFC8C8C8)

/**
 * What the stealth screen shows. Everything here is only drawn: nothing can be tapped, no
 * element handles touches, so every tap on it goes to the counter underneath and counts.
 */
@Composable
fun DisguiseScreen(index: Int) {
    when (index) {
        1 -> AlarmDisguise()
        2 -> ClockDisguise()
        3 -> UpdateDisguise()
        4 -> ChargingDisguise()
        else -> Unit // black
    }
}

@Composable
private fun rememberNow(): State<LocalDateTime> =
    produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(1000)
            value = LocalDateTime.now()
        }
    }

/** A ringing alarm: shaking bell, pulsing ring, fake (untappable) Snooze / Stop buttons. */
@Composable
private fun AlarmDisguise() {
    val now by rememberNow()
    val transition = rememberInfiniteTransition(label = "alarm")
    val shake by transition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(110, easing = LinearEasing), RepeatMode.Reverse),
        label = "shake",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(now.format(TimeFormat), color = Soft, fontSize = 72.sp, fontWeight = FontWeight.Light)
        Text("Alarm", color = Dim, fontSize = 18.sp)
        Spacer(Modifier.height(28.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Color(0xFF3A2222), radius = this.size.minDimension / 2f * pulse)
            }
            Text("⏰", fontSize = 60.sp, modifier = Modifier.graphicsLayer { rotationZ = shake })
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            FakeButton("Snooze", filled = false)
            FakeButton("Stop", filled = true)
        }
    }
}

/** Looks like a button but is only drawn. */
@Composable
private fun FakeButton(label: String, filled: Boolean) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (filled) Color(0xFF4A4A4A) else Color.Transparent)
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(23.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Soft, fontSize = 16.sp)
    }
}

/** A dim lock-screen clock with a softly fading hint. */
@Composable
private fun ClockDisguise() {
    val now by rememberNow()
    val transition = rememberInfiniteTransition(label = "clock")
    val hint by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "hint",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(now.format(DateFormat), color = Dim, fontSize = 18.sp)
            Text(now.format(TimeFormat), color = Soft, fontSize = 92.sp, fontWeight = FontWeight.ExtraLight)
        }
        Text(
            "Swipe up to unlock",
            color = Dim,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).graphicsLayer { alpha = hint },
        )
    }
}

/** "Installing system update" with a spinner and a slowly growing bar. */
@Composable
private fun UpdateDisguise() {
    val transition = rememberInfiniteTransition(label = "update")
    val progress by transition.animateFloat(
        initialValue = 0.02f,
        targetValue = 0.97f,
        animationSpec = infiniteRepeatable(tween(180_000, easing = LinearEasing), RepeatMode.Restart),
        label = "progress",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val stroke = 4.dp.toPx()
            drawArc(
                color = Dim,
                startAngle = spin,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(stroke, stroke),
                size = Size(this.size.width - 2 * stroke, this.size.height - 2 * stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Installing system update", color = Soft, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF2A2A2A)),
        ) {
            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(Dim))
        }
        Spacer(Modifier.height(10.dp))
        Text("${(progress * 100).toInt()}%", color = Dim, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Text("Do not turn off your device", color = Dim, fontSize = 13.sp)
    }
}

/** A battery that slowly fills and drains a little, like a charging screen. */
@Composable
private fun ChargingDisguise() {
    val transition = rememberInfiniteTransition(label = "charging")
    val level by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "level",
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(modifier = Modifier.size(width = 90.dp, height = 150.dp)) {
            val stroke = 3.dp.toPx()
            val body = Size(this.size.width, this.size.height - 10.dp.toPx())
            val top = 10.dp.toPx()
            drawRoundRect(
                color = Dim,
                topLeft = Offset(0f, top),
                size = body,
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = stroke),
            )
            drawRoundRect(
                color = Dim,
                topLeft = Offset(this.size.width * 0.35f, 0f),
                size = Size(this.size.width * 0.3f, 8.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            val pad = stroke * 2
            val innerH = body.height - 2 * pad
            val fillH = innerH * level
            drawRoundRect(
                color = Color(0xFF4F7F4F),
                topLeft = Offset(pad, top + pad + (innerH - fillH)),
                size = Size(this.size.width - 2 * pad, fillH),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("${(level * 100).toInt()}%", color = Soft, fontSize = 40.sp, fontWeight = FontWeight.Light)
        Text("Charging", color = Dim, fontSize = 16.sp)
    }
}
