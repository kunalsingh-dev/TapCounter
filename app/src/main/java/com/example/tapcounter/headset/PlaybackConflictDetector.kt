package com.example.tapcounter.headset

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper

/**
 * Android has no API that says "who owns the media buttons". Best available heuristic:
 * if more media players are active than our own keep-alive track, another media app is
 * playing and has probably become the preferred button target.
 */
class PlaybackConflictDetector(
    context: Context,
    private val ownMediaPlayers: () -> Int,
    private val onChange: (Boolean) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var last = false

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            evaluate(configs.orEmpty())
        }
    }

    fun start() {
        if (started) return
        started = true
        audioManager.registerAudioPlaybackCallback(callback, handler)
        evaluate(audioManager.activePlaybackConfigurations)
    }

    fun stop() {
        if (!started) return
        started = false
        audioManager.unregisterAudioPlaybackCallback(callback)
    }

    private fun evaluate(configs: List<AudioPlaybackConfiguration>) {
        val media = configs.count { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
        val conflict = media > ownMediaPlayers()
        if (conflict != last) {
            last = conflict
            onChange(conflict)
        }
    }
}
