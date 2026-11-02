package com.example.tapcounter.headset

/** Gestures produced by the C++ engine. [nativeId] must match enum class Gesture in gesture_engine.h. */
enum class Gesture(val nativeId: Int) {
    SINGLE(1), DOUBLE(2), TRIPLE(3), LONG_PRESS(4);

    companion object {
        fun fromNativeId(id: Int): Gesture? = entries.firstOrNull { it.nativeId == id }
    }
}

data class GestureEvent(val keyCode: Int, val gesture: Gesture, val timeMs: Long)

/** Timing configuration, all values in milliseconds. */
data class GestureConfig(
    val debounceMs: Long = 80,
    val multiTapWindowMs: Long = 350,
    val longPressMs: Long = 600,
)
