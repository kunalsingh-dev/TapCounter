package com.example.tapcounter.data

import java.time.LocalDate

data class DayPoint(val date: String, val total: Int, val malas: Int)

data class MalaSummary(val count: Int, val lastSec: Int?, val avgSec: Int?, val fastestSec: Int?)

/** Pure statistics for the Analytics screen. Works on the raw saved day records. */
object Analytics {

    /** The last [n] calendar days ending today, oldest first, missing days filled with 0. */
    fun lastDays(days: List<DayRecord>, n: Int, today: LocalDate): List<DayPoint> {
        val byDate = days.associateBy { it.date }
        return (n - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong()).toString()
            val r = byDate[d]
            DayPoint(d, r?.total ?: 0, r?.malas ?: 0)
        }
    }

    /** 24 totals: counts per hour of the day over the last [n] days. */
    fun hourTotals(days: List<DayRecord>, n: Int, today: LocalDate): List<Int> {
        val from = today.minusDays((n - 1).toLong()).toString()
        val to = today.toString()
        val out = IntArray(24)
        for (r in days) {
            if (r.date >= from && r.date <= to) {
                for (h in 0 until 24) out[h] += r.hours.getOrElse(h) { 0 }
            }
        }
        return out.toList()
    }

    fun best(points: List<DayPoint>): DayPoint? = points.filter { it.total > 0 }.maxByOrNull { it.total }

    /** Average counts per day over the whole range (empty days included). */
    fun average(points: List<DayPoint>): Int =
        if (points.isEmpty()) 0 else (points.sumOf { it.total } + points.size / 2) / points.size

    /** Consecutive days with at least one count, ending today (or yesterday if today has none yet). */
    fun streak(days: List<DayRecord>, today: LocalDate): Int {
        val byDate = days.associateBy { it.date }
        var d = today
        if ((byDate[d.toString()]?.total ?: 0) == 0) d = d.minusDays(1)
        var count = 0
        while ((byDate[d.toString()]?.total ?: 0) > 0) {
            count++
            d = d.minusDays(1)
        }
        return count
    }

    fun goalDaysMet(points: List<DayPoint>, goalMalas: Int): Int =
        if (goalMalas <= 0) 0 else points.count { it.malas >= goalMalas }
}

object MalaStats {
    /** [secondsNewestFirst]: durations of finished malas, newest first. */
    fun summarize(secondsNewestFirst: List<Int>): MalaSummary {
        if (secondsNewestFirst.isEmpty()) return MalaSummary(0, null, null, null)
        val avg = (secondsNewestFirst.sum() + secondsNewestFirst.size / 2) / secondsNewestFirst.size
        return MalaSummary(
            count = secondsNewestFirst.size,
            lastSec = secondsNewestFirst.first(),
            avgSec = avg,
            fastestSec = secondsNewestFirst.minOrNull(),
        )
    }
}
