package com.example.tapcounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Content never gets wider than this, so tablets and landscape phones stay tidy. */
private val MaxContentWidth = 640.dp

/** Header + width limit, no scrolling (for screens that bring their own LazyColumn). */
@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}

/** Header + width limit + vertical scrolling. */
@Composable
fun ScreenPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ScreenFrame(title, onBack) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Tiles in 2 columns on phones, 3 on wider screens. */
@Composable
fun StatGrid(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 480.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(columns).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Equal-width buttons; [selected] = -1 means none. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            if (i == selected) {
                Button(onClick = {}, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(label, maxLines = 1)
                }
            } else {
                OutlinedButton(onClick = { onSelect(i) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, background: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/**
 * Bar chart with grid lines, the maximum value in the corner and one label slot per bar
 * (blank labels are fine, so long ranges can label only some bars).
 */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp,
    highlightLast: Boolean = false,
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val n = values.size
                if (n == 0) return@Canvas
                for (f in listOf(0f, 0.5f, 1f)) {
                    val y = size.height - size.height * f
                    drawLine(color = grid, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                }
                val cell = size.width / n
                val gap = if (n > 16) 2.dp.toPx() else 6.dp.toPx()
                val barW = (cell - gap).coerceAtLeast(1f)
                values.forEachIndexed { i, v ->
                    val h = if (v > 0) maxOf(size.height * v / max, 3f) else 1.5f
                    drawRect(
                        color = if (highlightLast && i == n - 1) color else color.copy(alpha = 0.7f),
                        topLeft = Offset(i * cell + gap / 2f, size.height - h),
                        size = Size(barW, h),
                    )
                }
            }
            Text("$max", fontSize = 10.sp, color = labelColor, modifier = Modifier.align(Alignment.TopStart))
        }
        if (labels.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                labels.forEach { l ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            l,
                            fontSize = 10.sp,
                            color = labelColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun IntSlider(label: String, value: Int, range: IntRange, unit: String, onCommit: (Int) -> Unit) {
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

fun formatDay(date: String): String =
    runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) }.getOrDefault(date)

fun formatShortDay(date: String): String =
    runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM")) }.getOrDefault(date)

fun formatStamp(iso: String): String =
    runCatching { LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")) }
        .getOrElse { iso.replace('T', ' ') }
