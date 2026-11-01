package com.example.tapcounter.counter

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

data class CounterState(val count: Int = 0, val stealth: Boolean = false)

/**
 * The ONLY surface the headset layer can touch. There is deliberately no reset() here, so
 * a headphone button can never reset the counter, even through a coding mistake.
 * If your app already has a counter, make it implement this interface.
 */
interface CounterActions {
    fun increment()
    fun decrement()
    fun toggleStealth()
    fun forceStealthOn()
}

class CounterStore(context: Context) : CounterActions {
    private val prefs = context.applicationContext.getSharedPreferences("counter", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        CounterState(prefs.getInt(KEY_COUNT, 0), prefs.getBoolean(KEY_STEALTH, false)),
    )
    val state: StateFlow<CounterState> = _state.asStateFlow()

    override fun increment() = change { it.copy(count = it.count + 1) }
    override fun decrement() = change { it.copy(count = (it.count - 1).coerceAtLeast(0)) }
    override fun toggleStealth() = change { it.copy(stealth = !it.stealth) }
    override fun forceStealthOn() = change { it.copy(stealth = true) }

    // UI-only operations (not part of CounterActions):
    fun reset() = change { it.copy(count = 0) }
    fun setStealth(on: Boolean) = change { it.copy(stealth = on) }

    private fun change(f: (CounterState) -> CounterState) {
        val n = _state.updateAndGet(f)
        prefs.edit().putInt(KEY_COUNT, n.count).putBoolean(KEY_STEALTH, n.stealth).apply()
    }

    private companion object {
        const val KEY_COUNT = "count"
        const val KEY_STEALTH = "stealth"
    }
}
