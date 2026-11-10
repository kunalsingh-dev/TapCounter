package com.example.tapcounter.headset

/** Immutable snapshot of every headset / counting-feedback setting. */
data class HeadsetSettings(
    /** Hardware lock: when true, headphone and volume buttons are ignored (Learn mode still works). */
    val hardwareLock: Boolean = false,
    val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    val multiTapWindowMs: Long = DEFAULT_MULTI_TAP_MS,
    val longPressMs: Long = DEFAULT_LONG_PRESS_MS,
    /** Optional tiny tick played through the headset as confirmation. */
    val confirmationTick: Boolean = false,
    /** Short buzz for every counted tap. */
    val vibrationEnabled: Boolean = true,
    /** Distinct long pattern when a mala is completed. */
    val malaVibration: Boolean = true,
    /** Silent looping audio so Android keeps routing media buttons to us (costs a little battery). */
    val keepAliveAudio: Boolean = true,
    /** Phone volume buttons count +1 (a held key counts once). */
    val volumeKeysCount: Boolean = true,
    /**
     * Rapid-tap mode: every NEW press counts even if the headset never sent the release of the
     * previous one (some send only press events when you tap fast).
     */
    val fastTaps: Boolean = true,
    /** Counts per mala (27, 54 or 108). */
    val malaSize: Int = DEFAULT_MALA_SIZE,
    val bindings: Map<Pair<Int, Gesture>, Action> = defaultBindings(),
) {
    init {
        // Last line of defence: no code path may build settings that map RESET to a button.
        bindings.values.forEach { BindingRules.requireAssignable(it) }
    }

    val gestureConfig: GestureConfig
        get() = GestureConfig(debounceMs, multiTapWindowMs, longPressMs)

    /** What a finished gesture should do. NONE when locked or unbound. */
    fun actionFor(keyCode: Int, gesture: Gesture): Action {
        if (hardwareLock) return Action.NONE
        return bindings[keyCode to gesture] ?: Action.NONE
    }

    /** Same settings with values clamped to their allowed ranges. */
    fun sanitized(): HeadsetSettings = copy(
        debounceMs = debounceMs.coerceIn(DEBOUNCE_RANGE),
        multiTapWindowMs = multiTapWindowMs.coerceIn(MULTI_TAP_RANGE),
        longPressMs = longPressMs.coerceIn(LONG_PRESS_RANGE),
        malaSize = if (malaSize in MALA_SIZES) malaSize else DEFAULT_MALA_SIZE,
    )

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 50L
        const val DEFAULT_MULTI_TAP_MS = 350L
        /** Long enough that a normal press never triggers it: this is the stealth "special combination". */
        const val DEFAULT_LONG_PRESS_MS = 1200L
        const val DEFAULT_MALA_SIZE = 108

        val DEBOUNCE_RANGE = 0L..300L
        val MULTI_TAP_RANGE = 200L..800L
        val LONG_PRESS_RANGE = 300L..2000L
        val MALA_SIZES = listOf(27, 54, 108)

        /**
         * Every media key: single click = +1 (instant, because no double/triple is bound).
         * Play/Pause and the hook button: long hold = stealth on/off.
         */
        fun defaultBindings(): Map<Pair<Int, Gesture>, Action> {
            val map = LinkedHashMap<Pair<Int, Gesture>, Action>()
            for (key in HeadsetKeys.supported.sorted()) map[key to Gesture.SINGLE] = Action.INCREMENT
            for (key in listOf(HeadsetKeys.MEDIA_PLAY_PAUSE, HeadsetKeys.HEADSETHOOK)) {
                map[key to Gesture.LONG_PRESS] = Action.STEALTH_TOGGLE
            }
            return map
        }
    }
}
