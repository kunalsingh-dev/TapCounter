package com.example.tapcounter.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentCollectorTest {
    private fun v(vararg x: Float) = floatArrayOf(*x)

    @Test fun keepsOnlyGoodFramesSpacedInTime() {
        val c = EnrollmentCollector(target = 3, minSpacingMs = 600)
        assertTrue(c.offer(0, v(1f, 0f), true))
        assertFalse(c.offer(100, v(1f, 0f), true))   // too soon
        assertFalse(c.offer(700, v(1f, 0f), false))  // bad quality
        assertTrue(c.offer(700, v(1f, 0.1f), true))
        assertTrue(c.offer(1400, v(1f, 0.2f), true))
        assertTrue(c.done)
        assertFalse(c.offer(3000, v(1f, 0f), true))  // already full
        assertEquals(3, c.count)
    }

    @Test fun similarSamplesAreConsistent() {
        val c = EnrollmentCollector(target = 3, minSpacingMs = 0)
        c.offer(0, v(1f, 0.1f), true); c.offer(1, v(1f, 0.2f), true); c.offer(2, v(1f, 0f), true)
        assertTrue(c.isConsistent())
    }

    @Test fun mixingTwoPeopleIsRejected() {
        val c = EnrollmentCollector(target = 4, minSpacingMs = 0)
        c.offer(0, v(1f, 0f, 0f), true); c.offer(1, v(1f, 0.05f, 0f), true)
        c.offer(2, v(0f, 1f, 0f), true); c.offer(3, v(0f, 0.95f, 0.1f), true)
        assertFalse(c.isConsistent())
    }

    @Test fun emptyIsNotConsistentAndResetWorks() {
        val c = EnrollmentCollector(target = 2, minSpacingMs = 0)
        assertFalse(c.isConsistent())
        c.offer(0, v(1f, 0f), true)
        c.reset()
        assertEquals(0, c.count)
    }

    @Test fun templatesAreCopiesAndNormalised() {
        val c = EnrollmentCollector(target = 1)
        c.offer(0, v(3f, 4f), true)
        val t = c.templates()
        assertEquals(0.6f, t[0][0], 1e-6f)
        t[0][0] = 99f
        assertEquals(0.6f, c.templates()[0][0], 1e-6f)
    }

    @Test fun oneStrayBystanderSampleIsRejected() {
        val c = EnrollmentCollector(target = 8, minSpacingMs = 0)
        for (i in 0 until 7) c.offer(i.toLong(), v(1f, 0.02f * i, 0f), true)
        c.offer(7, v(0f, 0f, 1f), true) // someone else walked into view
        assertFalse(c.isConsistent())
    }

    @Test fun fiveAndThreeMixIsRejected() {
        val c = EnrollmentCollector(target = 8, minSpacingMs = 0)
        for (i in 0 until 5) c.offer(i.toLong(), v(1f, 0.02f * i, 0f), true)
        for (i in 5 until 8) c.offer(i.toLong(), v(0f, 0.02f * i, 1f), true)
        assertFalse(c.isConsistent())
    }

    @Test fun extraTemplatesAreKeptButIgnoredByConsistencyCheck() {
        val c = EnrollmentCollector(target = 2, minSpacingMs = 0)
        c.offer(0, v(1f, 0f), true, extra = listOf(v(0f, 1f), v(1f, 1f)))
        c.offer(1, v(1f, 0.1f), true, extra = listOf(v(0f, 1f)))
        assertTrue(c.isConsistent())          // the odd extras don't spoil it
        assertEquals(5, c.templates().size)   // 2 samples + 3 extras
        c.reset()
        assertEquals(0, c.templates().size)
    }

    @Test fun singleSampleIsTriviallyConsistent() {
        val c = EnrollmentCollector(target = 1)
        c.offer(0, v(1f, 0f), true)
        assertTrue(c.isConsistent())
    }
}
