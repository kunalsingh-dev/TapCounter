package com.example.tapcounter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.headset.Action
import com.example.tapcounter.headset.Gesture
import com.example.tapcounter.headset.HeadsetKeys
import com.example.tapcounter.headset.HeadsetSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: TapCounterApp, onBack: () -> Unit, onLearn: () -> Unit) {
    val repo = app.headsetSettings
    val s by repo.settings.collectAsStateWithLifecycle(initialValue = HeadsetSettings())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Headset settings", style = MaterialTheme.typography.headlineSmall)

        SwitchRow("Hardware lock (ignore headphone buttons)", s.hardwareLock) {
            scope.launch { repo.setHardwareLock(it) }
        }
        SwitchRow("Vibration feedback", s.vibrationEnabled) { scope.launch { repo.setVibrationEnabled(it) } }
        SwitchRow("Confirmation tick through headset", s.confirmationTick) {
            scope.launch { repo.setConfirmationTick(it) }
        }
        SwitchRow("Keep-alive silent audio", s.keepAliveAudio) { scope.launch { repo.setKeepAliveAudio(it) } }
        Text(
            "Keep-alive plays an inaudible looping sound so Android keeps sending button presses to this app. " +
                "Trade-off: the Bluetooth link stays in streaming mode, which uses a little more battery on phone and headset. " +
                "Turn it off if buttons still work without it on your headset.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Timing", style = MaterialTheme.typography.titleMedium)
        TimingSlider("Debounce", s.debounceMs, HeadsetSettings.DEBOUNCE_RANGE) {
            scope.launch { repo.setTiming(it, s.multiTapWindowMs, s.longPressMs) }
        }
        TimingSlider("Multi-click window", s.multiTapWindowMs, HeadsetSettings.MULTI_TAP_RANGE) {
            scope.launch { repo.setTiming(s.debounceMs, it, s.longPressMs) }
        }
        TimingSlider("Long press after", s.longPressMs, HeadsetSettings.LONG_PRESS_RANGE) {
            scope.launch { repo.setTiming(s.debounceMs, s.multiTapWindowMs, it) }
        }
        Text(
            "A single click is only counted after the multi-click window passes, so a lower window feels faster.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Button mapping", style = MaterialTheme.typography.titleMedium)
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
        Text(
            "Reset is not available for headphone buttons. It can only be done on screen.",
            style = MaterialTheme.typography.bodySmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Honest limits", style = MaterialTheme.typography.titleSmall)
                Text(
                    "• Volume up/down on Bluetooth headsets usually change the headset's own volume and are NOT sent to the phone as key events, so they can't count.\n" +
                        "• Reliable: Play/Pause (or the single hook button), Next, Previous. Many earbuds turn their own double/triple tap into Next/Previous, so use Learn mode to see what yours sends.\n" +
                        "• Long press is often reserved by the headset (voice assistant, noise control) and may send nothing.\n" +
                        "• Another media app playing can take the buttons away. Use Re-take control.\n" +
                        "• Battery savers on some phones kill the service. Exclude this app from battery optimisation.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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
