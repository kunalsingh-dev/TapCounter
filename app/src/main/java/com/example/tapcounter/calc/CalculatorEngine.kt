package com.example.tapcounter.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A plain pocket calculator (immediate execution: 2 + 3 × 4 = 20). It is the disguise shown
 * instead of the counter. [secretMatches] tells whether what was typed since the last
 * operator/clear is exactly the secret code, so typing the code and pressing "=" can open
 * the counter while looking like an ordinary number entry to anyone watching.
 */
class CalculatorEngine {
    private var acc: BigDecimal? = null
    private var op: Char? = null
    private var entry = "0"
    private var typing = false
    private var error = false
    /** Raw keys typed for the current number (keeps leading zeros), used for the secret code. */
    private var typed = ""

    val display: String get() = if (error) "Error" else entry

    fun digit(d: Int) {
        if (error) clear()
        if (!typing) {
            entry = d.toString()
            typing = true
            typed = ""
        } else if (entry == "0") {
            entry = d.toString()
        } else if (entry.count { it.isDigit() } < MAX_DIGITS) {
            entry += d
        }
        typed += d
    }

    fun dot() {
        if (error) clear()
        if (!typing) {
            entry = "0."
            typing = true
            typed = ""
        } else if ('.' !in entry) {
            entry += "."
        }
        typed += "."
    }

    fun operator(o: Char) {
        if (error) return
        val current = value()
        if (op != null && typing) {
            val r = apply(acc ?: current, op!!, current)
            if (r == null) {
                fail()
                return
            }
            acc = r
            entry = fmt(r)
        } else if (op == null) {
            acc = current
        }
        op = o
        typing = false
        typed = ""
    }

    fun equalsKey() {
        if (error) return
        val o = op
        val a = acc
        if (o != null && a != null) {
            val r = apply(a, o, value())
            if (r == null) {
                fail()
                return
            }
            entry = fmt(r)
        }
        acc = null
        op = null
        typing = false
        typed = ""
    }

    fun negate() {
        if (error || entry == "0") return
        entry = if (entry.startsWith("-")) entry.drop(1) else "-$entry"
        typed += "±"
    }

    fun percent() {
        if (error) return
        entry = fmt(value().divide(BigDecimal(100)))
        typed += "%"
    }

    fun clear() {
        acc = null
        op = null
        entry = "0"
        typing = false
        error = false
        typed = ""
    }

    /** True when the digits typed so far (and nothing else) equal [code] and no calculation is pending. */
    fun secretMatches(code: String): Boolean =
        !error && code.isNotEmpty() && op == null && acc == null && typed == code

    private fun value(): BigDecimal = entry.toBigDecimalOrNull() ?: BigDecimal.ZERO

    private fun apply(a: BigDecimal, o: Char, b: BigDecimal): BigDecimal? = when (o) {
        '+' -> a.add(b)
        '−' -> a.subtract(b)
        '×' -> a.multiply(b)
        '÷' -> if (b.signum() == 0) null else a.divide(b, 10, RoundingMode.HALF_UP)
        else -> b
    }

    private fun fmt(r: BigDecimal): String {
        val plain = r.stripTrailingZeros().toPlainString()
        return if (plain.length <= MAX_LEN) plain
        else r.round(MathContext(9)).stripTrailingZeros().toString()
    }

    private fun fail() {
        error = true
        acc = null
        op = null
        typing = false
        typed = ""
    }

    private companion object {
        const val MAX_DIGITS = 12
        const val MAX_LEN = 14
    }
}
