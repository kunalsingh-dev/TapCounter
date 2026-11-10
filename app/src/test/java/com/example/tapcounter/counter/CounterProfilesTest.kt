package com.example.tapcounter.counter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterProfilesTest {
    private val main = CounterProfile("main", "Main", "1234")
    private val other = CounterProfile("c1", "Hanuman Chalisa", "5678")
    private val all = listOf(main, other)

    @Test fun codeValidation() {
        assertTrue(CounterProfiles.isValidCode("1234"))
        assertTrue(CounterProfiles.isValidCode("00000000"))
        assertFalse(CounterProfiles.isValidCode("123"))
        assertFalse(CounterProfiles.isValidCode("123456789"))
        assertFalse(CounterProfiles.isValidCode("12a4"))
        assertFalse(CounterProfiles.isValidCode(""))
    }

    @Test fun codesMustBeUniqueAcrossCounters() {
        assertNull(CounterProfiles.codeError(all, "9999", null))
        assertEquals("That code already opens another counter.", CounterProfiles.codeError(all, "5678", null))
        assertEquals("That code already opens another counter.", CounterProfiles.codeError(all, "1234", "c1"))
    }

    @Test fun keepingYourOwnCodeIsAllowed() {
        assertNull(CounterProfiles.codeError(all, "5678", "c1"))
    }

    @Test fun badCodeMessage() {
        assertEquals("Use 4 to 8 digits.", CounterProfiles.codeError(all, "12", null))
    }

    @Test fun eachCodeOpensItsOwnCounter() {
        assertEquals("main", CounterProfiles.findByCode(all, "1234")?.id)
        assertEquals("c1", CounterProfiles.findByCode(all, "5678")?.id)
        assertNull(CounterProfiles.findByCode(all, "0000"))
    }

    @Test fun newIdSkipsUsedIds() {
        assertEquals("c1", CounterProfiles.newId(listOf(main)))
        assertEquals("c2", CounterProfiles.newId(all))
        assertEquals("c1", CounterProfiles.newId(listOf(main, CounterProfile("c2", "x", "1111"))))
    }

    @Test fun entryRoundTripKeepsAnyName() {
        val p = CounterProfile("c7", "राम | नाम, जप\nline2", "4321")
        assertEquals(p, CounterProfiles.decodeEntry("c7", CounterProfiles.encodeEntry(p)))
    }

    @Test fun brokenEntriesAreRejected() {
        assertNull(CounterProfiles.decodeEntry("c1", "nopipe"))
        assertNull(CounterProfiles.decodeEntry("c1", "|nocode"))
        assertNull(CounterProfiles.decodeEntry("c1", "12|short"))
        assertNull(CounterProfiles.decodeEntry("c1", "abcd|letters"))
    }

    @Test fun orderRoundTrip() {
        assertEquals(listOf("c1", "c3"), CounterProfiles.decodeOrder(CounterProfiles.encodeOrder(listOf("c1", "c3"))))
        assertEquals(emptyList<String>(), CounterProfiles.decodeOrder(""))
    }

    @Test fun stealthBrightness() {
        assertEquals(0.01f, StealthBrightness.pick(0, lcd = false, blackPct = 1, disguisePct = 30), 1e-6f)
        assertEquals(0f, StealthBrightness.pick(0, lcd = true, blackPct = 1, disguisePct = 30), 0f)
        assertEquals(0.30f, StealthBrightness.pick(2, lcd = false, blackPct = 1, disguisePct = 30), 1e-6f)
        assertEquals(0.30f, StealthBrightness.pick(2, lcd = true, blackPct = 1, disguisePct = 30), 1e-6f)
        assertEquals(0.01f, StealthBrightness.pick(1, lcd = false, blackPct = 50, disguisePct = 0), 1e-6f)
    }
}
