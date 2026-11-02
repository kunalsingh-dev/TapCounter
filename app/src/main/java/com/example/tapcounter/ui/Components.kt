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
