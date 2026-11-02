package com.example.tapcounter.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope

/** Two fingers must stay down this long to toggle stealth (the "special combination"). */
private const val COMBO_HOLD_MS = 900L

/** A counted tap must be released within this time. A finger that rests longer never counts. */
private const val TAP_MAX_MS = 450L

/**
 * Whole-screen gestures:
 *  - ONE finger, short touch, no movement  -> [onTap]  (counts +1 when released)
 *  - ONE finger held longer than 450 ms     -> nothing (a held press never counts)
 *  - TWO fingers held for 0.9 s             -> [onCombo] (stealth on/off), never counts
 * Buttons and menus consume their own touches first, so they never count.
 */
suspend fun PointerInputScope.detectCounterGestures(
    tapEnabled: Boolean,
    onTap: () -> Unit,
    onCombo: () -> Unit,
) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = true)
        val slop = viewConfiguration.touchSlop
        val downAt = first.uptimeMillis
        var lastEventAt = downAt
        var pressed = 1
        var maxPointers = 1
        var moved = false
        var comboFired = false
        var holdStart = -1L

        while (pressed > 0) {
            val remaining = if (holdStart >= 0 && !comboFired) COMBO_HOLD_MS - (lastEventAt - holdStart) else -1L
            val event: PointerEvent? = if (remaining >= 0) {
                withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent() }
            } else {
                awaitPointerEvent()
            }
            if (event == null) {
                // Two fingers stayed down for the whole hold time.
                comboFired = true
                onCombo()
                continue
            }
            lastEventAt = event.changes.maxOf { it.uptimeMillis }
            pressed = event.changes.count { it.pressed }
            if (pressed > maxPointers) maxPointers = pressed
            event.changes.firstOrNull { it.id == first.id }?.let { c ->
                if ((c.position - first.position).getDistance() > slop) moved = true
            }
            if (pressed >= 2) {
                if (holdStart < 0) holdStart = lastEventAt
            } else {
                holdStart = -1L
            }
        }

        val duration = lastEventAt - downAt
        if (tapEnabled && !comboFired && maxPointers == 1 && !moved && duration <= TAP_MAX_MS) onTap()
    }
}
