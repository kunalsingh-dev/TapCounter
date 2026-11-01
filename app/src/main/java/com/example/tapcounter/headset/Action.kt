package com.example.tapcounter.headset

/**
 * App-level actions. If your app already has an Action enum, merge these entries into it
 * and keep [isHeadsetAssignable].
 */
enum class Action {
    INCREMENT,
    DECREMENT,
    STEALTH_TOGGLE,
    STEALTH_FORCE_ON,
    NONE,
    RESET;

    /** RESET is destructive and must never be reachable from a headphone button. */
    val isHeadsetAssignable: Boolean get() = this != RESET

    companion object {
        /** Actions the Settings UI may offer for headset buttons. */
        val headsetActions: List<Action> = entries.filter { it.isHeadsetAssignable }
    }
}
