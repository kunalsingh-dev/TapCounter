package com.example.tapcounter.counter

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.tapcounter.data.HistoryStore
import com.example.tapcounter.data.MalaMath
import com.example.tapcounter.data.ResetEvent
import com.example.tapcounter.data.ResetType
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** [locked] = the calculator disguise is showing instead of the counter. Never saved to disk. */
data class CounterState(val count: Int = 0, val stealth: Boolean = false, val locked: Boolean = false)

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
    /** Index into the background themes. */
    val bgTheme: Int = 0,
    val bgImageVersion: Int = 0,
    /** Show a working calculator when leaving stealth and when the app starts; the code opens the counter. */
    val calcLock: Boolean = true,
    val calcCode: String = DEFAULT_CALC_CODE,
    /** Seconds the four/five-finger hold must last to switch stealth. */
    val stealthHoldSec: Int = 5,
) {
    companion object {
        const val DEFAULT_GOD_NAME = "राधावल्लभ\nश्रीहरिवंश"
        const val DEFAULT_CALC_CODE = "1234"
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

    private val handler = Handler(Looper.getMainLooper())
    private var saveQueued = false
    private val saveRunnable = Runnable {
        saveQueued = false
        writeState()
    }

    /** Counts per mala; kept in sync with the settings by TapCounterApp. */
    @Volatile
    var malaSize: Int = 108

    private val _display = MutableStateFlow(loadDisplay())
    val display: StateFlow<DisplayState> = _display.asStateFlow()

    private val _state = MutableStateFlow(
        CounterState(
            count = prefs.getInt(KEY_COUNT, 0),
            stealth = prefs.getBoolean(KEY_STEALTH, false),
            locked = _display.value.calcLock, // a fresh start shows the calculator first
        ),
    )
    val state: StateFlow<CounterState> = _state.asStateFlow()

    val godImageFile: File get() = File(appContext.filesDir, "god_image.jpg")
    val bgImageFile: File get() = File(appContext.filesDir, "bg_image.jpg")

    override fun increment(): Boolean {
        val n = _state.updateAndGet { it.copy(count = it.count + 1) }
        val completed = MalaMath.completesMala(n.count, malaSize)
        history.record(completed) // automatic date + hour log
        queueSave()
        return completed
    }

    override fun toggleStealth() = setStealth(!_state.value.stealth)
    override fun forceStealthOn() = setStealth(true)

    fun setStealth(on: Boolean) {
        // Leaving stealth swaps the counter for the calculator disguise (if enabled).
        _state.update { it.copy(stealth = on, locked = it.locked || (it.stealth && !on && _display.value.calcLock)) }
        queueSave()
    }

    /** Called when the secret code was entered on the calculator. */
    fun unlock() {
        _state.update { it.copy(locked = false) }
    }

    /** Show the calculator right now (menu: "Hide"). */
    fun lockNow() {
        if (_display.value.calcLock) _state.update { it.copy(locked = true) }
    }

    /**
     * Reset ONLY the current mala: the ring goes back to 0, completed malas, totals and history
     * stay. Logged with date and time. UI-only (Settings, confirmed); not reachable from headset code.
     */
    fun resetCurrentMala(now: LocalDateTime = LocalDateTime.now()) {
        val count = _state.value.count
        val ring = MalaMath.displayCount(count, malaSize)
        val sum = history.summary()
        history.addReset(ResetEvent(stamp(now), ResetType.MALA, ring, sum.todayTotal, sum.allTime, sum.allMalas))
        _state.update { it.copy(count = count - ring) }
        flush()
    }

    /**
     * Reset EVERYTHING shown: ring, today's numbers, all-time count and all malas go to 0. The
     * numbers at that moment are saved in the reset log (date + time) and the day-by-day
     * records stay in History. UI-only (Settings, confirmed).
     */
    fun resetAll(now: LocalDateTime = LocalDateTime.now()) {
        val ring = MalaMath.displayCount(_state.value.count, malaSize)
        val sum = history.summary()
        history.addReset(ResetEvent(stamp(now), ResetType.ALL, ring, sum.todayTotal, sum.allTime, sum.allMalas))
        history.markAllReset()
        _state.update { it.copy(count = 0) }
        flush()
    }

    /** Write everything to disk now (called when the app goes to the background or the service stops). */
    fun flush() {
        handler.removeCallbacks(saveRunnable)
        saveQueued = false
        writeState()
        history.flush()
    }

    private fun stamp(now: LocalDateTime): String = now.withNano(0).toString()

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
            .putInt(KEY_BG_THEME, d.bgTheme)
            .putInt(KEY_BG_IMAGE_VERSION, d.bgImageVersion)
            .putBoolean(KEY_CALC_LOCK, d.calcLock)
            .putString(KEY_CALC_CODE, d.calcCode)
            .putInt(KEY_STEALTH_HOLD, d.stealthHoldSec)
            .apply()
    }

    /** Batched write (at most every 250 ms) so rapid counting never waits for storage. */
    private fun queueSave() {
        if (saveQueued) return
        saveQueued = true
        handler.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    private fun writeState() {
        val n = _state.value
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
        bgTheme = prefs.getInt(KEY_BG_THEME, 0),
        bgImageVersion = prefs.getInt(KEY_BG_IMAGE_VERSION, 0),
        calcLock = prefs.getBoolean(KEY_CALC_LOCK, true),
        calcCode = prefs.getString(KEY_CALC_CODE, DisplayState.DEFAULT_CALC_CODE) ?: DisplayState.DEFAULT_CALC_CODE,
        stealthHoldSec = prefs.getInt(KEY_STEALTH_HOLD, 5),
    )

    private companion object {
        const val SAVE_DELAY_MS = 250L
        const val KEY_COUNT = "count"
        const val KEY_STEALTH = "stealth"
        const val KEY_GOD_NAME = "god_name"
        const val KEY_GOD_COLOR = "god_color"
        const val KEY_GOD_SIZE = "god_size"
        const val KEY_IMAGE_VERSION = "image_version"
        const val KEY_STEALTH_BRIGHTNESS = "stealth_brightness"
        const val KEY_TAP_ANYWHERE = "tap_anywhere"
        const val KEY_HIDE_BARS = "hide_bars_in_stealth"
        const val KEY_BG_THEME = "bg_theme"
        const val KEY_BG_IMAGE_VERSION = "bg_image_version"
        const val KEY_CALC_LOCK = "calc_lock"
        const val KEY_CALC_CODE = "calc_code"
        const val KEY_STEALTH_HOLD = "stealth_hold_sec"
    }
}
