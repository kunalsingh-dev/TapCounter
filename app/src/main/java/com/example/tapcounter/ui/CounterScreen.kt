package com.example.tapcounter.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.data.MalaMath
import com.example.tapcounter.headset.HeadsetService
import com.example.tapcounter.headset.HeadsetSettings
import java.time.LocalDate

@Composable
fun CounterScreen(app: TapCounterApp, onOpenSettings: () -> Unit, onOpenHistory: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    val counter by app.counterStore.state.collectAsStateWithLifecycle()
    val display by app.counterStore.display.collectAsStateWithLifecycle()
    val days by app.counterStore.history.days.collectAsStateWithLifecycle()
    val status by app.headsetController.status.collectAsStateWithLifecycle()
    val settings by app.headsetSettings.settings.collectAsStateWithLifecycle(initialValue = HeadsetSettings())
    val enable = rememberEnableHeadset()
    var menuOpen by remember { mutableStateOf(false) }

    // Stealth: dimmest brightness (so a black screen doesn't glow) and hidden time/status bar.
    DisposableEffect(counter.stealth, display.hideBars, display.stealthBrightnessPct) {
        val window = view.context.findActivity()?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        if (counter.stealth) {
            window?.setBrightness(display.stealthBrightnessPct.coerceIn(1, 100) / 100f)
            if (display.hideBars) {
                insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insets?.hide(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            window?.setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            insets?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            insets?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val gestures = Modifier.pointerInput(display.tapAnywhere, counter.stealth) {
        detectCounterGestures(
            tapEnabled = display.tapAnywhere,
            onTap = { app.headsetController.countTap() },
            onCombo = { app.headsetController.stealthComboFromScreen() },
        )
    }

    if (counter.stealth) {
        // Pure black, nothing drawn. Tap = +1, two fingers held 1 s = leave stealth.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).then(gestures))
        return
    }

    val progress = MalaMath.progress(counter.count, settings.malaSize)
    val top = lerp(Color(0xFF4BA591), Color(0xFF5AA0B5), progress)
    val bottom = lerp(Color(0xFF4E5E86), Color(0xFF7A8EB0), progress)
    val today = days.firstOrNull { it.date == LocalDate.now().toString() }
    val allTime = days.sumOf { it.total }
    val godImage = remember(display.imageVersion) {
        BitmapFactory.decodeFile(app.counterStore.godImageFile.path)?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .then(gestures),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0F12))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("नाम जप Counter", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("⋮", color = Color.White, fontSize = 26.sp) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("History") }, onClick = { menuOpen = false; onOpenHistory() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpenSettings() })
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                // God picture + name
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    godImage?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(140.dp).padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        text = display.godName,
                        color = GodColors[display.godColorIndex.coerceIn(0, GodColors.lastIndex)],
                        fontSize = display.godSizeSp.sp,
                        lineHeight = (display.godSizeSp * 1.25f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                // Ring with the count inside the current mala
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 18.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            color = Color.White.copy(alpha = 0.25f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                        val sweep = if (counter.count % settings.malaSize == 0) 0f else maxOf(360f * progress, 3f)
                        drawArc(
                            color = Color.White,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    Text(
                        text = MalaMath.displayCount(counter.count, settings.malaSize).toString(),
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text("Today's Mala Count: ${today?.malas ?: 0}", color = Color.White, fontSize = 22.sp)

                // Info box
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111418))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Today: ${today?.total ?: 0}   ·   All time: $allTime",
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                    if (!status.serviceRunning) {
                        TextButton(onClick = enable) {
                            Text("Enable headset + volume buttons", color = Color(0xFF8AB4F8))
                        }
                    } else {
                        Text(
                            buildString {
                                append("Buttons active")
                                if (settings.hardwareLock) append(" (locked)")
                                status.lastKeyName?.let { append(" · last: $it") }
                            },
                            color = Color(0xFFB0B8C4),
                            fontSize = 12.sp,
                        )
                        if (status.possibleConflict) {
                            TextButton(onClick = { HeadsetService.retakeControl(context) }) {
                                Text("Another app is playing: re-take control", color = Color(0xFFFFB4AB))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Window.setBrightness(value: Float) {
    val lp = attributes
    lp.screenBrightness = value
    attributes = lp
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
