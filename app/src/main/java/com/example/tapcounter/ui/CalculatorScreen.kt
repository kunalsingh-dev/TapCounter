package com.example.tapcounter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tapcounter.calc.CalculatorEngine

private val KeyDark = Color(0xFF333333)
private val KeyLight = Color(0xFFA5A5A5)
private val KeyOrange = Color(0xFFFF9F0A)

/**
 * A fully working calculator. Type the secret code and press "=" to open the counter;
 * anything else just calculates, so it looks like an ordinary calculator to anyone else.
 */
@Composable
fun CalculatorScreen(code: String, onUnlock: () -> Unit) {
    val engine = remember { CalculatorEngine() }
    var display by remember { mutableStateOf(engine.display) }

    fun press(action: () -> Unit) {
        action()
        display = engine.display
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(12.dp),
    ) {
    // Keys scale with the screen: 5 rows must fit, and the display keeps at least a third of the height.
    val keyHeight: Dp = ((maxHeight * 0.62f - 48.dp) / 5).coerceIn(44.dp, 84.dp)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
            Text(
                text = display,
                color = Color.White,
                fontSize = if (display.length > 9) 48.sp else 84.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalcKey(keyHeight, "AC", KeyLight, Color.Black) { press { engine.clear() } }
            CalcKey(keyHeight, "±", KeyLight, Color.Black) { press { engine.negate() } }
            CalcKey(keyHeight, "%", KeyLight, Color.Black) { press { engine.percent() } }
            CalcKey(keyHeight, "÷", KeyOrange) { press { engine.operator('÷') } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalcKey(keyHeight, "7", KeyDark) { press { engine.digit(7) } }
            CalcKey(keyHeight, "8", KeyDark) { press { engine.digit(8) } }
            CalcKey(keyHeight, "9", KeyDark) { press { engine.digit(9) } }
            CalcKey(keyHeight, "×", KeyOrange) { press { engine.operator('×') } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalcKey(keyHeight, "4", KeyDark) { press { engine.digit(4) } }
            CalcKey(keyHeight, "5", KeyDark) { press { engine.digit(5) } }
            CalcKey(keyHeight, "6", KeyDark) { press { engine.digit(6) } }
            CalcKey(keyHeight, "−", KeyOrange) { press { engine.operator('−') } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalcKey(keyHeight, "1", KeyDark) { press { engine.digit(1) } }
            CalcKey(keyHeight, "2", KeyDark) { press { engine.digit(2) } }
            CalcKey(keyHeight, "3", KeyDark) { press { engine.digit(3) } }
            CalcKey(keyHeight, "+", KeyOrange) { press { engine.operator('+') } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalcKey(keyHeight, "0", KeyDark, flex = 2.1f) { press { engine.digit(0) } }
            CalcKey(keyHeight, ".", KeyDark) { press { engine.dot() } }
            CalcKey(keyHeight, "=", KeyOrange) {
                if (engine.secretMatches(code)) {
                    onUnlock()
                } else {
                    press { engine.equalsKey() }
                }
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
    foreground: Color = Color.White,
    flex: Float = 1f,
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
