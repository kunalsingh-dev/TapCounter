package com.example.tapcounter.headset

/** Thrown when someone tries to map a forbidden action (RESET) to a headphone button. */
class ForbiddenActionException(action: Action) :
    IllegalArgumentException("Action $action cannot be assigned to a headphone button")

/** One headset mapping: (key, gesture) -> action. */
data class HeadsetBinding(val keyCode: Int, val gesture: Gesture, val action: Action)

object BindingRules {

    /** Single choke point used by the UI, the repository and the DataStore reader. */
    fun requireAssignable(action: Action): Action {
        if (!action.isHeadsetAssignable) throw ForbiddenActionException(action)
        return action
    }

    fun create(keyCode: Int, gesture: Gesture, action: Action): HeadsetBinding =
        HeadsetBinding(keyCode, gesture, requireAssignable(action))

    /** Stable storage key, e.g. "85:DOUBLE". */
    fun storageKey(keyCode: Int, gesture: Gesture): String = "$keyCode:${gesture.name}"

    /** Parses "85:DOUBLE" -> Pair(85, DOUBLE), or null when malformed. */
    fun parseStorageKey(raw: String): Pair<Int, Gesture>? {
        val parts = raw.split(':')
        if (parts.size != 2) return null
        val key = parts[0].toIntOrNull() ?: return null
        val gesture = Gesture.entries.firstOrNull { it.name == parts[1] } ?: return null
        return key to gesture
    }

    /**
     * Reads an action from storage. Unknown names AND "RESET" (e.g. from a tampered or
     * old backup) come back as null so the binding is dropped instead of executed.
     */
    fun parseStoredAction(raw: String): Action? {
        val action = Action.entries.firstOrNull { it.name == raw } ?: return null
        return if (action.isHeadsetAssignable) action else null
    }
}
