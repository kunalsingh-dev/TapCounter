package com.example.tapcounter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.data.MalaMath
import com.example.tapcounter.data.SummaryMath
import com.example.tapcounter.headset.HeadsetSettings
import java.time.LocalDate

/** The two resets, with the current numbers shown, used in Settings and in History > Resets. */
@Composable
fun ResetControls(app: TapCounterApp) {
    val store = app.counterStore
    val counter by store.state.collectAsStateWithLifecycle()
    val settings by app.headsetSettings.settings.collectAsStateWithLifecycle(initialValue = HeadsetSettings())
    val days by store.history.days.collectAsStateWithLifecycle()
    val baseline by store.history.baseline.collectAsStateWithLifecycle()
    val summary = remember(days, baseline) { SummaryMath.compute(days, baseline, LocalDate.now().toString()) }
    val ring = MalaMath.displayCount(counter.count, settings.malaSize)
    var confirm by remember { mutableIntStateOf(0) } // 0 none, 1 current mala, 2 everything

    val now = "ring $ring · today ${summary.todayTotal} · all time ${summary.allTime} · malas ${summary.allMalas}"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ResetOption(
            title = "Reset current mala",
            text = "The ring goes back to 0. Completed malas, totals and your history stay.",
            current = "Now: ring $ring",
            button = "Reset mala",
            onClick = { confirm = 1 },
        )
        ResetOption(
            title = "Reset ALL malas",
            text = "Ring, today's numbers, all-time count and all malas go to 0. The old numbers are saved in the reset log and your day-by-day history stays.",
            current = "Now: $now",
            button = "Reset all",
            onClick = { confirm = 2 },
        )
    }

    if (confirm == 1) {
        AlertDialog(
            onDismissRequest = { confirm = 0 },
            title = { Text("Reset current mala?") },
            text = { Text("The ring goes back to 0 (it is at $ring). Everything else stays saved. The reset is logged with date and time.") },
            confirmButton = { TextButton(onClick = { store.resetCurrentMala(); confirm = 0 }) { Text("Reset mala") } },
            dismissButton = { TextButton(onClick = { confirm = 0 }) { Text("Cancel") } },
        )
    }
    if (confirm == 2) {
        AlertDialog(
            onDismissRequest = { confirm = 0 },
            title = { Text("Reset ALL malas?") },
            text = { Text("These numbers go to 0: $now. They are saved in the reset log with date and time; your daily history is kept.") },
            confirmButton = { TextButton(onClick = { store.resetAll(); confirm = 0 }) { Text("Reset all") } },
            dismissButton = { TextButton(onClick = { confirm = 0 }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ResetOption(title: String, text: String, current: String, button: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall)
            Text(current, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = onClick) { Text(button) }
        }
    }
}
