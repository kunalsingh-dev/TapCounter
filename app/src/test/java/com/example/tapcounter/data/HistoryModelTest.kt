package com.example.tapcounter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryModelTest {

    private fun day(date: String, total: Int, malas: Int) =
        DayRecord(date, List(24) { if (it == 0) total else 0 }, malas)

    @Test fun resetEventRoundTrip() {
        val e = ResetEvent("2026-11-02T15:12:03", ResetType.ALL, 57, 300, 1284, 11)
        assertEquals(e, ResetCodec.decode(ResetCodec.encode(e)))
        val m = ResetEvent("2026-11-02T09:00:00", ResetType.MALA, 12, 12, 12, 0)
        assertEquals(m, ResetCodec.decode(ResetCodec.encode(m)))
    }

    @Test fun resetEventMalformedIsNull() {
        assertNull(ResetCodec.decode("garbage"))
        assertNull(ResetCodec.decode("NOPE|2026-11-02T15:12:03|1|2|3|4"))
        assertNull(ResetCodec.decode("ALL|2026-11-02T15:12:03|x|2|3|4"))
    }

    @Test fun baselineRoundTrip() {
        val b = Baseline(100, 3, "2026-11-02", 40, 1)
        assertEquals(b, BaselineCodec.decode(BaselineCodec.encode(b)))
        assertNull(BaselineCodec.decode("1|2"))
    }

    @Test fun summaryWithoutResetIsRawTotals() {
        val days = listOf(day("2026-11-02", 50, 0), day("2026-11-01", 216, 2))
        val s = SummaryMath.compute(days, Baseline(), "2026-11-02")
        assertEquals(50, s.todayTotal)
        assertEquals(266, s.allTime)
        assertEquals(2, s.allMalas)
    }

    @Test fun resetAllZeroesEverythingButKeepsDays() {
        val days = listOf(day("2026-11-02", 50, 1), day("2026-11-01", 216, 2))
        val b = SummaryMath.baselineNow(days, "2026-11-02")
        val s = SummaryMath.compute(days, b, "2026-11-02")
        assertEquals(Summary(0, 0, 0, 0), s)
        assertEquals(2, days.size) // data is still there
    }

    @Test fun countsAfterResetStartFromZero() {
        val before = listOf(day("2026-11-02", 50, 1))
        val b = SummaryMath.baselineNow(before, "2026-11-02")
        val after = listOf(day("2026-11-02", 58, 1))
        val s = SummaryMath.compute(after, b, "2026-11-02")
        assertEquals(8, s.todayTotal)
        assertEquals(8, s.allTime)
        assertEquals(0, s.todayMalas)
    }

    @Test fun todayBaselineDoesNotLeakToNextDay() {
        val days = listOf(day("2026-11-02", 50, 1))
        val b = SummaryMath.baselineNow(days, "2026-11-02")
        val next = listOf(day("2026-11-03", 20, 0), day("2026-11-02", 50, 1))
        val s = SummaryMath.compute(next, b, "2026-11-03")
        assertEquals(20, s.todayTotal)
        assertEquals(20, s.allTime)
    }

    @Test fun neverNegative() {
        val s = SummaryMath.compute(emptyList(), Baseline(allTime = 99, allMalas = 9, todayDate = "d", todayTotal = 5), "d")
        assertEquals(Summary(0, 0, 0, 0), s)
    }
}
