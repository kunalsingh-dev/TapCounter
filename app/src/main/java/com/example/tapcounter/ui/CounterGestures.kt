package com.example.tapcounter.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope

/** Fingers needed for the stealth combination (4 or 5 both work). */
private const val COMBO_FINGERS = 4

/** A third finger landing this soon after the first means "stealth gesture", so the first count is taken back. */
private const val UNDO_WINDOW_MS = 300L

/**
 * Whole-screen gestures:
 *  - ONE finger touches down -> [onTap] fires at that very instant: no waiting for release and
 *    no look-ahead. A finger that stays down counts once; lift and touch again for the next count.
 *  - If a 3rd finger lands within 300 ms (the start of a 4/5-finger stealth gesture), [onUndo]
 *    takes back that first count so entering stealth never adds a stray count.
 *  - 4 or 5 fingers held for [comboHoldMs] -> [onCombo] (stealth on/off).
 * Buttons and menus consume their own touches first, so they never count.
 */
suspend fun PointerInputScope.detectCounterGestures(
    tapEnabled: Boolean,
    comboHoldMs: Long,
    onTap: () -> Boolean,
    onUndo: () -> Unit,
    onCombo: () -> Unit,
) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = true)
        var counted = false
        if (tapEnabled) {
            counted = onTap() // instant; false when the touch-speed limit ignored it
        }
        var undone = false
        var comboFired = false
        var pressed = 1
        var holdStart = -1L
        var lastEventAt = first.uptimeMillis

        while (pressed > 0) {
            val waitMs: Long = if (holdStart >= 0 && !comboFired) {
                (comboHoldMs - (lastEventAt - holdStart)).coerceAtLeast(0L)
            } else {
                -1L
            }
            val event: PointerEvent? = if (waitMs >= 0) {
                withTimeoutOrNull(waitMs.coerceAtLeast(1L)) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                // Enough fingers stayed down for the whole hold time.
                comboFired = true
                onCombo()
                continue
            }
            lastEventAt = event.changes.maxOf { it.uptimeMillis }
            pressed = event.changes.count { it.pressed }
            if (pressed >= 3 && counted && !undone && lastEventAt - first.uptimeMillis <= UNDO_WINDOW_MS) {
                undone = true
                onUndo()
            }
            if (pressed >= COMBO_FINGERS) {
                if (holdStart < 0) holdStart = lastEventAt
            } else {
                holdStart = -1L
            }
        }
    }
}
