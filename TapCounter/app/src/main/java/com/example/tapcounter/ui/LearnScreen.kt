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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.headset.Action
import com.example.tapcounter.headset.Gesture
import com.example.tapcounter.headset.HeadsetSettings
import kotlinx.coroutines.launch

@Composable
fun LearnScreen(app: TapCounterApp, onDone: () -> Unit) {
    val controller = app.headsetController
    val repo = app.headsetSettings
    val learn by controller.learn.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = HeadsetSettings())
    val enable = rememberEnableHeadset()
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        controller.startLearn()
        onDispose { controller.stopLearn() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onDone) { Text("← Back") }
        Text("Press a headphone button", style = MaterialTheme.typography.headlineSmall)

        if (!status.serviceRunning) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Headset control isn't running, so button presses can't be seen.")
                    Button(onClick = enable) { Text("Start headset control") }
                }
            }
        }

        val captured = learn.captured
        if (captured == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Waiting… press ONE button on your headset once.")
            }
            Text(
                "The next key press is captured here and not counted. Works even with the hardware lock on.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Captured", style = MaterialTheme.typography.labelMedium)
                    Text(captured.name, style = MaterialTheme.typography.titleLarge)
                    Text("keycode ${captured.keyCode}")
                }
            }

            val draft = remember(captured.keyCode) {
                mutableStateMapOf<Gesture, Action>().apply {
                    Gesture.entries.forEach { g -> put(g, settings.bindings[captured.keyCode to g] ?: Action.NONE) }
                }
            }
            Text("Assign an action to each gesture:", style = MaterialTheme.typography.titleMedium)
            Gesture.entries.forEach { g ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(g.label())
                    ActionPicker(draft[g] ?: Action.NONE) { draft[g] = it }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        Gesture.entries.forEach { g ->
                            repo.setBinding(captured.keyCode, g, draft[g] ?: Action.NONE)
                        }
                        onDone()
                    }
                },
            ) { Text("Save") }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { controller.startLearn() }) {
                Text("Capture a different button")
            }
        }
    }
}
