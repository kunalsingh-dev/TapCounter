package com.example.tapcounter.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyticsTest {
    private val today = LocalDate.of(2026, 11, 8)

    private fun day(date: String, total: Int, malas: Int = 0, hour: Int = 6) =
        DayRecord(date, List(24) { if (it == hour) total else 0 }, malas)

    @Test fun lastDaysFillsMissingDaysWithZero() {
        val pts = Analytics.lastDays(listOf(day("2026-11-08", 50), day("2026-11-06", 20)), 7, today)
        assertEquals(7, pts.size)
        assertEquals("2026-11-02", pts.first().date)
        assertEquals("2026-11-08", pts.last().date)
        assertEquals(listOf(0, 0, 0, 0, 20, 0, 50), pts.map { it.total })
    }

    @Test fun bestAndAverage() {
        val pts = Analytics.lastDays(listOf(day("2026-11-08", 50), day("2026-11-06", 20)), 7, today)
        assertEquals("2026-11-08", Analytics.best(pts)?.date)
        assertEquals(10, Analytics.average(pts)) // 70 / 7
        assertNull(Analytics.best(Analytics.lastDays(emptyList(), 7, today)))
        assertEquals(0, Analytics.average(emptyList()))
    }

    @Test fun hourTotalsOnlyCountsRange() {
        val days = listOf(day("2026-11-08", 10, hour = 5), day("2026-11-07", 4, hour = 5), day("2026-10-01", 99, hour = 5))
        val h = Analytics.hourTotals(days, 7, today)
        assertEquals(14, h[5])
        assertEquals(24, h.size)
    }

    @Test fun streakCountsBackFromTodayOrYesterday() {
        val d = listOf(day("2026-11-08", 5), day("2026-11-07", 5), day("2026-11-06", 5), day("2026-11-04", 5))
        assertEquals(3, Analytics.streak(d, today))
        val noToday = listOf(day("2026-11-07", 5), day("2026-11-06", 5))
        assertEquals(2, Analytics.streak(noToday, today))
        assertEquals(0, Analytics.streak(emptyList(), today))
    }

    @Test fun goalDaysMet() {
        val pts = Analytics.lastDays(listOf(day("2026-11-08", 500, malas = 5), day("2026-11-07", 100, malas = 1)), 7, today)
        assertEquals(1, Analytics.goalDaysMet(pts, 5))
        assertEquals(2, Analytics.goalDaysMet(pts, 1))
        assertEquals(0, Analytics.goalDaysMet(pts, 0))
    }

    @Test fun malaStats() {
        val s = MalaStats.summarize(listOf(100, 120, 90))
        assertEquals(3, s.count)
        assertEquals(100, s.lastSec)
        assertEquals(103, s.avgSec)
        assertEquals(90, s.fastestSec)
        assertEquals(0, MalaStats.summarize(emptyList()).count)
        assertNull(MalaStats.summarize(emptyList()).lastSec)
    }

    @Test fun malaRecordCodec() {
        val r = MalaRecord("2026-11-08T07:00:00", 412)
        assertEquals(r, MalaRecordCodec.decode(MalaRecordCodec.encode(r)))
        assertNull(MalaRecordCodec.decode("x"))
        assertNull(MalaRecordCodec.decode("a|b"))
    }
}
