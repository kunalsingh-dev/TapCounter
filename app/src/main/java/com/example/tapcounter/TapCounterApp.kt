package com.example.tapcounter

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.tapcounter.counter.CounterStore
import com.example.tapcounter.headset.HeadsetController
import com.example.tapcounter.headset.HeadsetSettingsRepository

val Context.headsetStore: DataStore<Preferences> by preferencesDataStore(name = "headset_settings")

class TapCounterApp : Application() {
    val counterStore: CounterStore by lazy { CounterStore(this) }
    val headsetSettings: HeadsetSettingsRepository by lazy { HeadsetSettingsRepository(headsetStore) }
    val headsetController: HeadsetController by lazy { HeadsetController(this, counterStore, headsetSettings) }

    companion object {
        fun from(context: Context): TapCounterApp = context.applicationContext as TapCounterApp
    }
}
