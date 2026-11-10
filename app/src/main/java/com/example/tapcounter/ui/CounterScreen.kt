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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.counter.CounterProfiles
import com.example.tapcounter.counter.DisplayState
import com.example.tapcounter.counter.StealthBrightness
import com.example.tapcounter.data.MalaMath
import com.example.tapcounter.data.MalaStats
import com.example.tapcounter.data.Summary
import com.example.tapcounter.data.SummaryMath
import com.example.tapcounter.headset.HeadsetService
import com.example.tapcounter.headset.HeadsetSettings
import com.example.tapcounter.headset.HeadsetStatus
import java.time.LocalDate

@Composable
fun CounterScreen(
    app: TapCounterApp,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenCounters: () -> Unit,
) {
    val view = LocalView.current
    val counter by app.counterStore.state.collectAsStateWithLifecycle()
    val display by app.counterStore.display.collectAsStateWithLifecycle()
    val days by app.counterStore.history.days.collectAsStateWithLifecycle()
    val baseline by app.counterStore.history.baseline.collectAsStateWithLifecycle()
    val malaRecords by app.counterStore.history.malas.collectAsStateWithLifecycle()
    val status by app.headsetController.status.collectAsStateWithLifecycle()
    val activeId by app.counterStore.activeId.collectAsStateWithLifecycle()
    val counters by app.counterStore.counters.collectAsStateWithLifecycle()
    val settings by app.headsetSettings.settings.collectAsStateWithLifecycle(initialValue = HeadsetSettings())
    var menuOpen by remember { mutableStateOf(false) }

    // Stealth: dimmest brightness (so a black screen doesn't glow) and hidden time/status bar.
    DisposableEffect(counter.stealth, display.hideBars, display.stealthBrightnessPct, display.disguise, display.lcdMode, display.disguiseBrightnessPct) {
        val window = view.context.findActivity()?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        if (counter.stealth) {
            window?.setBrightness(
                StealthBrightness.pick(display.disguise, display.lcdMode, display.stealthBrightnessPct, display.disguiseBrightnessPct),
            )
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

    val holdMs = display.stealthHoldMs.coerceIn(300, 5000).toLong()
    val gestures = Modifier.pointerInput(display.tapAnywhere, counter.stealth, holdMs) {
        detectCounterGestures(
            tapEnabled = display.tapAnywhere,
            comboHoldMs = holdMs,
            onTap = { app.headsetController.countTap(display.tapMinGapMs.toLong()) },
            onUndo = { app.counterStore.undoLastCount() },
            onCombo = { app.headsetController.stealthComboFromScreen() },
        )
    }

    if (counter.stealth) {
        // Black, or a disguise (fake alarm, clock, update, charging). The disguise is only drawn: nothing on it
        // can be tapped, so a tap anywhere counts +1; 4-5 fingers held = leave stealth (calculator appears).
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).then(gestures)) {
            DisguiseScreen(display.disguise)
        }
        return
    }

    val theme = BgThemes[display.bgTheme.coerceIn(0, BgThemes.lastIndex)]
    val progress = MalaMath.progress(counter.count, settings.malaSize)
    val customTheme = display.bgTheme >= BgThemes.size
    val top = if (customTheme) Color(display.customTop) else lerp(theme.top0, theme.top1, progress)
    val bottom = if (customTheme) Color(display.customBottom) else lerp(theme.bottom0, theme.bottom1, progress)
    val summary = remember(days, baseline) { SummaryMath.compute(days, baseline, LocalDate.now().toString()) }
    val malaStats = remember(malaRecords) { MalaStats.summarize(malaRecords.map { it.seconds }) }
    val godImage = remember(display.imageVersion, activeId) {
        BitmapFactory.decodeFile(app.counterStore.godImageFile.path)?.asImageBitmap()
    }
    val bgImage = remember(display.bgImageVersion) {
        BitmapFactory.decodeFile(app.counterStore.bgImageFile.path)?.asImageBitmap()
    }
    val ringValue = MalaMath.displayCount(counter.count, settings.malaSize)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .then(gestures),
    ) {
        bgImage?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0F12))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val activeName = counters.firstOrNull { it.id == activeId }?.name.orEmpty()
                Text(
                    "नाम जप Counter" + if (activeId != CounterProfiles.MAIN_ID && activeName.isNotEmpty()) " · $activeName" else "",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("⋮", color = Color.White, fontSize = 26.sp) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Counters") }, onClick = { menuOpen = false; onOpenCounters() })
                        DropdownMenuItem(text = { Text("History") }, onClick = { menuOpen = false; onOpenHistory() })
                        DropdownMenuItem(text = { Text("Analytics") }, onClick = { menuOpen = false; onOpenAnalytics() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; onOpenSettings() })
                        DropdownMenuItem(
                            text = { Text("Hide (show calculator)") },
                            onClick = { menuOpen = false; app.counterStore.lockNow() },
                        )
                    }
                }
            }

            // Everything below adapts to the space left: portrait, landscape, small phones, tablets.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                val landscape = maxWidth > maxHeight * 1.15f
                val scale = (maxHeight.value / 560f).coerceIn(0.6f, 1.15f)
                val rawRing: Dp = if (landscape) minOf(maxHeight * 0.8f, maxWidth * 0.38f) else minOf(maxWidth * 0.62f, maxHeight * 0.36f)
                val ringSize: Dp = rawRing.coerceIn(120.dp, 300.dp)

                if (landscape) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        ) {
                            GodHeader(display, godImage, scale)
                            InfoBox(display, summary, malaStats.lastSec, malaStats.avgSec, status, settings, app)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        ) {
                            MalaRing(ringValue, progress, ringSize, settings.malaSize, counter.count)
                            Text("Today's Mala Count: ${summary.todayMalas}", color = Color.White, fontSize = (20f * scale).sp)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        GodHeader(display, godImage, scale)
                        MalaRing(ringValue, progress, ringSize, settings.malaSize, counter.count)
                        Text("Today's Mala Count: ${summary.todayMalas}", color = Color.White, fontSize = (22f * scale).sp)
                        InfoBox(display, summary, malaStats.lastSec, malaStats.avgSec, status, settings, app)
                    }
                }
            }
        }
    }
}

