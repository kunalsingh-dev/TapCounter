package com.example.tapcounter.headset

/**
 * Key codes a headset can send through a MediaSession. Literal ints (same values as
 * android.view.KeyEvent.KEYCODE_*) so this file and its tests run on the plain JVM.
 */
object HeadsetKeys {
    const val HEADSETHOOK = 79
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_PLAY = 126
    const val MEDIA_PAUSE = 127

    val supported: Set<Int> = setOf(
        HEADSETHOOK, MEDIA_PLAY_PAUSE, MEDIA_STOP, MEDIA_NEXT, MEDIA_PREVIOUS, MEDIA_PLAY, MEDIA_PAUSE,
    )

    fun name(keyCode: Int): String = when (keyCode) {
        HEADSETHOOK -> "KEYCODE_HEADSETHOOK"
        MEDIA_PLAY_PAUSE -> "KEYCODE_MEDIA_PLAY_PAUSE"
        MEDIA_STOP -> "KEYCODE_MEDIA_STOP"
        MEDIA_NEXT -> "KEYCODE_MEDIA_NEXT"
        MEDIA_PREVIOUS -> "KEYCODE_MEDIA_PREVIOUS"
        MEDIA_PLAY -> "KEYCODE_MEDIA_PLAY"
        MEDIA_PAUSE -> "KEYCODE_MEDIA_PAUSE"
        else -> "KEYCODE_$keyCode"
    }
}
