package com.example.tapcounter.headset

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays a 1-second buffer of +1/-1 LSB samples (about -90 dBFS, inaudible) in an endless loop.
 * Why: Android routes media buttons to the app that most recently PLAYED audio. Without a
 * real playing stream, a MediaSession that merely claims STATE_PLAYING can be deprioritised.
 * It deliberately does NOT request audio focus, so it never pauses the user's music.
 * Cost: the Bluetooth link stays in A2DP streaming mode (a little more headset/phone battery).
 */
class KeepAliveAudio {
    private var track: AudioTrack? = null

    val isPlaying: Boolean
        get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun start(): Boolean {
        if (isPlaying) return true
        stop()
        return try {
            val pcm = ShortArray(SAMPLE_RATE) { i -> (if (i % 2 == 0) 1 else -1).toShort() }
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setLoopPoints(0, pcm.size, -1)
            t.play()
            track = t
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }

    private companion object {
        const val SAMPLE_RATE = 8000
    }
}
