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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
fun SettingsScreen(app: TapCounterApp, onBack: () -> Unit, onLearn: () -> Unit) {
    val context = LocalContext.current
    val repo = app.headsetSettings
    val store = app.counterStore
    val s by repo.settings.collectAsStateWithLifecycle(initialValue = HeadsetSettings())
    val d by store.display.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && importGodImage(context, uri, store.godImageFile)) {
            store.updateDisplay { it.copy(imageVersion = it.imageVersion + 1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // ---------------- Display ----------------
        Text("God name and picture", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = d.godName,
            onValueChange = { v -> store.updateDisplay { it.copy(godName = v) } },
            label = { Text("Name (new line = Enter)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Colour")
            GodColors.forEachIndexed { i, color ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(3.dp, if (i == d.godColorIndex) Color(0xFF8AB4F8) else Color.Gray, CircleShape)
                        .clickable { store.updateDisplay { it.copy(godColorIndex = i) } },
                )
            }
        }
        IntSlider("Text size", d.godSizeSp, 28..80, "sp") { v -> store.updateDisplay { it.copy(godSizeSp = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Add picture") }
            OutlinedButton(onClick = {
                store.godImageFile.delete()
                store.updateDisplay { it.copy(imageVersion = it.imageVersion + 1) }
            }) { Text("Remove picture") }
        }

        // ---------------- Counting ----------------
        Text("Counting", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Tap anywhere on the screen to count", d.tapAnywhere) { v ->
            store.updateDisplay { it.copy(tapAnywhere = v) }
        }
        Text("Beads per mala")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeadsetSettings.MALA_SIZES.forEach { size ->
                if (size == s.malaSize) Button(onClick = {}) { Text("$size") }
                else OutlinedButton(onClick = { scope.launch { repo.setMalaSize(size) } }) { Text("$size") }
            }
        }
        SwitchRow("Phone volume buttons count", s.volumeKeysCount) { scope.launch { repo.setVolumeKeysCount(it) } }
        Text(
            "While headset control is running, the volume keys count +1 (also with the screen off) and do not change " +
                "the media volume. A key that is held down counts once; press again for the next count. " +
                "Turn this off to get normal volume keys back. Some phones don't allow this with the screen off.",
            style = MaterialTheme.typography.bodySmall,
        )

        // ---------------- Stealth ----------------
        Text("Stealth", style = MaterialTheme.typography.titleMedium)
        Text(
            "Enter and leave: hold TWO fingers on the screen for about 1 second. " +
                "On headphones: hold the Play/Pause button about 1.2 s (changeable below). " +
                "In stealth the screen is dimmed and black, a tap still counts.",
            style = MaterialTheme.typography.bodySmall,
        )
        IntSlider("Stealth brightness", d.stealthBrightnessPct, 1..30, "%") { v ->
            store.updateDisplay { it.copy(stealthBrightnessPct = v) }
        }
        SwitchRow("Hide time bar (status bar) in stealth", d.hideBars) { v -> store.updateDisplay { it.copy(hideBars = v) } }

        // ---------------- Vibration ----------------
        Text("Vibration", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Short buzz on every count", s.vibrationEnabled) { scope.launch { repo.setVibrationEnabled(it) } }
        SwitchRow("Long triple buzz when a mala is complete", s.malaVibration) { scope.launch { repo.setMalaVibration(it) } }
        SwitchRow("Confirmation tick through headset", s.confirmationTick) { scope.launch { repo.setConfirmationTick(it) } }

        // ---------------- Headset ----------------
        Text("Headset / Bluetooth", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Hardware lock (ignore headphone + volume buttons)", s.hardwareLock) {
            scope.launch { repo.setHardwareLock(it) }
        }
        SwitchRow("Keep-alive silent audio", s.keepAliveAudio) { scope.launch { repo.setKeepAliveAudio(it) } }
        Text(
            "Keep-alive plays an inaudible looping sound so Android keeps sending button presses to this app. " +
                "Trade-off: the Bluetooth link stays in streaming mode (a little more battery). Turn it off if your headset works without it.",
            style = MaterialTheme.typography.bodySmall,
        )
        TimingSlider("Debounce", s.debounceMs, HeadsetSettings.DEBOUNCE_RANGE) {
            scope.launch { repo.setTiming(it, s.multiTapWindowMs, s.longPressMs) }
        }
        TimingSlider("Hold time (stealth on headphones)", s.longPressMs, HeadsetSettings.LONG_PRESS_RANGE) {
            scope.launch { repo.setTiming(s.debounceMs, s.multiTapWindowMs, it) }
        }
        TimingSlider("Double/triple-click window", s.multiTapWindowMs, HeadsetSettings.MULTI_TAP_RANGE) {
            scope.launch { repo.setTiming(s.debounceMs, it, s.longPressMs) }
        }
        Text(
            "Single clicks count instantly as long as no double or triple click is assigned to that button.",
            style = MaterialTheme.typography.bodySmall,
        )

        Button(onClick = onLearn, modifier = Modifier.fillMaxWidth()) { Text("Press a headphone button (Learn mode)") }

        val keys = s.bindings.keys.map { it.first }.distinct().sorted()
        if (keys.isEmpty()) Text("No buttons mapped yet. Use Learn mode.")
        keys.forEach { key ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${HeadsetKeys.name(key)} ($key)", style = MaterialTheme.typography.titleSmall)
                    Gesture.entries.forEach { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(g.label())
                            ActionPicker(s.bindings[key to g] ?: Action.NONE) { action ->
                                scope.launch { repo.setBinding(key, g, action) }
                            }
                        }
                    }
                    TextButton(onClick = { scope.launch { repo.clearBindingsForKey(key) } }) { Text("Remove this button") }
                }
            }
        }
        OutlinedButton(onClick = { scope.launch { repo.resetBindingsToDefaults() } }) { Text("Restore default mapping") }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Honest limits", style = MaterialTheme.typography.titleSmall)
                Text(
                    "• Volume buttons on a Bluetooth headset change the headset's own volume and are NOT sent to the phone, so they can't count.\n" +
                        "• Reliable on most headsets: Play/Pause (or the single hook button), Next, Previous.\n" +
                        "• Many earbuds handle double/triple taps and long press themselves (assistant, noise control) and send nothing or Next/Previous. Use Learn mode.\n" +
                        "• Another media app playing can take the buttons: use Re-take control.\n" +
                        "• Battery savers on some phones stop the service: set this app to Unrestricted battery.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---------------- Reset (only here, never on the main screen, never from headphones) ----------------
        Text("Danger zone", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { confirmReset = true }) { Text("Reset current counter to 0") }
        Text("History is kept. Reset can only be done on this screen.", style = MaterialTheme.typography.bodySmall)
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset counter?") },
            text = { Text("Sets the current count back to 0. Your saved history is not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    store.resetCount()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
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

@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, unit: String, onCommit: (Int) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$label: ${local.toInt()} $unit")
        Slider(
            value = local,
            onValueChange = { local = it },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChangeFinished = { onCommit(local.toInt()) },
        )
    }
}

/** Copies the chosen picture into app storage (downsized) so it keeps working after the picker closes. */
private fun importGodImage(context: Context, uri: Uri, target: File): Boolean {
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
