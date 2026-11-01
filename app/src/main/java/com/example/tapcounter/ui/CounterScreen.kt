package com.example.tapcounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.headset.HeadsetService
import com.example.tapcounter.headset.HeadsetSettings

@Composable
fun CounterScreen(app: TapCounterApp, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val counter by app.counterStore.state.collectAsStateWithLifecycle()
    val status by app.headsetController.status.collectAsStateWithLifecycle()
    val settings by app.headsetSettings.settings.collectAsStateWithLifecycle(initialValue = HeadsetSettings())
    val enable = rememberEnableHeadset()
    val tapAnywhere by app.counterStore.tapAnywhere.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var confirmReset by remember { mutableStateOf(false) }

    // Whole-screen touch: tap = +1, long press = leave stealth. Buttons/switches consume their own taps first.
    val wholeScreen = Modifier.pointerInput(tapAnywhere, counter.stealth) {
        detectTapGestures(
            onTap = {
                if (tapAnywhere) {
                    app.counterStore.increment()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onLongPress = { if (counter.stealth) app.counterStore.setStealth(false) },
        )
    }

    if (counter.stealth) {
        // Stealth: black screen, no number. Tap anywhere = +1, long-press anywhere = leave.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(wholeScreen),
            contentAlignment = Alignment.Center,
        ) {
            Text("•", color = Color(0xFF222222), fontSize = 24.sp)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(wholeScreen)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(counter.count.toString(), fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
        Text(
            if (tapAnywhere) "Tap anywhere on the screen to count +1" else "Tap + to count",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { app.counterStore.decrement() },
                modifier = Modifier.weight(1f).height(72.dp),
            ) { Text("−", fontSize = 32.sp) }
            Button(
                onClick = { app.counterStore.increment() },
                modifier = Modifier.weight(1f).height(72.dp),
            ) { Text("+", fontSize = 32.sp) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Stealth  ")
                Switch(checked = counter.stealth, onCheckedChange = { app.counterStore.setStealth(it) })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Tap anywhere to count")
            Switch(checked = tapAnywhere, onCheckedChange = { app.counterStore.setTapAnywhere(it) })
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Headphone buttons", style = MaterialTheme.typography.titleMedium)
                if (!status.serviceRunning) {
                    Text("Off. Enable to count with your Bluetooth headset, even with the screen off.")
                    Button(onClick = enable) { Text("Enable headset control") }
                } else {
                    Text(
                        buildString {
                            append("Active")
                            if (settings.hardwareLock) append(" · LOCKED (buttons ignored)")
                            append("\nBluetooth: ")
                            append(
                                when (status.bluetoothConnected) {
                                    true -> "connected"
                                    false -> "not connected"
                                    null -> "unknown"
                                },
                            )
                            append("\nKeep-alive audio: ")
                            append(if (status.keepAliveRunning) "on" else "off")
                            status.lastKeyName?.let { append("\nLast key: $it") }
                        },
                    )
                    OutlinedButton(onClick = { HeadsetService.stop(context) }) { Text("Stop headset control") }
                }
            }
        }

        if (status.serviceRunning && status.possibleConflict) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Another app is playing audio", style = MaterialTheme.typography.titleMedium)
                    Text("It may have taken over the headphone buttons. Press a button to test, or take control back.")
                    Button(onClick = { HeadsetService.retakeControl(context) }) { Text("Re-take control") }
                }
            }
        }

        TextButton(onClick = onOpenSettings) { Text("Headset settings") }
        Text(
            "In stealth the screen is black: tap anywhere to count, hold anywhere to leave.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset counter?") },
            text = { Text("Sets the count back to 0. This can only be done here on screen, never from headphones.") },
            confirmButton = {
                TextButton(onClick = {
                    app.counterStore.reset()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
