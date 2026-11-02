package com.example.tapcounter.headset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BindingRulesTest {

    @Test fun resetIsNotHeadsetAssignable() {
        assertFalse(Action.RESET.isHeadsetAssignable)
        assertFalse(Action.RESET in Action.headsetActions)
    }

    @Test fun everyOtherActionIsAssignable() {
        Action.entries.filter { it != Action.RESET && it != Action.DECREMENT }
            .forEach { assertTrue(it.name, it.isHeadsetAssignable) }
        assertEquals(4, Action.headsetActions.size)
    }

    @Test fun decrementIsRetired() {
        assertFalse(Action.DECREMENT.isHeadsetAssignable)
        assertFalse(Action.DECREMENT in Action.headsetActions)
        assertNull(BindingRules.parseStoredAction("DECREMENT"))
    }

    @Test fun creatingResetBindingThrowsForEveryKeyAndGesture() {
        for (key in HeadsetKeys.supported) for (g in Gesture.entries) {
            try {
                BindingRules.create(key, g, Action.RESET)
                fail("RESET accepted for $key/$g")
            } catch (_: ForbiddenActionException) {
            }
        }
    }

    @Test fun storedResetIsDropped() {
        assertNull(BindingRules.parseStoredAction("RESET"))
        assertNull(BindingRules.parseStoredAction("reset"))
        assertNull(BindingRules.parseStoredAction("NOPE"))
        assertEquals(Action.INCREMENT, BindingRules.parseStoredAction("INCREMENT"))
    }

    @Test fun settingsCannotBeBuiltWithReset() {
        try {
            HeadsetSettings(bindings = mapOf((85 to Gesture.SINGLE) to Action.RESET))
            fail("HeadsetSettings accepted RESET")
        } catch (_: ForbiddenActionException) {
        }
    }

    @Test fun storageKeyRoundTrip() {
        val key = BindingRules.storageKey(85, Gesture.DOUBLE)
        assertEquals("85:DOUBLE", key)
        assertEquals(85 to Gesture.DOUBLE, BindingRules.parseStorageKey(key))
        assertNull(BindingRules.parseStorageKey("85"))
        assertNull(BindingRules.parseStorageKey("x:DOUBLE"))
        assertNull(BindingRules.parseStorageKey("85:TRIPLE_PLUS"))
    }
}
