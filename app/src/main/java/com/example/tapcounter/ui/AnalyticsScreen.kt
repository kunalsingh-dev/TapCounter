package com.example.tapcounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.data.Analytics
import com.example.tapcounter.data.MalaStats
import java.time.LocalDate

@Composable
fun AnalyticsScreen(app: TapCounterApp, onBack: () -> Unit) {
    val days by app.counterStore.history.days.collectAsStateWithLifecycle()
    val malaRecords by app.counterStore.history.malas.collectAsStateWithLifecycle()
    val display by app.counterStore.display.collectAsStateWithLifecycle()
    var range by remember { mutableIntStateOf(7) }

    val today = LocalDate.now()
    val points = remember(days, range) { Analytics.lastDays(days, range, today) }
    val hours = remember(days, range) { Analytics.hourTotals(days, range, today) }
    val last7 = remember(days) { Analytics.lastDays(days, 7, today) }
    val best = Analytics.best(points)
    val malaStats = remember(malaRecords) { MalaStats.summarize(malaRecords.map { it.seconds }) }
    val todayPoint = points.last()
    val goal = display.dailyGoalMalas

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Analytics", style = MaterialTheme.typography.headlineSmall)
        Text("Based on your saved history (resets do not delete it).", style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (range == 7) Button(onClick = {}) { Text("7 days") } else OutlinedButton(onClick = { range = 7 }) { Text("7 days") }
            if (range == 30) Button(onClick = {}) { Text("30 days") } else OutlinedButton(onClick = { range = 30 }) { Text("30 days") }
        }

        // ---- Daily counts ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Counts per day", style = MaterialTheme.typography.titleMedium)
                BarChart(points.map { it.total }, Color(0xFF4BA591), highlightLast = true)
                if (range == 7) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        points.forEach { p ->
                            Text(LocalDate.parse(p.date).dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(points.first().date.drop(5), style = MaterialTheme.typography.labelSmall)
                        Text(points.last().date.drop(5), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("Highest bar: ${points.maxOf { it.total }}", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Key numbers ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Summary ($range days)", style = MaterialTheme.typography.titleMedium)
                Text("Total: ${points.sumOf { it.total }} counts · ${points.sumOf { it.malas }} malas")
                Text("Average: ${Analytics.average(points)} per day")
                Text("Best day: " + (best?.let { "${it.date} (${it.total})" } ?: "none yet"))
                Text("Streak: ${Analytics.streak(days, today)} day(s) in a row")
            }
        }

        // ---- Hours of the day ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When you chant (hour of day)", style = MaterialTheme.typography.titleMedium)
                BarChart(hours, Color(0xFF5AA0B5), highlightLast = false)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("0", "6", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
                val peak = hours.indices.maxByOrNull { hours[it] }
                if (peak != null && hours[peak] > 0) {
                    Text("Busiest hour: %02d:00 – %02d:59".format(peak, peak), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---- Mala speed ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mala speed", style = MaterialTheme.typography.titleMedium)
                if (malaStats.count == 0) {
                    Text("Finish a mala to see how long it takes. Breaks longer than 2 minutes are not counted.")
                } else {
                    Text("Last: ${formatDuration(malaStats.lastSec ?: 0)}   ·   Average: ${formatDuration(malaStats.avgSec ?: 0)}")
                    Text("Fastest: ${formatDuration(malaStats.fastestSec ?: 0)}   ·   Malas timed: ${malaStats.count}")
                    BarChart(malaRecords.take(10).map { it.seconds }.reversed(), Color(0xFFFFC107), highlightLast = true)
                    Text("Last 10 malas, oldest to newest (taller = slower).", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---- Goal ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Daily goal", style = MaterialTheme.typography.titleMedium)
                if (goal <= 0) {
                    Text("No goal set. Set one in Settings → Counting.")
                } else {
                    Text("Today: ${todayPoint.malas} / $goal malas" + if (todayPoint.malas >= goal) "  ✓" else "")
                    Text("Goal met on ${Analytics.goalDaysMet(last7, goal)} of the last 7 days")
                }
            }
        }
    }
}

/** Simple bar chart drawn on a Canvas. */
@Composable
private fun BarChart(values: List<Int>, color: Color, highlightLast: Boolean) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
        val n = values.size
        if (n == 0) return@Canvas
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val h = if (v > 0) maxOf(size.height * v / max, 3f) else 1.5f
            drawRect(
                color = if (highlightLast && i == n - 1) color else color.copy(alpha = 0.65f),
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
            )
        }
    }
}
