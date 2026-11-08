package com.example.tapcounter.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMathTest {
    @Test fun normalizeMakesUnitLength() {
        val v = FaceMath.normalize(floatArrayOf(3f, 4f))
        assertEquals(0.6f, v[0], 1e-6f)
        assertEquals(0.8f, v[1], 1e-6f)
    }

    @Test fun zeroVectorSurvivesNormalize() {
        assertEquals(0f, FaceMath.normalize(floatArrayOf(0f, 0f))[0], 0f)
    }

    @Test fun cosineBasics() {
        assertEquals(1f, FaceMath.cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(2f, 4f, 6f)), 1e-6f)
        assertEquals(0f, FaceMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        assertEquals(-1f, FaceMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 1e-6f)
    }

    @Test fun cosineRejectsBadInput() {
        assertEquals(-1f, FaceMath.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0f)
        assertEquals(-1f, FaceMath.cosine(floatArrayOf(), floatArrayOf()), 0f)
        assertEquals(-1f, FaceMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0f)
    }

    @Test fun bestSimilarityPicksClosestTemplate() {
        val t = listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
        assertEquals(1f, FaceMath.bestSimilarity(t, floatArrayOf(0f, 5f)), 1e-6f)
        assertEquals(-1f, FaceMath.bestSimilarity(emptyList(), floatArrayOf(1f, 0f)), 0f)
    }

    @Test fun templateCodecRoundTripsExactly() {
        val a = FloatArray(4) { it * 0.123456789f - 0.3f }
        val b = FloatArray(4) { -1f / (it + 3) }
        val back = FaceTemplateCodec.decode(FaceTemplateCodec.encode(listOf(a, b)), dim = 4)
        assertEquals(2, back.size)
        assertTrue(a.contentEquals(back[0]))
        assertTrue(b.contentEquals(back[1]))
    }

    @Test fun templateCodecDropsBrokenVectors() {
        assertEquals(1, FaceTemplateCodec.decode("1,2,3;1,x,3;1,2", dim = 3).size)
        assertEquals(0, FaceTemplateCodec.decode("", dim = 3).size)
        assertEquals(0, FaceTemplateCodec.decode("garbage", dim = 3).size)
    }
}
