package com.example.tapcounter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.TapCounterApp
import com.example.tapcounter.data.DayRecord

@Composable
fun HistoryScreen(app: TapCounterApp, onBack: () -> Unit) {
    val days by app.counterStore.history.days.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("History", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Saved automatically, by date and by hour. Tap a day to see the hours.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (days.isEmpty()) {
            Text("Nothing counted yet.", modifier = Modifier.padding(top = 16.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(days, key = { it.date }) { day -> DayCard(day) }
        }
    }
}

@Composable
private fun DayCard(day: DayRecord) {
    var expanded by remember(day.date) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(day.date, style = MaterialTheme.typography.titleMedium)
                Text("${day.total} counts · ${day.malas} mala")
            }
            if (expanded) {
                val active = day.hours.withIndex().filter { it.value > 0 }
                if (active.isEmpty()) Text("No counts.")
                active.forEach { (hour, n) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("%02d:00 – %02d:59".format(hour, hour))
                        Text(n.toString())
                    }
                }
            }
        }
    }
}
