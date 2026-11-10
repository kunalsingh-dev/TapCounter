package com.example.tapcounter.face

data class FaceGateConfig(
    /** Cosine similarity needed to count as "this is the owner". */
    val threshold: Float = 0.50f,
    /** Consecutive matching frames before the owner is accepted. */
    val confirmFrames: Int = 3,
    /** Consecutive non-matching frames (one face) before a stranger is declared. */
    val rejectFrames: Int = 3,
    /** Consecutive frames with 2+ faces before "someone else is looking" is declared. */
    val multiFrames: Int = 2,
    /** Treat 2+ faces in view as a stranger, even if one of them is the owner. */
    val lockOnMultipleFaces: Boolean = true,
    /** Optional anti-photo check: a blink must be seen shortly before accepting. */
    val requireBlink: Boolean = false,
    val blinkWindowMs: Long = 5000L,
    /**
     * Re-confirmation (watch mode): if the owner's face has not been matched for this long, the
     * verdict becomes UNCONFIRMED and the app should hide again. 0 = off.
     */
    val reconfirmMs: Long = 0L,
)

/** What one camera frame showed. [similarity] belongs to the largest face; null if no face. */
data class FaceObservation(val faceCount: Int, val similarity: Float?, val eyesOpen: Boolean?)

enum class FaceVerdict { NONE, OWNER, STRANGER, UNCONFIRMED }

/**
 * Turns a stream of frame results into a decision. Pure logic, no Android classes.
 * Rules:
 *  - OWNER only after [FaceGateConfig.confirmFrames] consecutive frames with exactly one face that
 *    matches. A second face anywhere in view always cancels this (when lockOnMultipleFaces).
 *  - STRANGER after rejectFrames consecutive frames of one non-matching face, or multiFrames
 *    consecutive frames with 2+ faces. A single blurry frame never decides anything.
 *  - No face = no decision (NONE) and all streaks restart.
 * Unknown/failed frames must be reported as faceCount 0, which can never unlock.
 */
class FaceGate(private val cfg: FaceGateConfig = FaceGateConfig()) {
    private var matchRun = 0
    private var rejectRun = 0
    private var multiRun = 0
    private var lastBlinkAt = NEVER
    private var lastOwnerAt = NEVER
    private var closedSince = NEVER
    private var sawOpen = false

    fun onFrame(now: Long, obs: FaceObservation): FaceVerdict {
        if (lastOwnerAt == NEVER) lastOwnerAt = now // the gate starts right after a successful unlock
        val verdict = evaluate(now, obs)
        if (cfg.reconfirmMs > 0 && verdict == FaceVerdict.NONE && now - lastOwnerAt > cfg.reconfirmMs) {
            return FaceVerdict.UNCONFIRMED
        }
        return verdict
    }

    private fun evaluate(now: Long, obs: FaceObservation): FaceVerdict {
        trackBlink(now, obs.eyesOpen)

        if (obs.faceCount <= 0) {
            matchRun = 0; rejectRun = 0; multiRun = 0
            return FaceVerdict.NONE
        }
        if (obs.faceCount >= 2 && cfg.lockOnMultipleFaces) {
            multiRun++
            matchRun = 0; rejectRun = 0
            return if (multiRun >= cfg.multiFrames) FaceVerdict.STRANGER else FaceVerdict.NONE
        }
        multiRun = 0

        val sim = obs.similarity ?: return FaceVerdict.NONE
        return if (sim >= cfg.threshold) {
            lastOwnerAt = now
            matchRun++
            rejectRun = 0
            val blinkOk = !cfg.requireBlink || now - lastBlinkAt <= cfg.blinkWindowMs
            if (matchRun >= cfg.confirmFrames && blinkOk) FaceVerdict.OWNER else FaceVerdict.NONE
        } else {
            rejectRun++
            matchRun = 0
            if (rejectRun >= cfg.rejectFrames) FaceVerdict.STRANGER else FaceVerdict.NONE
        }
    }

    fun reset() {
        matchRun = 0; rejectRun = 0; multiRun = 0
        lastBlinkAt = NEVER; closedSince = NEVER; sawOpen = false; lastOwnerAt = NEVER
    }

    /** A blink = eyes open, then closed for at most 800 ms, then open again. */
    private fun trackBlink(now: Long, eyesOpen: Boolean?) {
        when (eyesOpen) {
            true -> {
                if (closedSince != NEVER && now - closedSince <= 800L) lastBlinkAt = now
                closedSince = NEVER
                sawOpen = true
            }
            false -> if (sawOpen && closedSince == NEVER) closedSince = now
            null -> Unit
        }
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE / 2
    }
}
