package com.example.tapcounter.headset

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed headset settings.
 *
 * Create the store once per process, e.g. at top level of a file:
 *   val Context.headsetStore by preferencesDataStore(name = "headset_settings")
 * then HeadsetSettingsRepository(context.headsetStore).
 */
class HeadsetSettingsRepository(private val store: DataStore<Preferences>) {

    internal object Keys {
        val HARDWARE_LOCK = booleanPreferencesKey("hardware_lock")
        val DEBOUNCE_MS = longPreferencesKey("debounce_ms")
        val MULTI_TAP_MS = longPreferencesKey("multi_tap_ms")
        val LONG_PRESS_MS = longPreferencesKey("long_press_ms")
        val CONFIRM_TICK = booleanPreferencesKey("confirmation_tick")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val KEEP_ALIVE = booleanPreferencesKey("keep_alive_audio")
        /** Absent = first run (defaults apply). Present, even if empty = user's own mapping. */
        val BINDINGS = stringSetPreferencesKey("bindings")
    }

    val settings: Flow<HeadsetSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun setHardwareLock(locked: Boolean) = store.edit { it[Keys.HARDWARE_LOCK] = locked }

    suspend fun setConfirmationTick(enabled: Boolean) = store.edit { it[Keys.CONFIRM_TICK] = enabled }

    suspend fun setVibrationEnabled(enabled: Boolean) = store.edit { it[Keys.VIBRATION] = enabled }

    suspend fun setKeepAliveAudio(enabled: Boolean) = store.edit { it[Keys.KEEP_ALIVE] = enabled }

    suspend fun setTiming(debounceMs: Long, multiTapMs: Long, longPressMs: Long) = store.edit {
        it[Keys.DEBOUNCE_MS] = debounceMs.coerceIn(HeadsetSettings.DEBOUNCE_RANGE)
        it[Keys.MULTI_TAP_MS] = multiTapMs.coerceIn(HeadsetSettings.MULTI_TAP_RANGE)
        it[Keys.LONG_PRESS_MS] = longPressMs.coerceIn(HeadsetSettings.LONG_PRESS_RANGE)
    }

    /** @throws ForbiddenActionException when [action] is RESET; nothing is written then. */
    suspend fun setBinding(keyCode: Int, gesture: Gesture, action: Action) {
        BindingRules.requireAssignable(action)
        store.edit { prefs ->
            val current = LinkedHashMap(prefs.currentBindings())
            if (action == Action.NONE) current.remove(keyCode to gesture) else current[keyCode to gesture] = action
            prefs[Keys.BINDINGS] = SettingsCodec.encode(current)
        }
    }

    suspend fun clearBindingsForKey(keyCode: Int) = store.edit { prefs ->
        val kept = prefs.currentBindings().filterKeys { it.first != keyCode }
        prefs[Keys.BINDINGS] = SettingsCodec.encode(kept)
    }

    suspend fun resetBindingsToDefaults() = store.edit { it.remove(Keys.BINDINGS) }

    private fun Preferences.currentBindings(): Map<Pair<Int, Gesture>, Action> =
        this[Keys.BINDINGS]?.let(SettingsCodec::decode) ?: HeadsetSettings.defaultBindings()

    private fun Preferences.toSettings(): HeadsetSettings = HeadsetSettings(
        hardwareLock = this[Keys.HARDWARE_LOCK] ?: false,
        debounceMs = this[Keys.DEBOUNCE_MS] ?: HeadsetSettings.DEFAULT_DEBOUNCE_MS,
        multiTapWindowMs = this[Keys.MULTI_TAP_MS] ?: HeadsetSettings.DEFAULT_MULTI_TAP_MS,
        longPressMs = this[Keys.LONG_PRESS_MS] ?: HeadsetSettings.DEFAULT_LONG_PRESS_MS,
        confirmationTick = this[Keys.CONFIRM_TICK] ?: false,
        vibrationEnabled = this[Keys.VIBRATION] ?: true,
        keepAliveAudio = this[Keys.KEEP_ALIVE] ?: true,
        bindings = currentBindings(),
    ).sanitized()
}
