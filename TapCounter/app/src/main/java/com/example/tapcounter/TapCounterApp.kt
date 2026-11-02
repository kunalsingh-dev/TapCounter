package com.example.tapcounter

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.tapcounter.counter.CounterStore
import com.example.tapcounter.headset.HeadsetController
import com.example.tapcounter.headset.HeadsetSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val Context.headsetStore: DataStore<Preferences> by preferencesDataStore(name = "headset_settings")

class TapCounterApp : Application() {
    val counterStore: CounterStore by lazy { CounterStore(this) }
    val headsetSettings: HeadsetSettingsRepository by lazy { HeadsetSettingsRepository(headsetStore) }
    val headsetController: HeadsetController by lazy { HeadsetController(this, counterStore, headsetSettings) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Keep the counter's mala size equal to the setting, in every process start (UI or service).
        scope.launch { headsetSettings.settings.collect { counterStore.malaSize = it.malaSize } }
    }

    companion object {
        fun from(context: Context): TapCounterApp = context.applicationContext as TapCounterApp
    }
}
