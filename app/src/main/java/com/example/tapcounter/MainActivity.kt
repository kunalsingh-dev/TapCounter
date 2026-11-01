package com.example.tapcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.tapcounter.ui.CounterScreen
import com.example.tapcounter.ui.LearnScreen
import com.example.tapcounter.ui.SettingsScreen
import com.example.tapcounter.ui.TapCounterTheme

enum class Screen { COUNTER, SETTINGS, LEARN }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = TapCounterApp.from(this)
        setContent {
            TapCounterTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot(app) }
            }
        }
    }
}

@Composable
fun AppRoot(app: TapCounterApp) {
    var screen by rememberSaveable { mutableStateOf(Screen.COUNTER) }

    BackHandler(enabled = screen != Screen.COUNTER) {
        screen = if (screen == Screen.LEARN) Screen.SETTINGS else Screen.COUNTER
    }

    when (screen) {
        Screen.COUNTER -> CounterScreen(app, onOpenSettings = { screen = Screen.SETTINGS })
        Screen.SETTINGS -> SettingsScreen(
            app,
            onBack = { screen = Screen.COUNTER },
            onLearn = { screen = Screen.LEARN },
        )
        Screen.LEARN -> LearnScreen(app, onDone = { screen = Screen.SETTINGS })
    }
}
