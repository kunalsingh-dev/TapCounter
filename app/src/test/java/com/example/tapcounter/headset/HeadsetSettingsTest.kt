package com.example.tapcounter.headset

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadsetSettingsTest {

    @Test fun defaultsMatchSpecExample() {
        val s = HeadsetSettings()
        assertEquals(Action.INCREMENT, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.SINGLE))
        assertEquals(Action.DECREMENT, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.DOUBLE))
        assertEquals(Action.STEALTH_TOGGLE, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.TRIPLE))
        assertEquals(Action.STEALTH_FORCE_ON, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.LONG_PRESS))
        assertEquals(80L, s.debounceMs)
    }

    @Test fun unboundGestureIsNone() =
        assertEquals(Action.NONE, HeadsetSettings().actionFor(HeadsetKeys.MEDIA_NEXT, Gesture.SINGLE))

    @Test fun hardwareLockIgnoresEverything() {
        val s = HeadsetSettings(hardwareLock = true)
        for (g in Gesture.entries) assertEquals(Action.NONE, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, g))
    }

    @Test fun sanitizedClampsTiming() {
        val s = HeadsetSettings(debounceMs = 9999, multiTapWindowMs = 1, longPressMs = 10).sanitized()
        assertEquals(300L, s.debounceMs)
        assertEquals(200L, s.multiTapWindowMs)
        assertEquals(300L, s.longPressMs)
    }

    @Test fun keyNames() {
        assertEquals("KEYCODE_HEADSETHOOK", HeadsetKeys.name(79))
        assertEquals("KEYCODE_999", HeadsetKeys.name(999))
    }
}
