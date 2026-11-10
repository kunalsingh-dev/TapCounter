package com.example.tapcounter.headset

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadsetSettingsTest {

    @Test fun defaultsAreInstantCountPlusHoldForStealth() {
        val s = HeadsetSettings()
        for (key in HeadsetKeys.supported) assertEquals(Action.INCREMENT, s.actionFor(key, Gesture.SINGLE))
        assertEquals(Action.STEALTH_TOGGLE, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.LONG_PRESS))
        assertEquals(Action.NONE, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.DOUBLE))
        assertEquals(Action.NONE, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.TRIPLE))
        assertEquals(50L, s.debounceMs)
        assertEquals(1200L, s.longPressMs)
    }

    @Test fun noDefaultBindingEverDecrementsOrResets() {
        assertEquals(0, HeadsetSettings.defaultBindings().values.count { it == Action.DECREMENT || it == Action.RESET })
    }

    @Test fun unboundGestureIsNone() =
        assertEquals(Action.NONE, HeadsetSettings().actionFor(HeadsetKeys.MEDIA_NEXT, Gesture.DOUBLE))

    @Test fun hardwareLockIgnoresEverything() {
        val s = HeadsetSettings(hardwareLock = true)
        for (g in Gesture.entries) assertEquals(Action.NONE, s.actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, g))
    }

    @Test fun sanitizedClampsValues() {
        val s = HeadsetSettings(debounceMs = 9999, multiTapWindowMs = 1, longPressMs = 10, malaSize = 50).sanitized()
        assertEquals(300L, s.debounceMs)
        assertEquals(200L, s.multiTapWindowMs)
        assertEquals(300L, s.longPressMs)
        assertEquals(108, s.malaSize)
    }

    @Test fun validMalaSizesSurvive() {
        for (size in listOf(27, 54, 108)) assertEquals(size, HeadsetSettings(malaSize = size).sanitized().malaSize)
    }

    @Test fun keyNames() {
        assertEquals("KEYCODE_HEADSETHOOK", HeadsetKeys.name(79))
        assertEquals("KEYCODE_999", HeadsetKeys.name(999))
    }
}
