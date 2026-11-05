package com.example.tapcounter

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tapcounter.ui.CalculatorScreen
import com.example.tapcounter.ui.CounterScreen
import com.example.tapcounter.ui.HistoryScreen
import com.example.tapcounter.ui.LearnScreen
import com.example.tapcounter.ui.SettingsScreen
import com.example.tapcounter.ui.TapCounterTheme

enum class Screen { COUNTER, SETTINGS, LEARN, HISTORY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark bars with light icons everywhere (the whole app is dark).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        val app = TapCounterApp.from(this)
        setContent {
            TapCounterTheme {
                Surface(modifier = Modifier.fillMaxSize()) { AppRoot(app) }
            }
        }
    }

    override fun onStop() {
        TapCounterApp.from(this).counterStore.flush()
        super.onStop()
    }

    // Phone volume buttons while the app is open. A held key repeats with repeatCount > 0
    // and is swallowed without counting; only a fresh press counts.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && TapCounterApp.from(this).headsetController.onVolumeKey(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && TapCounterApp.from(this).headsetController.onVolumeKey(event)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(code: Int) = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
}

@Composable
fun AppRoot(app: TapCounterApp) {
    var screen by rememberSaveable { mutableStateOf(Screen.COUNTER) }
    val counter by app.counterStore.state.collectAsStateWithLifecycle()
    val display by app.counterStore.display.collectAsStateWithLifecycle()

    // Calculator disguise: shown instead of the app until the secret code + "=" is entered.
    if (counter.locked) {
        CalculatorScreen(code = display.calcCode, onUnlock = { app.counterStore.unlock() })
        return
    }

    BackHandler(enabled = screen != Screen.COUNTER) {
        screen = if (screen == Screen.LEARN) Screen.SETTINGS else Screen.COUNTER
    }

    when (screen) {
        Screen.COUNTER -> CounterScreen(
            app,
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenHistory = { screen = Screen.HISTORY },
        )
        Screen.SETTINGS -> SettingsScreen(
            app,
            onBack = { screen = Screen.COUNTER },
            onLearn = { screen = Screen.LEARN },
        )
        Screen.LEARN -> LearnScreen(app, onDone = { screen = Screen.SETTINGS })
        Screen.HISTORY -> HistoryScreen(app, onBack = { screen = Screen.COUNTER })
    }
}
