package com.example.tapcounter.data

import android.content.Context
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Automatic date-wise and hour-wise log. Every counted tap (screen, headset, volume key)
 * is written immediately; nothing to press to save.
 */
class HistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)

    private val _days = MutableStateFlow(load())
    /** Newest day first. */
    val days: StateFlow<List<DayRecord>> = _days.asStateFlow()

    fun record(malaCompleted: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val date = now.toLocalDate().toString() // yyyy-MM-dd
        val list = _days.value
        val base = list.firstOrNull { it.date == date } ?: HistoryCodec.emptyDay(date)
        val updated = base.withCount(now.hour, malaCompleted)
        prefs.edit().putString(PREFIX + date, HistoryCodec.encode(updated)).apply()
        _days.value = (list.filter { it.date != date } + updated).sortedByDescending { it.date }
    }

    private fun load(): List<DayRecord> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(PREFIX) && value is String) HistoryCodec.decode(key.removePrefix(PREFIX), value) else null
        }.sortedByDescending { it.date }

    private companion object {
        const val PREFIX = "d:"
    }
}
