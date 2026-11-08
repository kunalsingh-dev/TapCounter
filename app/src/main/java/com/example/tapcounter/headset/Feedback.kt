package com.example.tapcounter.headset

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/** Haptic + optional audible confirmation. */
class Feedback(context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Short single buzz: a counted tap. */
    fun count(vibrate: Boolean, tick: Boolean) {
        if (vibrate) vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        if (tick) playTick(1500)
    }

    /** Long triple pattern: a mala (full round) was completed. */
    fun mala(vibrate: Boolean, tick: Boolean) {
        if (vibrate) vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250, 120, 500), -1))
        if (tick) playTick(700)
    }

    /** Rising long pattern: today's goal reached. */
    fun goal(vibrate: Boolean, tick: Boolean) {
        if (vibrate) vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 200, 100, 700), -1))
        if (tick) playTick(1000)
    }

    /** Distinct double buzz: stealth on/off. */
    fun stealthForceOn(vibrate: Boolean, tick: Boolean) {
        if (vibrate) {
            vibrate(VibrationEffect.createWaveform(longArrayOf(0, 45, 80, 45), -1))
        }
        if (tick) playTick(900)
    }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    effect,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
                )
            }
        } catch (_: RuntimeException) {
            // Vibration is best-effort feedback; never crash the key path.
        }
    }

    /** ~20 ms quiet sine blip routed to the active output (the headset). */
    private fun playTick(freqHz: Int) {
        try {
            val sampleRate = 22050
            val n = sampleRate / 50
            val pcm = ShortArray(n) { i ->
                val envelope = sin(PI * i / n)
                (sin(2 * PI * freqHz * i / sampleRate) * envelope * 3000).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, n)
            track.play()
            handler.postDelayed({ runCatching { track.release() } }, 300)
        } catch (_: Exception) {
            // best-effort
        }
    }
}
