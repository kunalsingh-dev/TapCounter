package com.example.tapcounter.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var rangeIndex by remember { mutableIntStateOf(0) }
    val range = if (rangeIndex == 0) 7 else 30

    val today = LocalDate.now()
    val points = remember(days, range) { Analytics.lastDays(days, range, today) }
    val hours = remember(days, range) { Analytics.hourTotals(days, range, today) }
    val last7 = remember(days) { Analytics.lastDays(days, 7, today) }
    val best = Analytics.best(points)
    val malaStats = remember(malaRecords) { MalaStats.summarize(malaRecords.map { it.seconds }) }
    val goal = display.dailyGoalMalas
    val todayMalas = points.last().malas

    ScreenPage("Analytics", onBack) {
        Text(
            "Built from your saved history. Resets never delete it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedControl(listOf("Last 7 days", "Last 30 days"), rangeIndex) { rangeIndex = it }

        StatGrid(
            listOf(
                "Total counts" to points.sumOf { it.total }.toString(),
                "Malas completed" to points.sumOf { it.malas }.toString(),
                "Average per day" to Analytics.average(points).toString(),
                "Best day" to (best?.let { "${formatShortDay(it.date)} · ${it.total}" } ?: "–"),
                "Current streak" to "${Analytics.streak(days, today)} days",
                "Goal met" to if (goal > 0) "${Analytics.goalDaysMet(last7, goal)} / 7 days" else "No goal",
            ),
        )

        SectionCard("Counts per day", if (range == 7) "Each bar is one day. The tall number is the highest bar." else "Each bar is one day, oldest on the left.") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val chartHeight = if (maxWidth > 480.dp) 180.dp else 140.dp
                val labels = if (range == 7) {
                    points.map { formatDayInitial(it.date) }
                } else {
                    points.mapIndexed { i, p -> if (i % 7 == 0 || i == points.lastIndex) formatShortDay(p.date) else "" }
                }
                BarChart(points.map { it.total }, labels, Color(0xFF4BA591), chartHeight = chartHeight, highlightLast = true)
            }
        }

        SectionCard("When you chant", "Counts by hour of the day over the last $range days.") {
            BarChart(
                hours,
                List(24) { if (it % 6 == 0 || it == 23) it.toString() else "" },
                Color(0xFF5AA0B5),
            )
            val peak = hours.indices.maxByOrNull { hours[it] }
            Text(
                if (peak != null && hours[peak] > 0) "Busiest hour: %02d:00 – %02d:59 (%d counts)".format(peak, peak, hours[peak]) else "No counts in this range yet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Mala speed", "How long each mala took. Breaks longer than 2 minutes are not counted.") {
            if (malaStats.count == 0) {
                Text("Finish a mala to see your speed.")
            } else {
                StatGrid(
                    listOf(
                        "Last mala" to formatDuration(malaStats.lastSec ?: 0),
                        "Average" to formatDuration(malaStats.avgSec ?: 0),
                        "Fastest" to formatDuration(malaStats.fastestSec ?: 0),
                        "Malas timed" to malaStats.count.toString(),
                    ),
                )
                val lastTen = malaRecords.take(10).map { it.seconds }.reversed()
                BarChart(lastTen, lastTen.indices.map { (it + 1).toString() }, Color(0xFFFFC107), highlightLast = true)
                Text("Last ${lastTen.size} malas, oldest to newest. A taller bar means a slower mala.", style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard("Daily goal") {
            if (goal <= 0) {
                Text("No goal set. Choose one in Settings → Counting.")
            } else {
                StatGrid(
                    listOf(
                        "Today" to "$todayMalas / $goal malas",
                        "Status" to if (todayMalas >= goal) "Goal reached ✓" else "${goal - todayMalas} to go",
                    ),
                )
            }
        }
    }
}

private fun formatDayInitial(date: String): String =
    runCatching { LocalDate.parse(date).dayOfWeek.name.take(1) }.getOrDefault("")
