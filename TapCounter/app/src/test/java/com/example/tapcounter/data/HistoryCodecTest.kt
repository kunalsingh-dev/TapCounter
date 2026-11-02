package com.example.tapcounter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryCodecTest {
    @Test fun roundTrip() {
        val r = HistoryCodec.emptyDay("2026-11-02").withCount(6, false).withCount(6, true).withCount(23, false)
        assertEquals(3, r.total)
        assertEquals(1, r.malas)
        assertEquals(2, r.hours[6])
        assertEquals(r, HistoryCodec.decode("2026-11-02", HistoryCodec.encode(r)))
    }

    @Test fun malformedIsNull() {
        assertNull(HistoryCodec.decode("d", "garbage"))
        assertNull(HistoryCodec.decode("d", "1|1,2,3"))
        assertNull(HistoryCodec.decode("d", "x|" + List(24) { "0" }.joinToString(",")))
        assertNull(HistoryCodec.decode("d", "1|" + List(24) { if (it == 3) "z" else "0" }.joinToString(",")))
    }

    @Test fun hourIsClamped() {
        val r = HistoryCodec.emptyDay("d").withCount(99, false)
        assertEquals(1, r.hours[23])
    }
}
