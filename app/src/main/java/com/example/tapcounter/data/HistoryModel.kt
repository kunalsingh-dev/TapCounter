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

enum class ResetType { MALA, ALL }

/** One entry of the reset log: what was reset, when, and the numbers at that moment. */
data class ResetEvent(
    /** Local date-time, ISO, e.g. 2026-11-02T15:12:03 */
    val at: String,
    val type: ResetType,
    val ringCount: Int,
    val todayTotal: Int,
    val allTime: Int,
    val allMalas: Int,
)

object ResetCodec {
    fun encode(e: ResetEvent): String =
        "${e.type.name}|${e.at}|${e.ringCount}|${e.todayTotal}|${e.allTime}|${e.allMalas}"

    fun decode(raw: String): ResetEvent? {
        val p = raw.split('|')
        if (p.size != 6) return null
        val type = ResetType.entries.firstOrNull { it.name == p[0] } ?: return null
        return ResetEvent(
            at = p[1],
            type = type,
            ringCount = p[2].toIntOrNull() ?: return null,
            todayTotal = p[3].toIntOrNull() ?: return null,
            allTime = p[4].toIntOrNull() ?: return null,
            allMalas = p[5].toIntOrNull() ?: return null,
        )
    }
}

/**
 * "Reset ALL" keeps every day record (the data stays saved) and remembers the totals at that
 * moment. Displayed all-time / today numbers are the raw totals minus this baseline.
 */
data class Baseline(
    val allTime: Int = 0,
    val allMalas: Int = 0,
    val todayDate: String = "",
    val todayTotal: Int = 0,
    val todayMalas: Int = 0,
)

object BaselineCodec {
    fun encode(b: Baseline): String = "${b.allTime}|${b.allMalas}|${b.todayDate}|${b.todayTotal}|${b.todayMalas}"

    fun decode(raw: String): Baseline? {
        val p = raw.split('|')
        if (p.size != 5) return null
        return Baseline(
            allTime = p[0].toIntOrNull() ?: return null,
            allMalas = p[1].toIntOrNull() ?: return null,
            todayDate = p[2],
            todayTotal = p[3].toIntOrNull() ?: return null,
            todayMalas = p[4].toIntOrNull() ?: return null,
        )
    }
}

data class Summary(val todayTotal: Int, val todayMalas: Int, val allTime: Int, val allMalas: Int)

object SummaryMath {
    fun compute(days: List<DayRecord>, b: Baseline, today: String): Summary {
        val t = days.firstOrNull { it.date == today }
        val sameDay = b.todayDate == today
        return Summary(
            todayTotal = ((t?.total ?: 0) - if (sameDay) b.todayTotal else 0).coerceAtLeast(0),
            todayMalas = ((t?.malas ?: 0) - if (sameDay) b.todayMalas else 0).coerceAtLeast(0),
            allTime = (days.sumOf { it.total } - b.allTime).coerceAtLeast(0),
            allMalas = (days.sumOf { it.malas } - b.allMalas).coerceAtLeast(0),
        )
    }

    /** Baseline that makes every displayed number 0 right now. */
    fun baselineNow(days: List<DayRecord>, today: String): Baseline {
        val t = days.firstOrNull { it.date == today }
        return Baseline(
            allTime = days.sumOf { it.total },
            allMalas = days.sumOf { it.malas },
            todayDate = today,
            todayTotal = t?.total ?: 0,
            todayMalas = t?.malas ?: 0,
        )
    }
}
