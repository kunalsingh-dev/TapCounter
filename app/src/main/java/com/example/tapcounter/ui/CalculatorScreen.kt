package com.example.tapcounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapcounter.calc.CalculatorEngine
import com.example.tapcounter.counter.CounterProfile

private val KeyDark = Color(0xFF333333)
private val KeyLight = Color(0xFFA5A5A5)
private val KeyOrange = Color(0xFFFF9F0A)

private val KeyRows = listOf(
    listOf("AC", "±", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "="),
)

/**
 * A fully working calculator that adapts to portrait, landscape and small screens. Type a counter's
 * secret code and press "=" to open THAT counter; anything else just calculates, so it looks like an
 * ordinary calculator to anyone else. Long-pressing the number is a hidden way in with the phone's
 * own face/fingerprint (only when allowed in Settings).
 */
@Composable
fun CalculatorScreen(codes: List<CounterProfile>, onUnlock: (String) -> Unit, onBiometric: (() -> Unit)? = null) {
    val engine = remember { CalculatorEngine() }
    var display by remember { mutableStateOf(engine.display) }

    fun press(action: () -> Unit) {
        action()
        display = engine.display
    }

    fun onKey(label: String) {
        when (label) {
            "AC" -> press { engine.clear() }
            "±" -> press { engine.negate() }
            "%" -> press { engine.percent() }
            "÷", "×", "−", "+" -> press { engine.operator(label[0]) }
            "." -> press { engine.dot() }
            "=" -> {
                val hit = codes.firstOrNull { engine.secretMatches(it.code) }
                if (hit != null) onUnlock(hit.id) else press { engine.equalsKey() }
            }
            else -> label.toIntOrNull()?.let { digit -> press { engine.digit(digit) } }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(12.dp),
    ) {
        val availableHeight = maxHeight
        val landscape = maxWidth > availableHeight * 1.2f
        if (landscape) {
            // Number on the left, keys on the right; five rows share the full height.
            val gap = 8.dp
            val keyHeight = ((availableHeight - gap * 4) / 5).coerceIn(34.dp, 72.dp)
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DisplayBox(display, onBiometric, Modifier.weight(1f).fillMaxHeight(), availableHeight * 0.4f)
                Keypad(keyHeight, gap, { onKey(it) }, Modifier.weight(1.3f))
            }
        } else {
            val gap = 12.dp
            // Five rows must fit and the number keeps at least a third of the height.
            val keyHeight = ((availableHeight * 0.62f - gap * 4) / 5).coerceIn(44.dp, 84.dp)
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                DisplayBox(display, onBiometric, Modifier.weight(1f).fillMaxWidth(), availableHeight * 0.22f)
                Keypad(keyHeight, gap, { onKey(it) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DisplayBox(text: String, onBiometric: (() -> Unit)?, modifier: Modifier, maxFont: Dp) {
    // Longer numbers get smaller type so they always fit on one line.
    val base = when {
        text.length <= 7 -> 84f
        text.length <= 9 -> 64f
        text.length <= 12 -> 48f
        else -> 38f
    }
    val size = minOf(base, maxFont.value * 0.9f).coerceAtLeast(28f)
    Box(
        modifier = modifier.pointerInput(onBiometric != null) { detectTapGestures(onLongPress = { onBiometric?.invoke() }) },
        contentAlignment = Alignment.BottomEnd,
    ) {
        Text(text = text, color = Color.White, fontSize = size.sp, fontWeight = FontWeight.Light, maxLines = 1)
    }
}

@Composable
private fun Keypad(keyHeight: Dp, gap: Dp, onKey: (String) -> Unit, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        KeyRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { label ->
                    val (bg, fg) = when {
                        label in listOf("÷", "×", "−", "+", "=") -> KeyOrange to Color.White
                        label in listOf("AC", "±", "%") -> KeyLight to Color.Black
                        else -> KeyDark to Color.White
                    }
                    CalcKey(keyHeight, label, bg, fg, flex = if (label == "0") 2.1f else 1f) { onKey(label) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalcKey(
    keyHeight: Dp,
    label: String,
    background: Color,
    foreground: Color,
    flex: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(flex)
            .height(keyHeight)
            .clip(RoundedCornerShape(keyHeight / 2))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = foreground, fontSize = (keyHeight.value * 0.4f).sp)
    }
}
