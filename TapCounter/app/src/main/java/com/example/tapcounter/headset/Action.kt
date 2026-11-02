package com.example.tapcounter.headset

/**
 * App-level actions. DECREMENT is kept only so old saved data still parses; the app has no
 * decrement any more and it can no longer be assigned. RESET can never be assigned.
 */
enum class Action {
    INCREMENT,
    DECREMENT,
    STEALTH_TOGGLE,
    STEALTH_FORCE_ON,
    NONE,
    RESET;

    /** RESET is destructive and DECREMENT is retired: neither may be reachable from a headphone button. */
    val isHeadsetAssignable: Boolean get() = this != RESET && this != DECREMENT

    companion object {
        /** Actions the Settings UI may offer for headset buttons. */
        val headsetActions: List<Action> = entries.filter { it.isHeadsetAssignable }
    }
}
