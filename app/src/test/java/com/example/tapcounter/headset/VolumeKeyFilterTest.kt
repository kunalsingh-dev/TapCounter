package com.example.tapcounter.headset

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeKeyFilterTest {

    private fun drain(f: VolumeKeyFilter, events: List<Long>, until: Long): Int {
        var total = 0
        for (t in events) total += f.onEvent(t)
        total += f.poll(until)
        return total
    }

    @Test fun singleTapCountsOnce() {
        val f = VolumeKeyFilter()
        assertEquals(0, f.onEvent(1000))
        assertEquals(1080L, f.nextDeadlineMs())
        assertEquals(0, f.poll(1079))
        assertEquals(1, f.poll(1080))
        assertEquals(-1L, f.nextDeadlineMs())
    }

    @Test fun heldKeyCountsExactlyOnce() {
        val f = VolumeKeyFilter()
        // press at 0, auto-repeat starts at 500 then every 50 ms until 3000
        val events = mutableListOf(0L)
        var t = 500L
        while (t <= 3000) { events.add(t); t += 50 }
        assertEquals(1, drain(f, events, 5000))
    }

    @Test fun nextRealTapAfterHoldCountsAgain() {
        val f = VolumeKeyFilter()
        val hold = mutableListOf(0L)
        var t = 500L
        while (t <= 1500) { hold.add(t); t += 50 }
        var total = drain(f, hold, 2000)
        total += drain(f, listOf(3000L), 3100)
        assertEquals(2, total)
    }

    @Test fun separateTapsEachCount() {
        val f = VolumeKeyFilter()
        var total = 0
        for (t in listOf(0L, 300L, 600L, 900L)) total += f.onEvent(t)
        total += f.poll(2000)
        assertEquals(4, total)
    }

    @Test fun fastTappingAround8PerSecondIsNotLost() {
        val f = VolumeKeyFilter()
        var total = 0
        for (i in 0 until 20) total += f.onEvent(i * 125L)
        total += f.poll(10_000)
        assertEquals(20, total)
    }

    @Test fun resetClearsPending() {
        val f = VolumeKeyFilter()
        f.onEvent(0)
        f.reset()
        assertEquals(-1L, f.nextDeadlineMs())
        assertEquals(0, f.poll(1000))
    }
}
