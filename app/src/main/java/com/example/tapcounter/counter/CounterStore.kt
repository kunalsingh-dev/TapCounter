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

/**
 * Look-and-feel and behaviour settings. [godName] and [imageVersion] belong to the ACTIVE counter;
 * everything else is shared by all counters.
 */
data class DisplayState(
    val godName: String = DEFAULT_GOD_NAME,
    val godColorIndex: Int = 0,
    val godSizeSp: Int = 56,
    /** Bumped whenever the picture file changes, so the UI reloads it. */
    val imageVersion: Int = 0,
    /** Screen brightness while in stealth with the BLACK screen, percent (1 = dimmest). */
    val stealthBrightnessPct: Int = 1,
    val tapAnywhere: Boolean = true,
    val hideBars: Boolean = true,
    /** Index into the background themes. */
    val bgTheme: Int = 0,
    val bgImageVersion: Int = 0,
    /** Show a working calculator when leaving stealth and when the app starts; the code opens the counter. */
    val calcLock: Boolean = true,
    /** Secret code of the MAIN counter (other counters have their own). */
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
    /** Face re-confirmation: hide again if my face was not matched for this many seconds (0 = off). */
    val faceReconfirmSec: Int = 0,
    /** What the stealth screen looks like: 0 black, 1 alarm, 2 clock, 3 update, 4 charging. */
    val disguise: Int = 0,
    /** Brightness for the disguise templates (not the black screen), percent. */
    val disguiseBrightnessPct: Int = 20,
    /** LCD / IPS screen: the black screen uses the dimmest brightness the phone allows. */
    val lcdMode: Boolean = false,
    /** Allow the phone's own strong biometric (long-press the calculator number). */
    val biometricUnlock: Boolean = false,
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

/**
 * Several counters in one app. The MAIN counter keeps its data in the original files ("counter",
 * "history"), so nothing is lost when upgrading; every extra counter has its own "counter_<id>" /
 * "history_<id>" files. Switching reloads the same state flows, so the screens do not change.
 */
class CounterStore(context: Context) : CounterActions {
    private val appContext = context.applicationContext

    /** Shared settings + the MAIN counter's own values. */
    private val prefs = appContext.getSharedPreferences("counter", Context.MODE_PRIVATE)
    private val profilePrefs = appContext.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    private val handler = Handler(Looper.getMainLooper())
    private var saveQueued = false
    private val saveRunnable = Runnable {
        saveQueued = false
        writeState()
    }

    private var extraProfiles: List<CounterProfile> = loadExtraProfiles()

    private val _activeId = MutableStateFlow(loadActiveId())
    /** Id of the counter that is open now. */
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    val history = HistoryStore(appContext, historyPrefsName(_activeId.value))

    /** Counts per mala; kept in sync with the settings by TapCounterApp. */
    @Volatile
    var malaSize: Int = 108

    private val _display = MutableStateFlow(loadDisplay(_activeId.value))
    val display: StateFlow<DisplayState> = _display.asStateFlow()

    private val _state = MutableStateFlow(
        CounterState(
            count = perPrefs(_activeId.value).getInt(KEY_COUNT, 0),
            stealth = prefs.getBoolean(KEY_STEALTH, false),
            locked = _display.value.calcLock, // a fresh start shows the calculator first
        ),
    )
    val state: StateFlow<CounterState> = _state.asStateFlow()

    private val _counters = MutableStateFlow(buildCounters())
    /** All counters, MAIN first, with the secret code of each. */
    val counters: StateFlow<List<CounterProfile>> = _counters.asStateFlow()

    val godImageFile: File get() = imageFile(_activeId.value)
    val bgImageFile: File get() = File(appContext.filesDir, "bg_image.jpg")

    private val malaTimer = MalaTimer().also { it.restore(perPrefs(_activeId.value).getLong(KEY_MALA_ACTIVE_MS, 0L)) }

    @Volatile
    private var lastIncrementAt = 0L

    // ------------------------------------------------------------------ counting

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

    /** Called when the face/biometric check passed: opens the counter that was open last. */
    fun unlock() {
        _state.update { it.copy(locked = false) }
    }

    /** Called when a secret code was typed on the calculator: opens THAT counter. */
    fun unlockCounter(id: String) {
        switchTo(id)
        unlock()
    }

    /** Show the calculator right now (menu: "Hide"). */
    fun lockNow() {
        if (_display.value.calcLock) _state.update { it.copy(locked = true) }
    }

    // ------------------------------------------------------------------ several counters

    /** Makes [id] the open counter (count, history, name and picture all switch). */
    fun switchTo(id: String) {
        if (id == _activeId.value) return
        if (id != CounterProfiles.MAIN_ID && extraProfiles.none { it.id == id }) return
        flush() // save the counter we are leaving
        _activeId.value = id
        profilePrefs.edit().putString(KEY_ACTIVE, id).apply()
        history.reload(historyPrefsName(id))
        val p = perPrefs(id)
        _state.update { it.copy(count = p.getInt(KEY_COUNT, 0)) }
        _display.update {
            it.copy(
                godName = p.getString(KEY_GOD_NAME, defaultNameFor(id)) ?: defaultNameFor(id),
                imageVersion = p.getInt(KEY_IMAGE_VERSION, 0),
            )
        }
        malaTimer.restore(p.getLong(KEY_MALA_ACTIVE_MS, 0L))
        lastIncrementAt = 0L
    }

    /** @return an error message, or null when the counter was added. */
    fun addCounter(name: String, code: String): String? {
        CounterProfiles.codeError(_counters.value, code, null)?.let { return it }
        val id = CounterProfiles.newId(_counters.value)
        val finalName = name.trim().ifEmpty { "Counter ${_counters.value.size + 1}" }
        extraProfiles = extraProfiles + CounterProfile(id, finalName, code)
        saveProfiles()
        perPrefs(id).edit().putString(KEY_GOD_NAME, finalName).apply()
        refreshCounters()
        return null
    }

    fun renameCounter(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        if (id == CounterProfiles.MAIN_ID) {
            profilePrefs.edit().putString(KEY_MAIN_NAME, clean).apply()
        } else {
            extraProfiles = extraProfiles.map { if (it.id == id) it.copy(name = clean) else it }
            saveProfiles()
        }
        refreshCounters()
    }

    /** @return an error message, or null when the code was changed. */
    fun setCounterCode(id: String, code: String): String? {
        CounterProfiles.codeError(_counters.value, code, id)?.let { return it }
        if (id == CounterProfiles.MAIN_ID) {
            updateDisplay { it.copy(calcCode = code) }
        } else {
            extraProfiles = extraProfiles.map { if (it.id == id) it.copy(code = code) else it }
            saveProfiles()
            refreshCounters()
        }
        return null
    }

    /** Deletes a counter and ALL its data. The main counter cannot be deleted. */
    fun deleteCounter(id: String) {
        if (id == CounterProfiles.MAIN_ID || extraProfiles.none { it.id == id }) return
        if (_activeId.value == id) switchTo(CounterProfiles.MAIN_ID)
        extraProfiles = extraProfiles.filter { it.id != id }
        saveProfiles()
        perPrefs(id).edit().clear().apply()
        appContext.getSharedPreferences(historyPrefsName(id), Context.MODE_PRIVATE).edit().clear().apply()
        imageFile(id).delete()
        refreshCounters()
    }

    // ------------------------------------------------------------------ resets (UI only)

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

    // ------------------------------------------------------------------ settings

    fun updateDisplay(transform: (DisplayState) -> DisplayState) {
        _display.update(transform)
        val d = _display.value
        // Shared settings
        prefs.edit()
            .putInt(KEY_GOD_COLOR, d.godColorIndex)
            .putInt(KEY_GOD_SIZE, d.godSizeSp)
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
            .putInt(KEY_FACE_RECONFIRM, d.faceReconfirmSec)
            .putInt(KEY_DISGUISE, d.disguise)
            .putInt(KEY_DISGUISE_BRIGHTNESS, d.disguiseBrightnessPct)
            .putBoolean(KEY_LCD_MODE, d.lcdMode)
            .putBoolean(KEY_BIOMETRIC, d.biometricUnlock)
            .apply()
        // Name and picture belong to the open counter
        perPrefs(_activeId.value).edit()
            .putString(KEY_GOD_NAME, d.godName)
            .putInt(KEY_IMAGE_VERSION, d.imageVersion)
            .apply()
        refreshCounters() // the main counter's code may have changed
    }

    // ------------------------------------------------------------------ internals

    private fun stamp(now: LocalDateTime): String = now.withNano(0).toString()

    /** Batched write (at most every 250 ms) so rapid counting never waits for storage. */
    private fun queueSave() {
        if (saveQueued) return
        saveQueued = true
        handler.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    private fun writeState() {
        val n = _state.value
        perPrefs(_activeId.value).edit()
            .putInt(KEY_COUNT, n.count)
            .putLong(KEY_MALA_ACTIVE_MS, malaTimer.activeMs)
            .apply()
        prefs.edit().putBoolean(KEY_STEALTH, n.stealth).apply()
    }

    private fun perPrefs(id: String) =
        appContext.getSharedPreferences(if (id == CounterProfiles.MAIN_ID) "counter" else "counter_$id", Context.MODE_PRIVATE)

    private fun historyPrefsName(id: String) = if (id == CounterProfiles.MAIN_ID) "history" else "history_$id"

    private fun imageFile(id: String) =
        File(appContext.filesDir, if (id == CounterProfiles.MAIN_ID) "god_image.jpg" else "god_image_$id.jpg")

    private fun defaultNameFor(id: String): String =
        if (id == CounterProfiles.MAIN_ID) DisplayState.DEFAULT_GOD_NAME
        else extraProfiles.firstOrNull { it.id == id }?.name ?: DisplayState.DEFAULT_GOD_NAME

    private fun loadExtraProfiles(): List<CounterProfile> =
        CounterProfiles.decodeOrder(profilePrefs.getString(KEY_ORDER, "") ?: "").mapNotNull { id ->
            profilePrefs.getString("$KEY_PROFILE_PREFIX$id", null)?.let { CounterProfiles.decodeEntry(id, it) }
        }

    private fun saveProfiles() {
        val editor = profilePrefs.edit()
        // remove stale entries, then write the current ones
        for (key in profilePrefs.all.keys) if (key.startsWith(KEY_PROFILE_PREFIX)) editor.remove(key)
        for (p in extraProfiles) editor.putString("$KEY_PROFILE_PREFIX${p.id}", CounterProfiles.encodeEntry(p))
        editor.putString(KEY_ORDER, CounterProfiles.encodeOrder(extraProfiles.map { it.id }))
        editor.apply()
    }

    private fun loadActiveId(): String {
        val saved = profilePrefs.getString(KEY_ACTIVE, CounterProfiles.MAIN_ID) ?: CounterProfiles.MAIN_ID
        return if (saved == CounterProfiles.MAIN_ID || extraProfiles.any { it.id == saved }) saved else CounterProfiles.MAIN_ID
    }

    private fun buildCounters(): List<CounterProfile> {
        val mainName = profilePrefs.getString(KEY_MAIN_NAME, "Main") ?: "Main"
        return listOf(CounterProfile(CounterProfiles.MAIN_ID, mainName, _display.value.calcCode)) + extraProfiles
    }

    private fun refreshCounters() {
        _counters.value = buildCounters()
    }

    private fun loadDisplay(activeId: String): DisplayState {
        val own = perPrefs(activeId)
        return DisplayState(
            godName = own.getString(KEY_GOD_NAME, defaultNameFor(activeId)) ?: defaultNameFor(activeId),
            godColorIndex = prefs.getInt(KEY_GOD_COLOR, 0),
            godSizeSp = prefs.getInt(KEY_GOD_SIZE, 56),
            imageVersion = own.getInt(KEY_IMAGE_VERSION, 0),
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
            faceReconfirmSec = prefs.getInt(KEY_FACE_RECONFIRM, 0),
            disguise = prefs.getInt(KEY_DISGUISE, 0),
            disguiseBrightnessPct = prefs.getInt(KEY_DISGUISE_BRIGHTNESS, 20),
            lcdMode = prefs.getBoolean(KEY_LCD_MODE, false),
            biometricUnlock = prefs.getBoolean(KEY_BIOMETRIC, false),
        )
    }

    private companion object {
        const val SAVE_DELAY_MS = 250L
        const val UNDO_MAX_MS = 400L
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
        const val KEY_FACE_RECONFIRM = "face_reconfirm"
        const val KEY_DISGUISE = "disguise"
        const val KEY_DISGUISE_BRIGHTNESS = "disguise_brightness"
        const val KEY_LCD_MODE = "lcd_mode"
        const val KEY_BIOMETRIC = "biometric_unlock"
        const val KEY_ACTIVE = "active"
        const val KEY_ORDER = "order"
        const val KEY_MAIN_NAME = "main_name"
        const val KEY_PROFILE_PREFIX = "p:"
    }
}
