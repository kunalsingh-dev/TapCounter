package com.example.tapcounter.data

/**
 * Measures how long one mala took, counting only active chanting: a gap between two counts
 * longer than [pauseMs] is treated as a break and not added.
 */
class MalaTimer(private val pauseMs: Long = 120_000L) {
    var activeMs: Long = 0L
        private set
    private var lastAt = -1L

    /** Call on every count with a monotonic clock (milliseconds). */
    fun onCount(now: Long) {
        if (lastAt >= 0) {
            val gap = now - lastAt
            if (gap in 0..pauseMs) activeMs += gap
        }
        lastAt = now
    }

    /** Call right after the count that completed the mala. Returns its active duration and resets. */
    fun finishMala(): Long {
        val d = activeMs
        reset()
        return d
    }

    fun reset() {
        activeMs = 0L
        lastAt = -1L
    }

    /** Restore progress after the app was restarted in the middle of a mala. */
    fun restore(savedActiveMs: Long) {
        activeMs = savedActiveMs.coerceAtLeast(0L)
        lastAt = -1L
    }
}
