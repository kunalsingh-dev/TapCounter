package com.example.tapcounter.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun CalculatorEngine.type(s: String) {
        for (c in s) when (c) {
            in '0'..'9' -> digit(c - '0')
            '.' -> dot()
            '+', '−', '×', '÷' -> operator(c)
            '=' -> equalsKey()
        }
    }

    @Test fun basicArithmetic() {
        val e = CalculatorEngine(); e.type("2+3=")
        assertEquals("5", e.display)
        val m = CalculatorEngine(); m.type("12×3=")
        assertEquals("36", m.display)
        val d = CalculatorEngine(); d.type("10÷4=")
        assertEquals("2.5", d.display)
    }

    @Test fun immediateExecutionChain() {
        val e = CalculatorEngine(); e.type("2+3×4=")
        assertEquals("20", e.display)
    }

    @Test fun divisionByZeroIsError() {
        val e = CalculatorEngine(); e.type("1÷0=")
        assertEquals("Error", e.display)
        e.clear()
        assertEquals("0", e.display)
    }

    @Test fun decimalsAndTyping() {
        val e = CalculatorEngine(); e.type("0.5+0.25=")
        assertEquals("0.75", e.display)
        val t = CalculatorEngine(); t.type("007")
        assertEquals("7", t.display)
    }

    @Test fun negateAndPercent() {
        val e = CalculatorEngine(); e.type("50"); e.percent()
        assertEquals("0.5", e.display)
        val n = CalculatorEngine(); n.type("8"); n.negate()
        assertEquals("-8", n.display)
    }

    @Test fun secretMatchesOnlyForPlainTypedCode() {
        val e = CalculatorEngine(); e.type("1234")
        assertTrue(e.secretMatches("1234"))
        assertFalse(e.secretMatches("12345"))
    }

    @Test fun secretKeepsLeadingZeros() {
        val e = CalculatorEngine(); e.type("0123")
        assertTrue(e.secretMatches("0123"))
    }

    @Test fun secretNotMatchedInsideACalculation() {
        val a = CalculatorEngine(); a.type("12+34")
        assertFalse(a.secretMatches("34"))
        assertFalse(a.secretMatches("1234"))
        val b = CalculatorEngine(); b.type("1+1=")
        assertFalse(b.secretMatches("2"))
        val c = CalculatorEngine(); c.type("123.4")
        assertFalse(c.secretMatches("1234"))
    }

    @Test fun secretResetByClear() {
        val e = CalculatorEngine(); e.type("1234"); e.clear()
        assertFalse(e.secretMatches("1234"))
        e.type("1234")
        assertTrue(e.secretMatches("1234"))
    }
}
