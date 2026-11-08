package com.example.tapcounter.counter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.tapcounter.data.HistoryStore
import com.example.tapcounter.data.MalaMath
import com.example.tapcounter.data.MalaRecord
import com.example.tapcounter.data.MalaTimer
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
    /** Milliseconds the four/five-finger hold must last to switch stealth. */
    val stealthHoldMs: Int = 1000,
    /** Touch speed: a screen tap closer than this to the previous counted tap is ignored (0 = every touch). */
    val tapMinGapMs: Int = 0,
    /** Daily goal in malas (0 = off). */
    val dailyGoalMalas: Int = 0,
    /** Custom background colours (ARGB), used when [bgTheme] == custom (= number of built-in themes). */
    val customTop: Int = 0xFF4BA591.toInt(),
    val customBottom: Int = 0xFF4E5E86.toInt(),
    /** Face unlock: my face opens the counter, other faces keep the calculator. */
    val faceUnlock: Boolean = false,
    /** Cosine-similarity threshold in percent (higher = stricter). */
    val faceThresholdPct: Int = 50,
    /** While the counter is open, another face switches back to the calculator. */
    val faceWatch: Boolean = true,
    /** Two or more faces in view count as a stranger. */
    val faceLockOnMultiple: Boolean = true,
    /** Optional anti-photo check: blink to unlock. */
    val faceRequireBlink: Boolean = false,
) {
    companion object {
        const val DEFAULT_GOD_NAME = "राधावल्लभ\nश्रीहरिवंश"
        const val DEFAULT_CALC_CODE = "1234"
    }
}

/** What one count did. GOAL = it completed the mala that met today's goal. */
enum class CountResult { COUNTED, MALA, GOAL }

/**
 * The ONLY surface the headset layer can touch. There is no reset() and no decrement(), so a
 * headphone/volume button can never reset or reduce the counter, even through a coding mistake.
 */
interface CounterActions {
    fun increment(): CountResult
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

    private val malaTimer = MalaTimer().also { it.restore(prefs.getLong(KEY_MALA_ACTIVE_MS, 0L)) }

    override fun increment(): CountResult {
        val n = _state.updateAndGet { it.copy(count = it.count + 1) }
        val completed = MalaMath.completesMala(n.count, malaSize)
        history.record(completed) // automatic date + hour log
        lastIncrementAt = SystemClock.uptimeMillis()
        malaTimer.onCount(SystemClock.elapsedRealtime())
        queueSave()
        if (!completed) return CountResult.COUNTED

        // A mala just finished: remember how long it took (mala speed) and check the daily goal.
        val seconds = ((malaTimer.finishMala() + 500L) / 1000L).toInt()
        history.addMalaRecord(MalaRecord(LocalDateTime.now().withNano(0).toString(), seconds))
        val goal = _display.value.dailyGoalMalas
        return if (goal > 0 && history.summary().todayMalas == goal) CountResult.GOAL else CountResult.MALA
    }

    @Volatile
    private var lastIncrementAt = 0L

    /**
     * Takes back the count a multi-finger touch made by accident (4 fingers landing for stealth).
     * Only works within 400 ms of the count and is called only from the screen gesture code,
     * never from headset or volume buttons.
     */
    fun undoLastCount() {
        if (SystemClock.uptimeMillis() - lastIncrementAt > UNDO_MAX_MS) return
        lastIncrementAt = 0L
        if (_state.value.count <= 0) return
        _state.update { it.copy(count = it.count - 1) }
        history.undoLast()
        queueSave()
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
        malaTimer.reset()
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
        malaTimer.reset()
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
            .putInt(KEY_STEALTH_HOLD_MS, d.stealthHoldMs)
            .putInt(KEY_TAP_MIN_GAP, d.tapMinGapMs)
            .putInt(KEY_DAILY_GOAL, d.dailyGoalMalas)
            .putInt(KEY_CUSTOM_TOP, d.customTop)
            .putInt(KEY_CUSTOM_BOTTOM, d.customBottom)
            .putBoolean(KEY_FACE_UNLOCK, d.faceUnlock)
            .putInt(KEY_FACE_THRESHOLD, d.faceThresholdPct)
            .putBoolean(KEY_FACE_WATCH, d.faceWatch)
            .putBoolean(KEY_FACE_MULTI, d.faceLockOnMultiple)
            .putBoolean(KEY_FACE_BLINK, d.faceRequireBlink)
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
        prefs.edit()
            .putInt(KEY_COUNT, n.count)
            .putBoolean(KEY_STEALTH, n.stealth)
            .putLong(KEY_MALA_ACTIVE_MS, malaTimer.activeMs)
            .apply()
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
        stealthHoldMs = prefs.getInt(KEY_STEALTH_HOLD_MS, 1000),
        tapMinGapMs = prefs.getInt(KEY_TAP_MIN_GAP, 0),
        dailyGoalMalas = prefs.getInt(KEY_DAILY_GOAL, 0),
        customTop = prefs.getInt(KEY_CUSTOM_TOP, 0xFF4BA591.toInt()),
        customBottom = prefs.getInt(KEY_CUSTOM_BOTTOM, 0xFF4E5E86.toInt()),
        faceUnlock = prefs.getBoolean(KEY_FACE_UNLOCK, false),
        faceThresholdPct = prefs.getInt(KEY_FACE_THRESHOLD, 50),
        faceWatch = prefs.getBoolean(KEY_FACE_WATCH, true),
        faceLockOnMultiple = prefs.getBoolean(KEY_FACE_MULTI, true),
        faceRequireBlink = prefs.getBoolean(KEY_FACE_BLINK, false),
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
        const val KEY_STEALTH_HOLD_MS = "stealth_hold_ms"
        const val UNDO_MAX_MS = 400L
        const val KEY_MALA_ACTIVE_MS = "mala_active_ms"
        const val KEY_TAP_MIN_GAP = "tap_min_gap"
        const val KEY_DAILY_GOAL = "daily_goal"
        const val KEY_CUSTOM_TOP = "custom_top"
        const val KEY_CUSTOM_BOTTOM = "custom_bottom"
        const val KEY_FACE_UNLOCK = "face_unlock"
        const val KEY_FACE_THRESHOLD = "face_threshold"
        const val KEY_FACE_WATCH = "face_watch"
        const val KEY_FACE_MULTI = "face_multi"
        const val KEY_FACE_BLINK = "face_blink"
    }
}
