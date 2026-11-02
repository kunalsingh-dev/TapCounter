package com.example.tapcounter.headset

data class HeadsetStatus(
    val serviceRunning: Boolean = false,
    val sessionActive: Boolean = false,
    /** null = unknown (Bluetooth permission not granted). */
    val bluetoothConnected: Boolean? = null,
    val keepAliveRunning: Boolean = false,
    /** Heuristic: another app is playing media and may have taken the headset buttons. */
    val possibleConflict: Boolean = false,
    val lastKeyName: String? = null,
    val lastKeyAtMs: Long = 0L,
)

data class CapturedKey(val keyCode: Int) {
    val name: String get() = HeadsetKeys.name(keyCode)
}

data class LearnState(val active: Boolean = false, val captured: CapturedKey? = null)
