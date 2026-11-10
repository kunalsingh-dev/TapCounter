package com.example.tapcounter.face

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceGateTest {
    private val owner = FaceObservation(1, 0.80f, true)
    private val stranger = FaceObservation(1, 0.10f, true)
    private val two = FaceObservation(2, 0.80f, true)
    private val none = FaceObservation(0, null, null)

    private fun run(gate: FaceGate, vararg obs: FaceObservation): List<FaceVerdict> =
        obs.mapIndexed { i, o -> gate.onFrame(i * 100L, o) }

    @Test fun ownerAcceptedAfterThreeMatchingFrames() {
        val r = run(FaceGate(), owner, owner, owner)
        assertEquals(listOf(FaceVerdict.NONE, FaceVerdict.NONE, FaceVerdict.OWNER), r)
    }

    @Test fun oneBadFrameResetsTheStreak() {
        val r = run(FaceGate(), owner, owner, stranger, owner, owner)
        assertEquals(FaceVerdict.NONE, r.last())
    }

    @Test fun thresholdIsInclusive() {
        val g = FaceGate(FaceGateConfig(threshold = 0.55f))
        val edge = FaceObservation(1, 0.55f, true)
        assertEquals(FaceVerdict.OWNER, run(g, edge, edge, edge).last())
        val below = FaceObservation(1, 0.549f, true)
        assertEquals(FaceVerdict.NONE, run(FaceGate(FaceGateConfig(threshold = 0.55f)), below, below, below).first())
    }

    @Test fun strangerNeedsThreeConsecutiveFrames() {
        val r = run(FaceGate(), stranger, stranger, stranger)
        assertEquals(listOf(FaceVerdict.NONE, FaceVerdict.NONE, FaceVerdict.STRANGER), r)
    }

    @Test fun singleBlurryFrameNeverLocks() {
        val r = run(FaceGate(), owner, stranger, owner, owner, stranger, owner)
        assertEquals(false, r.any { it == FaceVerdict.STRANGER })
    }

    @Test fun twoFacesAreAStrangerEvenIfOneIsTheOwner() {
        val r = run(FaceGate(), two, two)
        assertEquals(listOf(FaceVerdict.NONE, FaceVerdict.STRANGER), r)
    }

    @Test fun ownerNeverAcceptedWhileSecondFaceIsPresent() {
        val r = run(FaceGate(), owner, owner, two, owner, owner, two, two, owner)
        assertEquals(false, r.drop(2).any { it == FaceVerdict.OWNER })
    }

    @Test fun twoFacesIgnoredWhenOptionIsOff() {
        val g = FaceGate(FaceGateConfig(lockOnMultipleFaces = false))
        val r = run(g, two, two, two)
        assertEquals(FaceVerdict.OWNER, r.last())
    }

    @Test fun noFaceNeverDecidesAndRestartsStreaks() {
        val r = run(FaceGate(), owner, owner, none, owner, owner)
        assertEquals(FaceVerdict.NONE, r.last())
        assertEquals(FaceVerdict.NONE, run(FaceGate(), none, none, none, none).last())
    }

    @Test fun failedFrameReportedAsNoFaceCanNeverUnlock() {
        val failed = FaceObservation(0, 0.99f, true) // even a bogus high score is ignored without a face
        assertEquals(false, run(FaceGate(), failed, failed, failed, failed).any { it == FaceVerdict.OWNER })
    }

    @Test fun unknownSimilarityNeverUnlocks() {
        val unknown = FaceObservation(1, null, true)
        assertEquals(false, run(FaceGate(), unknown, unknown, unknown, unknown).any { it != FaceVerdict.NONE })
    }

    @Test fun blinkRequiredBlocksAStillPhoto() {
        val g = FaceGate(FaceGateConfig(requireBlink = true))
        val r = run(g, owner, owner, owner, owner, owner, owner)
        assertEquals(false, r.any { it == FaceVerdict.OWNER })
    }

    @Test fun blinkRequiredAcceptsAfterABlink() {
        val g = FaceGate(FaceGateConfig(requireBlink = true))
        val closed = FaceObservation(1, 0.80f, false)
        val r = run(g, owner, closed, owner, owner, owner)
        assertEquals(FaceVerdict.OWNER, r.last())
    }

    @Test fun veryLongEyeClosureIsNotABlink() {
        val g = FaceGate(FaceGateConfig(requireBlink = true))
        val closed = FaceObservation(1, 0.80f, false)
        assertEquals(FaceVerdict.NONE, g.onFrame(0, owner))
        g.onFrame(100, closed)
        g.onFrame(2000, owner) // closed for 1.9 s: eyes shut, not a blink
        g.onFrame(2100, owner)
        assertEquals(FaceVerdict.NONE, g.onFrame(2200, owner))
    }

    @Test fun reconfirmHidesWhenOwnerNotSeenForTooLong() {
        val g = FaceGate(FaceGateConfig(reconfirmMs = 1000))
        assertEquals(FaceVerdict.NONE, g.onFrame(0, none))
        assertEquals(FaceVerdict.NONE, g.onFrame(900, none))
        assertEquals(FaceVerdict.UNCONFIRMED, g.onFrame(1100, none))
    }

    @Test fun seeingTheOwnerRestartsTheReconfirmClock() {
        val g = FaceGate(FaceGateConfig(reconfirmMs = 1000))
        g.onFrame(0, none)
        g.onFrame(800, owner)               // owner seen
        assertEquals(FaceVerdict.NONE, g.onFrame(1700, none))          // 900 ms since the owner
        assertEquals(FaceVerdict.UNCONFIRMED, g.onFrame(1900, none))   // 1100 ms since the owner
    }

    @Test fun strangerBeatsUnconfirmed() {
        val g = FaceGate(FaceGateConfig(reconfirmMs = 1000))
        g.onFrame(0, none)
        g.onFrame(1500, stranger)
        g.onFrame(1600, stranger)
        assertEquals(FaceVerdict.STRANGER, g.onFrame(1700, stranger))
    }

    @Test fun reconfirmOffByDefault() {
        val g = FaceGate()
        g.onFrame(0, none)
        assertEquals(FaceVerdict.NONE, g.onFrame(1_000_000, none))
    }

    @Test fun reconfirmCountsAnOwnerMatchEvenBeforeTheConfirmFrames() {
        val g = FaceGate(FaceGateConfig(reconfirmMs = 1000))
        g.onFrame(0, none)
        g.onFrame(900, owner) // one matching frame is enough to refresh the clock
        assertEquals(FaceVerdict.NONE, g.onFrame(1800, none))
    }
}