@Composable
private fun GodHeader(display: DisplayState, godImage: ImageBitmap?, scale: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        godImage?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height((130f * scale).dp).padding(bottom = 6.dp),
            )
        }
        val size = (display.godSizeSp * scale).coerceAtLeast(18f)
        Text(
            text = display.godName,
            color = GodColors[display.godColorIndex.coerceIn(0, GodColors.lastIndex)],
            fontSize = size.sp,
            lineHeight = (size * 1.25f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MalaRing(value: Int, progress: Float, size: Dp, malaSize: Int, count: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.082f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val sweep = if (count % malaSize == 0) 0f else maxOf(360f * progress, 3f)
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
        Text(text = value.toString(), color = Color.White, fontSize = (size.value * 0.33f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoBox(
    display: DisplayState,
    summary: Summary,
    lastSec: Int?,
    avgSec: Int?,
    status: HeadsetStatus,
    settings: HeadsetSettings,
    app: TapCounterApp,
) {
    val context = LocalContext.current
    val enable = rememberEnableHeadset()
    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111418))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Today ${summary.todayTotal}  ·  All time ${summary.allTime}  ·  Malas ${summary.allMalas}",
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (display.dailyGoalMalas > 0) {
            val met = summary.todayMalas >= display.dailyGoalMalas
            Text(
                "Goal ${summary.todayMalas}/${display.dailyGoalMalas} malas" + if (met) "  ✓" else "",
                color = if (met) Color(0xFFFFD54F) else Color.White,
                fontSize = 13.sp,
            )
        }
        lastSec?.let { last ->
            Text(
                "Last mala ${formatDuration(last)}  ·  Average ${formatDuration(avgSec ?: last)}",
                color = Color(0xFFB0B8C4),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (!status.serviceRunning) {
            TextButton(onClick = enable) { Text("Enable headset + volume buttons", color = Color(0xFF8AB4F8)) }
        } else {
            Text(
                buildString {
                    append("Buttons active")
                    if (settings.hardwareLock) append(" (locked)")
                    status.lastKeyName?.let { append(" · last: $it") }
                },
                color = Color(0xFFB0B8C4),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            if (status.possibleConflict) {
                TextButton(onClick = { HeadsetService.retakeControl(context) }) {
                    Text("Another app is playing: re-take control", color = Color(0xFFFFB4AB))
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
