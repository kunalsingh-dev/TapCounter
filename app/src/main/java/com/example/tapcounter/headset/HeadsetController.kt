package com.example.tapcounter.headset

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
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
 * Hub between the MediaSession (raw key events) and the app (counter actions).
 * All entry points run on the main thread (MediaSession callbacks are delivered there).
 *
 * key event -> [learn mode? capture] -> [hardware lock? swallow] -> C++ engine
 *           -> gesture -> settings lookup -> Action -> CounterActions + feedback
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

    init {
        scope.launch {
            repo.settings.collect { s ->
                settings = s
                engine.configure(s.gestureConfig)
            }
        }
    }

    fun updateStatus(transform: (HeadsetStatus) -> HeadsetStatus) = _status.update(transform)

    // ---- Learn mode ----------------------------------------------------------------

    fun startLearn() {
        resetGestureState()
        _learn.value = LearnState(active = true, captured = null)
    }

    fun stopLearn() {
        _learn.value = LearnState(active = false, captured = null)
    }

    // ---- Key path ------------------------------------------------------------------

    /** Called from MediaSessionCompat.Callback.onMediaButtonEvent. Returns true if consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return true // ACTION_MULTIPLE etc.: consume, ignore
        }
        val now = SystemClock.uptimeMillis()

        if (down) {
            // Receiving a key proves the session is currently ours.
            _status.update {
                it.copy(
                    lastKeyName = HeadsetKeys.name(code),
                    lastKeyAtMs = now,
                    possibleConflict = false,
                )
            }
        }

        // Learn mode captures ANY key (even unknown ones) and bypasses the hardware lock.
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
        engine.reset()
    }

    fun release() {
        handler.removeCallbacks(tickRunnable)
    }

    // ---- internals -----------------------------------------------------------------

    private fun scheduleTick() {
        handler.removeCallbacks(tickRunnable)
        val deadline = engine.nextDeadlineMs()
        if (deadline >= 0) {
            val wait = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L) + 1
            handler.postDelayed(tickRunnable, wait)
        }
    }

    private fun dispatch(events: List<GestureEvent>) {
        for (e in events) execute(settings.actionFor(e.keyCode, e.gesture))
    }

    private fun execute(action: Action) {
        val s = settings
        when (action) {
            Action.INCREMENT -> { counter.increment(); feedback.count(s.vibrationEnabled, s.confirmationTick) }
            Action.DECREMENT -> { counter.decrement(); feedback.count(s.vibrationEnabled, s.confirmationTick) }
            Action.STEALTH_TOGGLE -> { counter.toggleStealth(); feedback.count(s.vibrationEnabled, s.confirmationTick) }
            Action.STEALTH_FORCE_ON -> { counter.forceStealthOn(); feedback.stealthForceOn(s.vibrationEnabled, s.confirmationTick) }
            Action.NONE -> Unit
            Action.RESET -> Unit // unreachable by construction (BindingRules); CounterActions has no reset
        }
    }
}
