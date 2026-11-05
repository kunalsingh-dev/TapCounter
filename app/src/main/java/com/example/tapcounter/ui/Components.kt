package com.example.tapcounter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.tapcounter.headset.Action
import com.example.tapcounter.headset.Gesture
import com.example.tapcounter.headset.HeadsetService

@Composable
fun TapCounterTheme(content: @Composable () -> Unit) {
    // Always dark: the counter screen has a dark top bar and a coloured gradient.
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

/** Choices for the god-name text colour (index is stored). */
val GodColors = listOf(
    Color(0xFF000000), Color(0xFFFF3B30), Color(0xFFFFFFFF), Color(0xFFFFC107), Color(0xFF1E3A8A),
)

fun Action.label(): String = when (this) {
    Action.INCREMENT -> "Count +1"
    Action.DECREMENT -> "(retired)"
    Action.STEALTH_TOGGLE -> "Toggle stealth"
    Action.STEALTH_FORCE_ON -> "Stealth ON (force)"
    Action.NONE -> "Do nothing"
    Action.RESET -> "Reset (not allowed)"
}

fun Gesture.label(): String = when (this) {
    Gesture.SINGLE -> "Single click"
    Gesture.DOUBLE -> "Double click"
    Gesture.TRIPLE -> "Triple click"
    Gesture.LONG_PRESS -> "Long press"
}

/** Dropdown that only offers headset-assignable actions (RESET is never listed). */
@Composable
fun ActionPicker(current: Action, onSelect: (Action) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded = true }) { Text(current.label()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Action.headsetActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label()) },
                    onClick = {
                        expanded = false
                        onSelect(action)
                    },
                )
            }
        }
    }
}

/** Returns a function that asks for the needed permissions (if any) and then starts the service. */
@Composable
fun rememberEnableHeadset(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Start regardless of the answer: the service works without them (less status info, hidden notification).
        HeadsetService.start(context)
    }
    return {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) HeadsetService.start(context) else launcher.launch(missing.toTypedArray())
    }
}

/** Counter background themes: gradient at 0% and at 100% of a mala (colour drifts as you count). */
data class BgTheme(val name: String, val top0: Color, val bottom0: Color, val top1: Color, val bottom1: Color)

val BgThemes = listOf(
    BgTheme("Mala", Color(0xFF4BA591), Color(0xFF4E5E86), Color(0xFF5AA0B5), Color(0xFF7A8EB0)),
    BgTheme("Saffron", Color(0xFFE8A23A), Color(0xFF9C4A1A), Color(0xFFF2C14E), Color(0xFFC4561F)),
    BgTheme("Midnight", Color(0xFF2B3A67), Color(0xFF111827), Color(0xFF5B4B8A), Color(0xFF1F1B3A)),
    BgTheme("Forest", Color(0xFF2E7D5B), Color(0xFF123524), Color(0xFF4CAF7A), Color(0xFF1B4D35)),
    BgTheme("Rose", Color(0xFFD6577A), Color(0xFF5C1A33), Color(0xFFEF7A9A), Color(0xFF7A2442)),
    BgTheme("Ocean", Color(0xFF2A7FB8), Color(0xFF0E2F4F), Color(0xFF3FB0D8), Color(0xFF164A73)),
)
