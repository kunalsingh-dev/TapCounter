package com.example.tapcounter.headset

/**
 * Thin Kotlin wrapper over the C++ gesture engine (libtapgesture.so).
 *
 * Usage: feed every KeyEvent through [onKey] with SystemClock.uptimeMillis(), then call
 * [tick] at [nextDeadlineMs] (e.g. Handler.postAtTime) to resolve timeouts.
 * Methods are synchronized; after [close] they return empty/-1 instead of crashing.
 */
class NativeGestureEngine(config: GestureConfig = GestureConfig()) : AutoCloseable {

    private var handle: Long =
        nativeCreate(config.debounceMs, config.multiTapWindowMs, config.longPressMs)

    @Synchronized
    fun configure(config: GestureConfig) {
        if (handle == 0L) return
        nativeConfigure(handle, config.debounceMs, config.multiTapWindowMs, config.longPressMs)
    }

    /** 1..3. With 1, a single click is emitted at once (no waiting for a possible double click). */
    @Synchronized
    fun setMaxTaps(keyCode: Int, maxTaps: Int) {
        if (handle != 0L) nativeSetMaxTaps(handle, keyCode, maxTaps)
    }

    @Synchronized
    fun onKey(keyCode: Int, down: Boolean, timeMs: Long): List<GestureEvent> {
        if (handle == 0L) return emptyList()
        return decode(nativeOnKey(handle, keyCode, down, timeMs))
    }

    @Synchronized
    fun tick(nowMs: Long): List<GestureEvent> {
        if (handle == 0L) return emptyList()
        return decode(nativeTick(handle, nowMs))
    }

    /** Next time [tick] must be called, or -1 when nothing is pending. */
    @Synchronized
    fun nextDeadlineMs(): Long = if (handle == 0L) -1L else nativeNextDeadline(handle)

    @Synchronized
    fun reset() {
        if (handle != 0L) nativeReset(handle)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private fun decode(flat: LongArray?): List<GestureEvent> {
        if (flat == null || flat.isEmpty()) return emptyList()
        val out = ArrayList<GestureEvent>(flat.size / 3)
        var i = 0
        while (i + 2 < flat.size) {
            val gesture = Gesture.fromNativeId(flat[i + 1].toInt())
            if (gesture != null) out.add(GestureEvent(flat[i].toInt(), gesture, flat[i + 2]))
            i += 3
        }
        return out
    }

    private external fun nativeCreate(debounceMs: Long, multiTapMs: Long, longPressMs: Long): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeConfigure(handle: Long, debounceMs: Long, multiTapMs: Long, longPressMs: Long)
    private external fun nativeOnKey(handle: Long, keyCode: Int, down: Boolean, timeMs: Long): LongArray?
    private external fun nativeTick(handle: Long, nowMs: Long): LongArray?
    private external fun nativeNextDeadline(handle: Long): Long
    private external fun nativeReset(handle: Long)
    private external fun nativeSetMaxTaps(handle: Long, keyCode: Int, maxTaps: Int)

    companion object {
        init {
            System.loadLibrary("tapgesture")
        }
    }
}
