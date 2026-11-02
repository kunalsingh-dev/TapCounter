package com.example.tapcounter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalaMathTest {
    @Test fun displayWrapsAtMalaSize() {
        assertEquals(0, MalaMath.displayCount(0, 108))
        assertEquals(107, MalaMath.displayCount(107, 108))
        assertEquals(0, MalaMath.displayCount(108, 108))
        assertEquals(5, MalaMath.displayCount(113, 108))
    }

    @Test fun completionOnlyOnExactMultiples() {
        assertFalse(MalaMath.completesMala(107, 108))
        assertTrue(MalaMath.completesMala(108, 108))
        assertFalse(MalaMath.completesMala(109, 108))
        assertTrue(MalaMath.completesMala(216, 108))
        assertTrue(MalaMath.completesMala(27, 27))
        assertFalse(MalaMath.completesMala(0, 108))
    }

    @Test fun progressIsFraction() {
        assertEquals(0.5f, MalaMath.progress(54, 108), 0.0001f)
        assertEquals(0f, MalaMath.progress(108, 108), 0.0001f)
    }
}
