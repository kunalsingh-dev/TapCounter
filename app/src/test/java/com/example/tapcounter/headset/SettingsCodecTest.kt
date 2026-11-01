package com.example.tapcounter.headset

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsCodecTest {

    @Test fun roundTrip() {
        val map = mapOf(
            (85 to Gesture.SINGLE) to Action.INCREMENT,
            (87 to Gesture.LONG_PRESS) to Action.STEALTH_FORCE_ON,
            (88 to Gesture.DOUBLE) to Action.NONE,
        )
        assertEquals(map, SettingsCodec.decode(SettingsCodec.encode(map)))
    }

    @Test fun decodeDropsResetAndGarbage() {
        val decoded = SettingsCodec.decode(
            setOf("85:SINGLE=RESET", "85:DOUBLE=DECREMENT", "junk", "85:SINGLE", "=INCREMENT", "85:X=INCREMENT", "85:TRIPLE="),
        )
        assertEquals(mapOf((85 to Gesture.DOUBLE) to Action.DECREMENT), decoded)
    }

    @Test fun encodeRefusesReset() {
        try {
            SettingsCodec.encode(mapOf((85 to Gesture.SINGLE) to Action.RESET))
            fail("encode accepted RESET")
        } catch (_: ForbiddenActionException) {
        }
    }

    @Test fun emptySetDecodesToEmptyMap() = assertTrue(SettingsCodec.decode(emptySet()).isEmpty())
}
