package com.example.tapcounter.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.headset.Action
import com.example.tapcounter.headset.Gesture
import com.example.tapcounter.headset.HeadsetKeys
import com.example.tapcounter.headset.HeadsetSettings
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: TapCounterApp, onBack: () -> Unit, onLearn: () -> Unit, onFace: () -> Unit) {
    val context = LocalContext.current
    val repo = app.headsetSettings
    val store = app.counterStore
    val s by repo.settings.collectAsStateWithLifecycle(initialValue = HeadsetSettings())
    val d by store.display.collectAsStateWithLifecycle()
    val faceTemplates by app.faceStore.templates.collectAsStateWithLifecycle()
    val faceEnrolled = faceTemplates.isNotEmpty()
    val scope = rememberCoroutineScope()

    val godPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && importPicture(context, uri, store.godImageFile)) {
            store.updateDisplay { it.copy(imageVersion = it.imageVersion + 1) }
        }
    }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && importPicture(context, uri, store.bgImageFile)) {
            store.updateDisplay { it.copy(bgImageVersion = it.bgImageVersion + 1) }
        }
    }

    ScreenPage("Settings", onBack) {

        // ---------------- Look ----------------
        SectionCard("God name and picture") {
            OutlinedTextField(
                value = d.godName,
                onValueChange = { v -> store.updateDisplay { it.copy(godName = v) } },
                label = { Text("Name (Enter = new line)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Text colour", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GodColors.forEachIndexed { i, color ->
                    Swatch(
                        selected = i == d.godColorIndex,
                        modifier = Modifier.weight(1f),
                        brush = Brush.verticalGradient(listOf(color, color)),
                    ) { store.updateDisplay { it.copy(godColorIndex = i) } }
                }
            }
            IntSlider("Text size", d.godSizeSp, 28..80, "sp") { v -> store.updateDisplay { it.copy(godSizeSp = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { godPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("Add picture")
                }
                OutlinedButton(onClick = {
                    store.godImageFile.delete()
                    store.updateDisplay { it.copy(imageVersion = it.imageVersion + 1) }
                }) { Text("Remove") }
            }
        }

        SectionCard("Background", "Pick a theme, or the last circle for your own colours.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BgThemes.forEachIndexed { i, t ->
                    Swatch(
                        selected = i == d.bgTheme,
                        modifier = Modifier.weight(1f),
                        brush = Brush.verticalGradient(listOf(t.top0, t.bottom0)),
                    ) { store.updateDisplay { it.copy(bgTheme = i) } }
                }
                Swatch(
                    selected = d.bgTheme >= BgThemes.size,
                    modifier = Modifier.weight(1f),
                    brush = Brush.verticalGradient(listOf(Color(d.customTop), Color(d.customBottom))),
                ) { store.updateDisplay { it.copy(bgTheme = BgThemes.size) } }
            }
            Text(
                "Theme: " + if (d.bgTheme >= BgThemes.size) "Custom" else BgThemes[d.bgTheme.coerceIn(0, BgThemes.lastIndex)].name,
                style = MaterialTheme.typography.bodySmall,
            )
            ColorPicker("Custom top colour", d.customTop) { c -> store.updateDisplay { it.copy(customTop = c, bgTheme = BgThemes.size) } }
            ColorPicker("Custom bottom colour", d.customBottom) { c -> store.updateDisplay { it.copy(customBottom = c, bgTheme = BgThemes.size) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("Background picture")
                }
                OutlinedButton(onClick = {
                    store.bgImageFile.delete()
                    store.updateDisplay { it.copy(bgImageVersion = it.bgImageVersion + 1) }
                }) { Text("Remove") }
            }
        }

        // ---------------- Counting ----------------
        SectionCard("Counting") {
            SwitchRow("Tap anywhere on the screen to count", d.tapAnywhere) { v -> store.updateDisplay { it.copy(tapAnywhere = v) } }

            Text("Touch speed", style = MaterialTheme.typography.labelMedium)
            val gaps = listOf(400, 200, 100, 0)
            SegmentedControl(listOf("Slow", "Normal", "Fast", "Turbo"), gaps.indexOf(d.tapMinGapMs)) { i ->
                store.updateDisplay { it.copy(tapMinGapMs = gaps[i]) }
            }
            IntSlider("Minimum time between counted taps", d.tapMinGapMs, 0..800, "ms") { v -> store.updateDisplay { it.copy(tapMinGapMs = v) } }
            Text(
                "Turbo counts every touch. Slow ignores touches closer than 0.4 s, which stops accidental double touches. Applies to screen taps.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Beads per mala", style = MaterialTheme.typography.labelMedium)
            SegmentedControl(HeadsetSettings.MALA_SIZES.map { it.toString() }, HeadsetSettings.MALA_SIZES.indexOf(s.malaSize)) { i ->
                scope.launch { repo.setMalaSize(HeadsetSettings.MALA_SIZES[i]) }
            }
            IntSlider("Daily goal (0 = off)", d.dailyGoalMalas, 0..30, "malas") { v -> store.updateDisplay { it.copy(dailyGoalMalas = v) } }

            SwitchRow("Phone volume buttons count", s.volumeKeysCount) { scope.launch { repo.setVolumeKeysCount(it) } }
            Text(
                "While headset control runs, the volume keys count +1 (also with the screen off) and do not change the media volume. " +
                    "A key held down counts once. Turn this off to get normal volume keys back. Some phones don't allow it with the screen off.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ---------------- Stealth + disguise ----------------
        SectionCard(
            "Stealth",
            "Hold FOUR or FIVE fingers on the screen to enter and leave. On headphones, hold Play/Pause. In stealth the screen is dimmed and black; a tap still counts.",
        ) {
            IntSlider("Finger-hold time", d.stealthHoldMs, 300..5000, "ms") { v -> store.updateDisplay { it.copy(stealthHoldMs = v) } }
            IntSlider("Brightness in stealth", d.stealthBrightnessPct, 1..30, "%") { v -> store.updateDisplay { it.copy(stealthBrightnessPct = v) } }
            SwitchRow("Hide the time bar (status bar) in stealth", d.hideBars) { v -> store.updateDisplay { it.copy(hideBars = v) } }
        }

        SectionCard(
            "Calculator disguise",
            "Leaving stealth, and starting the app, shows a working calculator. Type your code and press = to open the counter.",
        ) {
            SwitchRow("Show the calculator", d.calcLock) { v -> store.updateDisplay { it.copy(calcLock = v, faceUnlock = d.faceUnlock && v) } }
            var codeDraft by remember(d.calcCode) { mutableStateOf(d.calcCode) }
            OutlinedTextField(
                value = codeDraft,
                onValueChange = { v ->
                    val digits = v.filter { it.isDigit() }.take(8)
                    codeDraft = digits
                    if (digits.length >= 4) store.updateDisplay { it.copy(calcCode = digits) }
                },
                label = { Text("Secret code (4 to 8 digits)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Default 1234. Remember it: without the code (or your face) you cannot open the counter. " +
                    "Headphone and volume counting keep working while the calculator is shown.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(
            "Face unlock",
            "Needs the calculator. The app opens on the calculator and quietly looks for YOUR face: if it sees you, the counter opens. Another face keeps the calculator, and while the counter is open another face switches back to it. The camera only runs while the app is on screen.",
        ) {
            Button(onClick = onFace, modifier = Modifier.fillMaxWidth()) {
                Text(if (faceEnrolled) "Test / re-enroll my face" else "Set up face unlock")
            }
            SwitchRow("Use face unlock", d.faceUnlock && faceEnrolled && d.calcLock) { on ->
                if (on && !faceEnrolled) onFace() else store.updateDisplay { it.copy(faceUnlock = on, calcLock = it.calcLock || on) }
            }
            SwitchRow("Counter open: another face switches to the calculator", d.faceWatch) { v -> store.updateDisplay { it.copy(faceWatch = v) } }
            SwitchRow("Two or more faces in view count as a stranger", d.faceLockOnMultiple) { v -> store.updateDisplay { it.copy(faceLockOnMultiple = v) } }
            SwitchRow("Anti-photo check: blink once to unlock", d.faceRequireBlink) { v -> store.updateDisplay { it.copy(faceRequireBlink = v) } }
            IntSlider("Match strictness", d.faceThresholdPct, 35..85, "%") { v -> store.updateDisplay { it.copy(faceThresholdPct = v) } }
        }

        // ---------------- Feedback ----------------
        SectionCard("Vibration") {
            SwitchRow("Short buzz on every count", s.vibrationEnabled) { scope.launch { repo.setVibrationEnabled(it) } }
            SwitchRow("Long triple buzz when a mala is complete", s.malaVibration) { scope.launch { repo.setMalaVibration(it) } }
            SwitchRow("Confirmation tick through the headset", s.confirmationTick) { scope.launch { repo.setConfirmationTick(it) } }
        }

        // ---------------- Headset ----------------
        SectionCard("Headset / Bluetooth") {
            SwitchRow("Hardware lock (ignore headphone and volume buttons)", s.hardwareLock) { scope.launch { repo.setHardwareLock(it) } }
            SwitchRow("Keep-alive silent audio", s.keepAliveAudio) { scope.launch { repo.setKeepAliveAudio(it) } }
            Text(
                "Keep-alive plays an inaudible loop so Android keeps sending button presses to this app. The Bluetooth link stays in streaming mode, which costs a little battery. Turn it off if your headset works without it.",
                style = MaterialTheme.typography.bodySmall,
            )
            TimingSlider("Debounce", s.debounceMs, HeadsetSettings.DEBOUNCE_RANGE) { scope.launch { repo.setTiming(it, s.multiTapWindowMs, s.longPressMs) } }
            TimingSlider("Hold time (stealth on headphones)", s.longPressMs, HeadsetSettings.LONG_PRESS_RANGE) { scope.launch { repo.setTiming(s.debounceMs, s.multiTapWindowMs, it) } }
            TimingSlider("Double/triple-click window", s.multiTapWindowMs, HeadsetSettings.MULTI_TAP_RANGE) { scope.launch { repo.setTiming(s.debounceMs, it, s.longPressMs) } }
            Text("Single clicks count instantly while no double or triple click is assigned to that button.", style = MaterialTheme.typography.bodySmall)

            Button(onClick = onLearn, modifier = Modifier.fillMaxWidth()) { Text("Press a headphone button (Learn mode)") }

            val keys = s.bindings.keys.map { it.first }.distinct().sorted()
            if (keys.isEmpty()) Text("No buttons mapped yet. Use Learn mode.")
            keys.forEach { key ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${HeadsetKeys.name(key)} ($key)", style = MaterialTheme.typography.titleSmall)
                        Gesture.entries.forEach { g ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(g.label(), modifier = Modifier.weight(1f))
                                ActionPicker(s.bindings[key to g] ?: Action.NONE) { action -> scope.launch { repo.setBinding(key, g, action) } }
                            }
                        }
                        TextButton(onClick = { scope.launch { repo.clearBindingsForKey(key) } }) { Text("Remove this button") }
                    }
                }
            }
            OutlinedButton(onClick = { scope.launch { repo.resetBindingsToDefaults() } }) { Text("Restore default mapping") }
        }

        // ---------------- Reset ----------------
        SectionCard("Reset", "Only possible here and in History. Headphones and volume keys can never reset anything.") {
            ResetControls(app)
        }

        SectionCard("Honest limits") {
            Text(
                "• Volume buttons on a Bluetooth headset change the headset's own volume and are NOT sent to the phone, so they can't count.\n" +
                    "• Fingerprint-sensor presses can't be read by apps.\n" +
                    "• Reliable on most headsets: Play/Pause (or the hook button), Next, Previous.\n" +
                    "• Many earbuds handle double/triple taps and long press themselves and send nothing or Next/Previous. Use Learn mode.\n" +
                    "• Another media app playing can take the buttons: use Re-take control.\n" +
                    "• Face unlock is a convenience disguise, not bank-grade security.\n" +
                    "• Battery savers on some phones stop the service: set this app to Unrestricted battery.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Round colour chip that grows with the available width. */
@Composable
private fun Swatch(selected: Boolean, modifier: Modifier, brush: Brush, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(brush)
            .border(3.dp, if (selected) Color(0xFF8AB4F8) else Color.Gray, CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun TimingSlider(label: String, value: Long, range: LongRange, onCommit: (Long) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$label: ${local.toInt()} ms")
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChangeFinished = { onCommit(local.toLong()) },
        )
    }
}

private val PaletteColors = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5,
    0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41, 0xFF455A64, 0xFF111111,
).map { Color(it) }

/** Palette dots for a quick pick, plus hue / saturation / brightness sliders for any colour. */
@Composable
private fun ColorPicker(title: String, argb: Int, onChange: (Int) -> Unit) {
    val hsv = remember(argb) { FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) } }
    var hue by remember(argb) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(argb) { mutableFloatStateOf(hsv[1]) }
    var bright by remember(argb) { mutableFloatStateOf(hsv[2]) }
    fun current() = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), bright.coerceIn(0f, 1f))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Box(modifier = Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp)).background(current()))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            PaletteColors.forEach { c ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(c)
                        .clickable { onChange(c.toArgb()) },
                )
            }
        }
        Text("Hue", style = MaterialTheme.typography.labelSmall)
        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, onValueChangeFinished = { onChange(current().toArgb()) })
        Text("Saturation", style = MaterialTheme.typography.labelSmall)
        Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f, onValueChangeFinished = { onChange(current().toArgb()) })
        Text("Brightness", style = MaterialTheme.typography.labelSmall)
        Slider(value = bright, onValueChange = { bright = it }, valueRange = 0f..1f, onValueChangeFinished = { onChange(current().toArgb()) })
    }
}

/** Copies the chosen picture into app storage (downsized) so it keeps working after the picker closes. */
private fun importPicture(context: Context, uri: Uri, target: File): Boolean {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false
        target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Exception) {
        false
    }
}
