package com.example.tapcounter.headset

/**
 * Turns the raw stream of volume-key adjust events into "one count per physical press".
 *
 * Problem: when a key is HELD, Android repeats it (first repeat after ~500 ms, then every ~50 ms)
 * and a MediaSession volume provider gets one onAdjustVolume per repeat without repeatCount.
 * Rule: a press is only confirmed if no other event follows within [confirmDelayMs]. If a second
 * event arrives inside that window it is a key-repeat stream: the pending press is dropped and
 * everything is ignored until the stream has been quiet for [quietMs]. A held key therefore
 * counts exactly once (at its first event); the next real tap counts again.
 * Pure logic, driven by timestamps, no Android classes.
 */
class VolumeKeyFilter(
    private val confirmDelayMs: Long = 80,
    private val quietMs: Long = 250,
) {
    private var pendingAt = NONE
    private var lastEventAt = NONE
    private var held = false

    /** Feed one adjust event. Returns counts to commit now (0 or 1). */
    fun onEvent(now: Long): Int {
        val commits = commitIfDue(now)
        if (held) {
            if (now - lastEventAt < quietMs) {
                lastEventAt = now
                return commits
            }
            held = false
        }
        if (pendingAt != NONE) {
            // Second event before the first was confirmed: key-repeat stream.
            pendingAt = NONE
            held = true
            lastEventAt = now
            return commits
        }
        pendingAt = now
        lastEventAt = now
        return commits
    }

    /** Call at [nextDeadlineMs] to confirm a pending press. */
    fun poll(now: Long): Int = commitIfDue(now)

    /** Time when [poll] must be called, or -1. */
    fun nextDeadlineMs(): Long = if (pendingAt == NONE) -1L else pendingAt + confirmDelayMs

    fun reset() {
        pendingAt = NONE
        lastEventAt = NONE
        held = false
    }

    private fun commitIfDue(now: Long): Int {
        if (pendingAt != NONE && now - pendingAt >= confirmDelayMs) {
            pendingAt = NONE
            return 1
        }
        return 0
    }

    private companion object {
        const val NONE = Long.MIN_VALUE / 2
    }
}
