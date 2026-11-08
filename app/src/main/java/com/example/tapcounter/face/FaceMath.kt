package com.example.tapcounter.face

import kotlin.math.sqrt

/** Vector helpers for face embeddings (MobileFaceNet gives 192 numbers per face). */
object FaceMath {
    const val EMBEDDING_SIZE = 192

    /** Scales [v] to length 1. A zero vector is returned unchanged. */
    fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm == 0f) return v.copyOf()
        return FloatArray(v.size) { v[it] / norm }
    }

    /** Cosine similarity, -1..1 (1 = same direction). Returns -1 for unusable input. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val d = sqrt(na) * sqrt(nb)
        return if (d == 0f) -1f else dot / d
    }

    /** Best similarity of [probe] against any enrolled template (-1 when there are none). */
    fun bestSimilarity(templates: List<FloatArray>, probe: FloatArray): Float =
        templates.maxOfOrNull { cosine(it, probe) } ?: -1f
}

/** Stores enrolled embeddings as text: vectors separated by ';', numbers by ','. */
object FaceTemplateCodec {
    fun encode(templates: List<FloatArray>): String =
        templates.joinToString(";") { v -> v.joinToString(",") { it.toString() } }

    /** Vectors with the wrong length or unreadable numbers are dropped. */
    fun decode(raw: String, dim: Int = FaceMath.EMBEDDING_SIZE): List<FloatArray> {
        if (raw.isBlank()) return emptyList()
        return raw.split(';').mapNotNull { part ->
            val xs = part.split(',')
            if (xs.size != dim) {
                null
            } else {
                val arr = FloatArray(dim)
                for (i in 0 until dim) arr[i] = xs[i].toFloatOrNull() ?: return@mapNotNull null
                arr
            }
        }
    }
}
