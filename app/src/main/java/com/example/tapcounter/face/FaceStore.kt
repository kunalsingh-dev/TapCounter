package com.example.tapcounter.face

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The owner's face profile: a few embeddings (numbers), never a photo. Private app storage, never uploaded. */
class FaceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("face", Context.MODE_PRIVATE)
    private val _templates = MutableStateFlow(load())
    val templates: StateFlow<List<FloatArray>> = _templates.asStateFlow()

    fun save(list: List<FloatArray>) {
        prefs.edit().putString(KEY, FaceTemplateCodec.encode(list)).apply()
        _templates.value = list
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _templates.value = emptyList()
    }

    private fun load(): List<FloatArray> = FaceTemplateCodec.decode(prefs.getString(KEY, "") ?: "")

    private companion object {
        const val KEY = "templates"
    }
}
