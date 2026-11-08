package com.example.tapcounter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.data.DayRecord
import com.example.tapcounter.data.ResetEvent
import com.example.tapcounter.data.ResetType
import com.example.tapcounter.data.SummaryMath
import java.time.LocalDate

@Composable
fun HistoryScreen(app: TapCounterApp, onBack: () -> Unit) {
    val days by app.counterStore.history.days.collectAsStateWithLifecycle()
    val resets by app.counterStore.history.resets.collectAsStateWithLifecycle()
    val baseline by app.counterStore.history.baseline.collectAsStateWithLifecycle()
    val summary = remember(days, baseline) { SummaryMath.compute(days, baseline, LocalDate.now().toString()) }
    var tab by remember { mutableIntStateOf(0) }

    ScreenFrame("History", onBack) {
        StatGrid(
            listOf(
                "Today" to summary.todayTotal.toString(),
                "Malas today" to summary.todayMalas.toString(),
                "All time" to summary.allTime.toString(),
                "All malas" to summary.allMalas.toString(),
            ),
        )
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            SegmentedControl(listOf("Days (${days.size})", "Resets (${resets.size})"), tab) { tab = it }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            if (tab == 0) {
                if (days.isEmpty()) item { EmptyNote("Nothing counted yet. Your days appear here automatically.") }
                items(days, key = { it.date }) { day -> DayCard(day) }
            } else {
                item { SectionCard("Reset", "Both resets are written to the log below with the date and time.") { ResetControls(app) } }
                item { Text("Reset log", style = MaterialTheme.typography.titleMedium) }
                if (resets.isEmpty()) item { EmptyNote("No resets yet.") }
                items(resets) { r -> ResetCard(r) }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ResetCard(r: ResetEvent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (r.type == ResetType.ALL) Pill("ALL", Color(0xFFD32F2F)) else Pill("MALA", Color(0xFF1976D2))
                Text(formatStamp(r.at), style = MaterialTheme.typography.titleSmall)
            }
            if (r.type == ResetType.MALA) {
                Text("The ring was at ${r.ringCount} and went back to 0. Totals and malas were kept.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Everything was set to 0. Numbers before the reset:", style = MaterialTheme.typography.bodyMedium)
                StatGrid(
                    listOf(
                        "Ring" to r.ringCount.toString(),
                        "Today" to r.todayTotal.toString(),
                        "All time" to r.allTime.toString(),
                        "Malas" to r.allMalas.toString(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DayCard(day: DayRecord) {
    var expanded by remember(day.date) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(formatDay(day.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${day.total} counts", style = MaterialTheme.typography.titleSmall)
                    Text("${day.malas} mala" + if (day.malas == 1) "" else "s", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (expanded) {
                BarChart(day.hours, List(24) { if (it % 6 == 0 || it == 23) it.toString() else "" }, Color(0xFF5AA0B5), chartHeight = 90.dp)
                val peak = day.hours.indices.maxByOrNull { day.hours[it] }
                if (peak != null && day.hours[peak] > 0) {
                    Text(
                        "Busiest hour: %02d:00 – %02d:59 (%d counts)".format(peak, peak, day.hours[peak]),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text("Tap for the hour-by-hour chart", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
