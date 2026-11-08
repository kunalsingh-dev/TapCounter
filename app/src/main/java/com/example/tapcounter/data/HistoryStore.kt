package com.example.tapcounter.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Automatic date-wise and hour-wise log, plus the reset log. Every counted tap (screen, headset,
 * volume key) updates memory at once; disk writes are batched (at most every 250 ms) so fast
 * counting never waits for storage. [flush] writes immediately.
 */
class HistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val dirtyDates = linkedSetOf<String>()
    private var saveQueued = false
    private var last: LastRecord? = null
    private data class LastRecord(val date: String, val hour: Int, val completed: Boolean)
    private val saveRunnable = Runnable {
        saveQueued = false
        saveNow()
    }

    private val _days = MutableStateFlow(loadDays())
    /** Newest day first. */
    val days: StateFlow<List<DayRecord>> = _days.asStateFlow()

    private val _resets = MutableStateFlow(loadResets())
    /** Newest reset first. */
    val resets: StateFlow<List<ResetEvent>> = _resets.asStateFlow()

    private val _malas = MutableStateFlow(loadMalas())
    /** Durations of finished malas, newest first (latest 300). */
    val malas: StateFlow<List<MalaRecord>> = _malas.asStateFlow()

    private val _baseline = MutableStateFlow(loadBaseline())
    val baseline: StateFlow<Baseline> = _baseline.asStateFlow()

    fun record(malaCompleted: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val date = now.toLocalDate().toString() // yyyy-MM-dd
        val list = _days.value
        val head = list.firstOrNull()
        val headIsToday = head != null && head.date == date
        val base = if (headIsToday && head != null) head
        else list.firstOrNull { it.date == date } ?: HistoryCodec.emptyDay(date)
        val updated = base.withCount(now.hour, malaCompleted)
        _days.value = if (headIsToday) {
            listOf(updated) + list.drop(1)
        } else {
            (list.filter { it.date != date } + updated).sortedByDescending { it.date }
        }
        dirtyDates.add(date)
        last = LastRecord(date, now.hour, malaCompleted)
        queueSave()
    }

    /** Takes back the most recent [record] (used only to correct an accidental multi-finger touch). */
    fun undoLast() {
        val l = last ?: return
        last = null
        val list = _days.value
        val day = list.firstOrNull { it.date == l.date } ?: return
        val updated = day.withoutCount(l.hour, l.completed)
        _days.value = list.map { if (it.date == l.date) updated else it }
        dirtyDates.add(l.date)
        queueSave()
    }

    fun addMalaRecord(record: MalaRecord) {
        prefs.edit().putString("$MALA_PREFIX${System.currentTimeMillis()}", MalaRecordCodec.encode(record)).apply()
        _malas.value = (listOf(record) + _malas.value).take(MAX_MALA_RECORDS)
    }

    fun summary(today: String = LocalDate.now().toString()): Summary =
        SummaryMath.compute(_days.value, _baseline.value, today)

    fun addReset(event: ResetEvent) {
        prefs.edit()
            .putString("$RESET_PREFIX${System.currentTimeMillis()}:${_resets.value.size}", ResetCodec.encode(event))
            .apply()
        _resets.value = (listOf(event) + _resets.value).sortedByDescending { it.at }
    }

    /** After this, every displayed total reads 0; the day records themselves stay saved. */
    fun markAllReset(today: String = LocalDate.now().toString()) {
        val b = SummaryMath.baselineNow(_days.value, today)
        prefs.edit().putString(KEY_BASELINE, BaselineCodec.encode(b)).apply()
        _baseline.value = b
    }

    fun flush() {
        handler.removeCallbacks(saveRunnable)
        saveQueued = false
        saveNow()
    }

    private fun queueSave() {
        if (saveQueued) return // fixed cadence: never postponed by continuous tapping
        saveQueued = true
        handler.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    private fun saveNow() {
        if (dirtyDates.isEmpty()) return
        val editor = prefs.edit()
        val current = _days.value
        for (d in dirtyDates) {
            current.firstOrNull { it.date == d }?.let { editor.putString(DAY_PREFIX + d, HistoryCodec.encode(it)) }
        }
        editor.apply()
        dirtyDates.clear()
    }

    private fun loadDays(): List<DayRecord> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(DAY_PREFIX) && value is String) {
                HistoryCodec.decode(key.removePrefix(DAY_PREFIX), value)
            } else {
                null
            }
        }.sortedByDescending { it.date }

    private fun loadResets(): List<ResetEvent> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(RESET_PREFIX) && value is String) ResetCodec.decode(value) else null
        }.sortedByDescending { it.at }

    private fun loadMalas(): List<MalaRecord> =
        prefs.all.entries
            .filter { it.key.startsWith(MALA_PREFIX) && it.value is String }
            .sortedByDescending { it.key.removePrefix(MALA_PREFIX).toLongOrNull() ?: 0L }
            .take(MAX_MALA_RECORDS)
            .mapNotNull { MalaRecordCodec.decode(it.value as String) }

    private fun loadBaseline(): Baseline =
        prefs.getString(KEY_BASELINE, null)?.let { BaselineCodec.decode(it) } ?: Baseline()

    private companion object {
        const val DAY_PREFIX = "d:"
        const val RESET_PREFIX = "r:"
        const val MALA_PREFIX = "m:"
        const val MAX_MALA_RECORDS = 300
        const val KEY_BASELINE = "baseline"
        const val SAVE_DELAY_MS = 250L
    }
}
