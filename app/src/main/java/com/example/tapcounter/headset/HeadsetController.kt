package com.example.tapcounter.headset

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.example.tapcounter.counter.CountResult
import com.example.tapcounter.counter.CounterActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Hub between every input (screen taps, headset keys, phone volume keys) and the counter.
 * All entry points run on the main thread.
 *
 * headset key -> [learn mode? capture] -> [hardware lock? swallow] -> C++ engine
 *             -> gesture -> settings lookup -> Action -> CounterActions + feedback
 * volume key  -> [lock?] -> (foreground: repeatCount==0 only) | (background: VolumeKeyFilter) -> +1
 * screen tap  -> +1
 */
class HeadsetController(
    context: Context,
    private val counter: CounterActions,
    private val repo: HeadsetSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val engine = NativeGestureEngine()
    private val feedback = Feedback(context.applicationContext)
    private val volumeFilter = VolumeKeyFilter()
    private val tapLimiter = TapRateLimiter()

    @Volatile
    private var settings = HeadsetSettings()

    private val _status = MutableStateFlow(HeadsetStatus())
    val status: StateFlow<HeadsetStatus> = _status.asStateFlow()

    private val _learn = MutableStateFlow(LearnState())
    val learn: StateFlow<LearnState> = _learn.asStateFlow()

    private val tickRunnable = Runnable {
        dispatch(engine.tick(SystemClock.uptimeMillis()))
        scheduleTick()
    }

    private val volumeRunnable = Runnable {
        commitCounts(volumeFilter.poll(SystemClock.uptimeMillis()))
        scheduleVolume()
    }

    init {
        scope.launch {
            repo.settings.collect { s ->
                settings = s
                engine.configure(s.gestureConfig)
                applyMaxTaps(s)
            }
        }
    }

    fun updateStatus(transform: (HeadsetStatus) -> HeadsetStatus) = _status.update(transform)

    // ---- Screen --------------------------------------------------------------------

    /**
     * One touch on the screen. [minGapMs] is the touch-speed setting: touches closer than that to
     * the previous counted one are ignored. Returns true if the touch was counted.
     */
    fun countTap(minGapMs: Long): Boolean {
        if (!tapLimiter.allow(SystemClock.uptimeMillis(), minGapMs)) return false
        countOne()
        return true
    }

    /** The two-finger-hold combination: toggle stealth with a distinct double buzz. */
    fun stealthComboFromScreen() {
        counter.toggleStealth()
        feedback.stealthForceOn(settings.vibrationEnabled, false)
    }

    // ---- Phone volume keys ---------------------------------------------------------

    /** Foreground path (Activity.onKeyDown/onKeyUp). Returns true if the key was consumed. */
    fun onVolumeKey(event: KeyEvent): Boolean {
        val s = settings
        if (!s.volumeKeysCount || s.hardwareLock) return false
        // A held key arrives with repeatCount > 0: swallow it, count only the first press.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) countOne()
        return true
    }

    /** Background path (MediaSession volume provider). Direction is +1 / -1 / 0. */
    fun onVolumeAdjust(direction: Int) {
        val s = settings
        if (direction == 0 || !s.volumeKeysCount || s.hardwareLock) return
        commitCounts(volumeFilter.onEvent(SystemClock.uptimeMillis()))
        scheduleVolume()
    }

    // ---- Learn mode ----------------------------------------------------------------

    fun startLearn() {
        resetGestureState()
        _learn.value = LearnState(active = true, captured = null)
    }

    fun stopLearn() {
        _learn.value = LearnState(active = false, captured = null)
    }

    // ---- Headset key path ----------------------------------------------------------

    /** Called from MediaSessionCompat.Callback.onMediaButtonEvent. Returns true if consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return true
        }
        val now = SystemClock.uptimeMillis()

        if (down) {
            _status.update {
                it.copy(lastKeyName = HeadsetKeys.name(code), lastKeyAtMs = now, possibleConflict = false)
            }
        }

        // Learn mode captures ANY key from ANY Bluetooth device and bypasses the hardware lock.
        if (_learn.value.active) {
            if (down && event.repeatCount == 0) {
                _learn.value = LearnState(active = false, captured = CapturedKey(code))
                feedback.count(vibrate = true, tick = false)
            }
            return true
        }

        val known = code in HeadsetKeys.supported || settings.bindings.keys.any { it.first == code }
        if (!known) return false
        if (settings.hardwareLock) return true // consumed, deliberately ignored

        dispatch(engine.onKey(code, down, now))
        scheduleTick()
        return true
    }

    fun resetGestureState() {
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(volumeRunnable)
        engine.reset()
        volumeFilter.reset()
    }

    fun release() {
        handler.removeCallbacks(tickRunnable)
        handler.removeCallbacks(volumeRunnable)
    }

    // ---- internals -----------------------------------------------------------------

    /**
     * A key with no double/triple binding is told maxTaps = 1, so its single click is counted
     * the instant it is released instead of after the multi-click window.
     */
    private fun applyMaxTaps(s: HeadsetSettings) {
        val keys = HeadsetKeys.supported + s.bindings.keys.map { it.first }
        for (key in keys) {
            val maxTaps = when {
                isBound(s.bindings[key to Gesture.TRIPLE]) -> 3
                isBound(s.bindings[key to Gesture.DOUBLE]) -> 2
                else -> 1
            }
            engine.setMaxTaps(key, maxTaps)
        }
    }

    private fun isBound(a: Action?): Boolean = a != null && a != Action.NONE

    private fun scheduleTick() {
        handler.removeCallbacks(tickRunnable)
        val deadline = engine.nextDeadlineMs()
        if (deadline >= 0) {
            handler.postDelayed(tickRunnable, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L) + 1)
        }
    }

    private fun scheduleVolume() {
        handler.removeCallbacks(volumeRunnable)
        val deadline = volumeFilter.nextDeadlineMs()
        if (deadline >= 0) {
            handler.postDelayed(volumeRunnable, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L) + 1)
        }
    }

    private fun commitCounts(n: Int) {
        repeat(n) { countOne() }
    }

    private fun countOne() {
        val s = settings
        when (counter.increment()) {
            CountResult.GOAL ->
                if (s.malaVibration) feedback.goal(true, s.confirmationTick)
                else feedback.count(s.vibrationEnabled, s.confirmationTick)
            CountResult.MALA ->
                if (s.malaVibration) feedback.mala(true, s.confirmationTick)
                else feedback.count(s.vibrationEnabled, s.confirmationTick)
            CountResult.COUNTED -> feedback.count(s.vibrationEnabled, s.confirmationTick)
        }
    }

    private fun dispatch(events: List<GestureEvent>) {
        for (e in events) execute(settings.actionFor(e.keyCode, e.gesture))
    }

    private fun execute(action: Action) {
        val s = settings
        when (action) {
            Action.INCREMENT -> countOne()
            Action.STEALTH_TOGGLE -> { counter.toggleStealth(); feedback.stealthForceOn(s.vibrationEnabled, s.confirmationTick) }
            Action.STEALTH_FORCE_ON -> { counter.forceStealthOn(); feedback.stealthForceOn(s.vibrationEnabled, s.confirmationTick) }
            Action.NONE -> Unit
            Action.DECREMENT -> Unit // retired
            Action.RESET -> Unit // unreachable by construction (BindingRules); CounterActions has no reset
        }
    }
}
