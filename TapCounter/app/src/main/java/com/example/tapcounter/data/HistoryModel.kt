package com.example.tapcounter.data

/** One calendar day: 24 hourly buckets plus how many malas were completed. */
data class DayRecord(val date: String, val hours: List<Int>, val malas: Int) {
    val total: Int get() = hours.sum()

    fun withCount(hour: Int, malaCompleted: Boolean): DayRecord {
        val h = hours.toMutableList()
        val i = hour.coerceIn(0, 23)
        h[i] = h[i] + 1
        return copy(hours = h, malas = malas + if (malaCompleted) 1 else 0)
    }
}

/** Storage format: "malas|h0,h1,...,h23". Malformed entries decode to null. */
object HistoryCodec {
    fun emptyDay(date: String) = DayRecord(date, List(24) { 0 }, 0)

    fun encode(r: DayRecord): String = "${r.malas}|${r.hours.joinToString(",")}"

    fun decode(date: String, raw: String): DayRecord? {
        val parts = raw.split('|')
        if (parts.size != 2) return null
        val malas = parts[0].toIntOrNull() ?: return null
        val hours = parts[1].split(',').map { it.toIntOrNull() ?: return null }
        if (hours.size != 24) return null
        return DayRecord(date, hours, malas)
    }
}
