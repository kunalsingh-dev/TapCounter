package com.example.tapcounter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MalaTimerTest {
    @Test fun sumsGapsBetweenCounts() {
        val t = MalaTimer()
        t.onCount(0); t.onCount(1000); t.onCount(2500)
        assertEquals(2500L, t.finishMala())
    }

    @Test fun longPauseIsNotCounted() {
        val t = MalaTimer(pauseMs = 120_000)
        t.onCount(0); t.onCount(1000)
        t.onCount(1000 + 600_000) // 10 minute break
        t.onCount(1000 + 600_000 + 500)
        assertEquals(1500L, t.finishMala())
    }

    @Test fun finishResetsForNextMala() {
        val t = MalaTimer()
        t.onCount(0); t.onCount(2000)
        t.finishMala()
        t.onCount(100_000)         // gap since the previous mala must not count
        t.onCount(101_000)
        assertEquals(1000L, t.finishMala())
    }

    @Test fun restoreContinuesAfterRestart() {
        val t = MalaTimer()
        t.restore(5000)
        t.onCount(0); t.onCount(1000)
        assertEquals(6000L, t.finishMala())
    }
}
