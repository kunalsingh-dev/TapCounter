package com.example.tapcounter.headset

/**
 * Stores bindings as a set of strings "keyCode:GESTURE=ACTION", e.g. "85:DOUBLE=DECREMENT".
 * Decoding is defensive: malformed entries and any RESET entry are silently dropped.
 */
object SettingsCodec {

    fun encode(bindings: Map<Pair<Int, Gesture>, Action>): Set<String> =
        bindings.map { (id, action) ->
            BindingRules.requireAssignable(action)
            "${BindingRules.storageKey(id.first, id.second)}=${action.name}"
        }.toSet()

    fun decode(entries: Set<String>): Map<Pair<Int, Gesture>, Action> {
        val out = LinkedHashMap<Pair<Int, Gesture>, Action>()
        for (raw in entries.sorted()) {
            val eq = raw.indexOf('=')
            if (eq <= 0 || eq == raw.length - 1) continue
            val id = BindingRules.parseStorageKey(raw.substring(0, eq)) ?: continue
            val action = BindingRules.parseStoredAction(raw.substring(eq + 1)) ?: continue
            out[id] = action
        }
        return out
    }
}
