package com.example.tapcounter.headset

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapRateLimiterTest {
    @Test fun turboCountsEveryTouch() {
        val l = TapRateLimiter()
        for (t in listOf(0L, 1L, 2L, 3L)) assertTrue(l.allow(t, 0))
    }

    @Test fun slowIgnoresTapsInsideTheGap() {
        val l = TapRateLimiter()
        assertTrue(l.allow(1000, 400))
        assertFalse(l.allow(1200, 400))
        assertFalse(l.allow(1399, 400))
        assertTrue(l.allow(1400, 400))
    }

    @Test fun ignoredTapDoesNotExtendTheGap() {
        val l = TapRateLimiter()
        assertTrue(l.allow(0, 300))
        assertFalse(l.allow(200, 300))
        assertTrue(l.allow(300, 300)) // measured from the last COUNTED tap
    }

    @Test fun resetAllowsImmediately() {
        val l = TapRateLimiter()
        l.allow(0, 500)
        l.reset()
        assertTrue(l.allow(1, 500))
    }
}
