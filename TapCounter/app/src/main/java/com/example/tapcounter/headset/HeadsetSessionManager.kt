package com.example.tapcounter.headset

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media.VolumeProviderCompat

/**
 * Owns the MediaSessionCompat. Android delivers media-button events only to an ACTIVE session,
 * and prefers the one that most recently reported STATE_PLAYING.
 * Must be used from the main thread.
 */
class HeadsetSessionManager(
    private val context: Context,
    private val controller: HeadsetController,
    private val sessionActivity: PendingIntent?,
) {
    private var session: MediaSessionCompat? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            val intent = mediaButtonEvent ?: return false
            val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                ?: return false
            return controller.onKeyEvent(event)
        }
    }

    /**
     * @param captureVolume when true the session also owns the phone's volume keys (remote volume
     * provider), so they count even with the screen off. Side effect: while this is on, the
     * hardware volume keys no longer change the media volume.
     */
    fun start(captureVolume: Boolean) {
        if (session != null) return
        val s = MediaSessionCompat(context, TAG)
        s.setCallback(callback)
        sessionActivity?.let { s.setSessionActivity(it) }
        s.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Tap Counter")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Headset button control")
                .build(),
        )
        s.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        if (captureVolume) {
            s.setPlaybackToRemote(object : VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, 100, 50) {
                override fun onAdjustVolume(direction: Int) {
                    // Delivered on a binder thread: hop to the main thread.
                    mainHandler.post { controller.onVolumeAdjust(direction) }
                }
            })
        }
        s.isActive = true
        session = s
        controller.updateStatus { it.copy(sessionActive = true) }
    }

    fun stop() {
        session?.let {
            it.isActive = false
            it.release()
        }
        session = null
        controller.updateStatus { it.copy(sessionActive = false) }
    }

    /** Re-take control: a brand-new session becomes the most recent PLAYING session. */
    fun restart(captureVolume: Boolean) {
        stop()
        start(captureVolume)
    }

    private companion object {
        const val TAG = "TapCounterHeadset"
    }
}
