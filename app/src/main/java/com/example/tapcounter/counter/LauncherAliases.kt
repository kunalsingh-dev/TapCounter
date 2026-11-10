package com.example.tapcounter.counter

/**
 * The app icon + name shown by the phone's launcher is chosen by enabling exactly one of several
 * activity-aliases. This is the pure part: which one is enabled right now.
 */
object LauncherAliases {
    const val CALCULATOR = 0
    const val CLOCK = 1
    const val TAP_COUNTER = 2

    val labels = listOf("Calculator", "Clock", "Tap Counter")

    /** Component names in the manifest, same order as [labels]. */
    val componentNames = listOf("AliasCalculator", "AliasClock", "AliasTapCounter")

    // PackageManager.COMPONENT_ENABLED_STATE_*: DEFAULT = 0 (use the manifest value), ENABLED = 1, DISABLED = 2
    private const val STATE_DEFAULT = 0
    private const val STATE_ENABLED = 1

    /** The alias that is enabled. A component never changed uses its manifest value: only CALCULATOR is on there. */
    fun currentIndex(states: List<Int>): Int {
        for ((i, s) in states.withIndex()) {
            if (s == STATE_ENABLED || (s == STATE_DEFAULT && i == CALCULATOR)) return i
        }
        return CALCULATOR
    }
}
