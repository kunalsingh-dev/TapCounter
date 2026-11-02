package com.example.tapcounter.counter

import android.content.Context
import com.example.tapcounter.data.HistoryStore
import com.example.tapcounter.data.MalaMath
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

data class CounterState(val count: Int = 0, val stealth: Boolean = false)

/** Look-and-feel settings of the counter screen. */
data class DisplayState(
    val godName: String = DEFAULT_GOD_NAME,
    val godColorIndex: Int = 0,
    val godSizeSp: Int = 56,
    /** Bumped whenever the picture file changes, so the UI reloads it. */
    val imageVersion: Int = 0,
    /** Screen brightness while in stealth, percent (1 = dimmest). */
    val stealthBrightnessPct: Int = 1,
    val tapAnywhere: Boolean = true,
    val hideBars: Boolean = true,
) {
    companion object {
        const val DEFAULT_GOD_NAME = "राधावल्लभ\nश्रीहरिवंश"
    }
}

/**
 * The ONLY surface the headset layer can touch. There is no reset() and no decrement(), so a
 * headphone/volume button can never reset or reduce the counter, even through a coding mistake.
 */
interface CounterActions {
    /** @return true when this count completed a mala. */
    fun increment(): Boolean
    fun toggleStealth()
    fun forceStealthOn()
}

class CounterStore(context: Context) : CounterActions {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("counter", Context.MODE_PRIVATE)

    val history = HistoryStore(appContext)

    /** Counts per mala; kept in sync with the settings by TapCounterApp. */
    @Volatile
    var malaSize: Int = 108

    private val _state = MutableStateFlow(
        CounterState(prefs.getInt(KEY_COUNT, 0), prefs.getBoolean(KEY_STEALTH, false)),
    )
    val state: StateFlow<CounterState> = _state.asStateFlow()

    private val _display = MutableStateFlow(loadDisplay())
    val display: StateFlow<DisplayState> = _display.asStateFlow()

    val godImageFile: File get() = File(appContext.filesDir, "god_image.jpg")

    override fun increment(): Boolean {
        val n = _state.updateAndGet { it.copy(count = it.count + 1) }
        val completed = MalaMath.completesMala(n.count, malaSize)
        history.record(completed) // automatic date + hour log
        persist(n)
        return completed
    }

    override fun toggleStealth() = setStealth(!_state.value.stealth)
    override fun forceStealthOn() = setStealth(true)

    fun setStealth(on: Boolean) {
        persist(_state.updateAndGet { it.copy(stealth = on) })
    }

    /** UI-only (Settings, confirmed). Not reachable from headset code. History is kept. */
    fun resetCount() {
        persist(_state.updateAndGet { it.copy(count = 0) })
    }

    fun updateDisplay(transform: (DisplayState) -> DisplayState) {
        _display.update(transform)
        val d = _display.value
        prefs.edit()
            .putString(KEY_GOD_NAME, d.godName)
            .putInt(KEY_GOD_COLOR, d.godColorIndex)
            .putInt(KEY_GOD_SIZE, d.godSizeSp)
            .putInt(KEY_IMAGE_VERSION, d.imageVersion)
            .putInt(KEY_STEALTH_BRIGHTNESS, d.stealthBrightnessPct)
            .putBoolean(KEY_TAP_ANYWHERE, d.tapAnywhere)
            .putBoolean(KEY_HIDE_BARS, d.hideBars)
            .apply()
    }

    private fun persist(n: CounterState) {
        prefs.edit().putInt(KEY_COUNT, n.count).putBoolean(KEY_STEALTH, n.stealth).apply()
    }

    private fun loadDisplay() = DisplayState(
        godName = prefs.getString(KEY_GOD_NAME, DisplayState.DEFAULT_GOD_NAME) ?: DisplayState.DEFAULT_GOD_NAME,
        godColorIndex = prefs.getInt(KEY_GOD_COLOR, 0),
        godSizeSp = prefs.getInt(KEY_GOD_SIZE, 56),
        imageVersion = prefs.getInt(KEY_IMAGE_VERSION, 0),
        stealthBrightnessPct = prefs.getInt(KEY_STEALTH_BRIGHTNESS, 1),
        tapAnywhere = prefs.getBoolean(KEY_TAP_ANYWHERE, true),
        hideBars = prefs.getBoolean(KEY_HIDE_BARS, true),
    )

    private companion object {
        const val KEY_COUNT = "count"
        const val KEY_STEALTH = "stealth"
        const val KEY_GOD_NAME = "god_name"
        const val KEY_GOD_COLOR = "god_color"
        const val KEY_GOD_SIZE = "god_size"
        const val KEY_IMAGE_VERSION = "image_version"
        const val KEY_STEALTH_BRIGHTNESS = "stealth_brightness"
        const val KEY_TAP_ANYWHERE = "tap_anywhere"
        const val KEY_HIDE_BARS = "hide_bars_in_stealth"
    }
}
