package com.example.tapcounter.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope

/** Fingers needed for the stealth combination (4 or 5 both work). */
private const val COMBO_FINGERS = 4

/**
 * After the first finger lands we wait this long for more fingers. If none come, the count
 * happens right then (finger still down), so counting does not wait for release.
 */
private const val MULTI_GUARD_MS = 50L

/**
 * Whole-screen gestures:
 *  - ONE finger touches down -> [onTap] fires immediately (after a 50 ms look for more fingers).
 *    A finger that stays down counts once, never repeatedly; lift and touch again for the next count.
 *  - 4 or 5 fingers held for [comboHoldMs] -> [onCombo] (stealth on/off), never counts.
 * Buttons and menus consume their own touches first, so they never count.
 */
suspend fun PointerInputScope.detectCounterGestures(
    tapEnabled: Boolean,
    comboHoldMs: Long,
    onTap: () -> Unit,
    onCombo: () -> Unit,
) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = true)
        var counted = false
        var multi = false
        var comboFired = false
        var pressed = 1
        var holdStart = -1L
        var lastEventAt = first.uptimeMillis
        val guardEnd = first.uptimeMillis + MULTI_GUARD_MS

        while (pressed > 0) {
            val waitMs: Long = when {
                !counted && !multi -> (guardEnd - lastEventAt).coerceAtLeast(0L)
                holdStart >= 0 && !comboFired -> (comboHoldMs - (lastEventAt - holdStart)).coerceAtLeast(0L)
                else -> -1L
            }
            val event: PointerEvent? = if (waitMs >= 0) {
                withTimeoutOrNull(waitMs.coerceAtLeast(1L)) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                if (!counted && !multi) {
                    counted = true
                    if (tapEnabled) onTap()
                } else if (holdStart >= 0 && !comboFired) {
                    comboFired = true
                    onCombo()
                }
                continue
            }
            lastEventAt = event.changes.maxOf { it.uptimeMillis }
            pressed = event.changes.count { it.pressed }
            if (pressed >= 2) multi = true
            if (pressed >= COMBO_FINGERS) {
                if (holdStart < 0) holdStart = lastEventAt
            } else {
                holdStart = -1L
            }
            // Very quick tap: released before the 50 ms look-ahead ended.
            if (pressed == 0 && !counted && !multi) {
                counted = true
                if (tapEnabled) onTap()
            }
        }
    }
}
