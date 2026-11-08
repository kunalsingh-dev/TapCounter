package com.example.tapcounter.headset

/**
 * "Touch speed" for screen taps: a tap closer than [minGapMs] to the previous COUNTED tap is
 * ignored. 0 = every touch counts (Turbo). Pure logic, timestamps in milliseconds.
 */
class TapRateLimiter {
    private var last = NEVER

    fun allow(now: Long, minGapMs: Long): Boolean {
        if (minGapMs > 0 && now - last < minGapMs) return false
        last = now
        return true
    }

    fun reset() {
        last = NEVER
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2
    }
}
