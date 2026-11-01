package com.example.tapcounter.headset

/** Immutable snapshot of every headset-related setting. */
data class HeadsetSettings(
    /** Hardware lock: when true, headphone buttons are ignored (Learn mode still works). */
    val hardwareLock: Boolean = false,
    val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    val multiTapWindowMs: Long = DEFAULT_MULTI_TAP_MS,
    val longPressMs: Long = DEFAULT_LONG_PRESS_MS,
    /** Optional tiny tick played through the headset as confirmation. */
    val confirmationTick: Boolean = false,
    val vibrationEnabled: Boolean = true,
    /** Silent looping audio so Android keeps routing media buttons to us (costs a little battery). */
    val keepAliveAudio: Boolean = true,
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

    /** Same settings with timing values clamped to their allowed ranges. */
    fun sanitized(): HeadsetSettings = copy(
        debounceMs = debounceMs.coerceIn(DEBOUNCE_RANGE),
        multiTapWindowMs = multiTapWindowMs.coerceIn(MULTI_TAP_RANGE),
        longPressMs = longPressMs.coerceIn(LONG_PRESS_RANGE),
    )

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 80L
        const val DEFAULT_MULTI_TAP_MS = 350L
        const val DEFAULT_LONG_PRESS_MS = 600L

        val DEBOUNCE_RANGE = 0L..300L
        val MULTI_TAP_RANGE = 200L..800L
        val LONG_PRESS_RANGE = 300L..2000L

        /** Play/Pause and the classic headset hook button get the example mapping. */
        fun defaultBindings(): Map<Pair<Int, Gesture>, Action> {
            val map = LinkedHashMap<Pair<Int, Gesture>, Action>()
            for (key in listOf(HeadsetKeys.MEDIA_PLAY_PAUSE, HeadsetKeys.HEADSETHOOK)) {
                map[key to Gesture.SINGLE] = Action.INCREMENT
                map[key to Gesture.DOUBLE] = Action.DECREMENT
                map[key to Gesture.TRIPLE] = Action.STEALTH_TOGGLE
                map[key to Gesture.LONG_PRESS] = Action.STEALTH_FORCE_ON
            }
            return map
        }
    }
}
