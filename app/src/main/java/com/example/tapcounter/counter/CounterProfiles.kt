package com.example.tapcounter.counter

/** One counter. Each has its own count, history, name and secret code. */
data class CounterProfile(val id: String, val name: String, val code: String)

/** Pure rules for several counters, each opened by its own secret code on the calculator. */
object CounterProfiles {
    const val MAIN_ID = "main"
    const val MIN_CODE = 4
    const val MAX_CODE = 8

    fun isValidCode(code: String): Boolean = code.length in MIN_CODE..MAX_CODE && code.all { it in '0'..'9' }

    /** Null when [code] may be used; otherwise a message. [ignoreId] = the counter being edited. */
    fun codeError(all: List<CounterProfile>, code: String, ignoreId: String?): String? = when {
        !isValidCode(code) -> "Use $MIN_CODE to $MAX_CODE digits."
        all.any { it.id != ignoreId && it.code == code } -> "That code already opens another counter."
        else -> null
    }

    /** Which counter does a typed code open? */
    fun findByCode(all: List<CounterProfile>, code: String): CounterProfile? = all.firstOrNull { it.code == code }

    fun newId(existing: List<CounterProfile>): String {
        var n = 1
        while (existing.any { it.id == "c$n" }) n++
        return "c$n"
    }

    /** Stored as "code|name"; the name may contain any character. */
    fun encodeEntry(p: CounterProfile): String = "${p.code}|${p.name}"

    fun decodeEntry(id: String, raw: String): CounterProfile? {
        val i = raw.indexOf('|')
        if (i <= 0) return null
        val code = raw.substring(0, i)
        if (!isValidCode(code)) return null
        return CounterProfile(id, raw.substring(i + 1), code)
    }

    fun encodeOrder(ids: List<String>): String = ids.joinToString(",")

    fun decodeOrder(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Screen brightness while in stealth. */
object StealthBrightness {
    /**
     * Window brightness 0f..1f.
     * - Black screen on OLED: [blackPct] (1% default, pixels are off anyway).
     * - Black screen on LCD/IPS: 0f = the dimmest the phone allows (the backlight can't be fully off).
     * - Any disguise template: [disguisePct].
     */
    fun pick(disguise: Int, lcd: Boolean, blackPct: Int, disguisePct: Int): Float =
        if (disguise == 0) {
            if (lcd) 0f else blackPct.coerceIn(1, 100) / 100f
        } else {
            disguisePct.coerceIn(1, 100) / 100f
        }
}
