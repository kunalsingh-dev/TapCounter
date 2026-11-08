package com.example.tapcounter.face

/**
 * Collects face samples while the owner enrols: only good frames, spaced out in time, and at the
 * end a consistency check so that two different people can't be mixed into one profile.
 */
class EnrollmentCollector(
    val target: Int = 8,
    private val minSpacingMs: Long = 600L,
    private val minConsistency: Float = 0.5f,
) {
    private val samples = mutableListOf<FloatArray>()
    /** Slightly shifted/zoomed versions of accepted samples: extra templates, not used for the consistency check. */
    private val extras = mutableListOf<FloatArray>()
    private var lastAt = Long.MIN_VALUE / 2

    /** @return true if the sample was kept. */
    @Synchronized
    fun offer(now: Long, embedding: FloatArray, qualityOk: Boolean, extra: List<FloatArray> = emptyList()): Boolean {
        if (!qualityOk || samples.size >= target) return false
        if (now - lastAt < minSpacingMs) return false
        samples.add(FaceMath.normalize(embedding))
        for (x in extra) extras.add(FaceMath.normalize(x))
        lastAt = now
        return true
    }

    val count: Int @Synchronized get() = samples.size

    val done: Boolean @Synchronized get() = samples.size >= target

    /**
     * Every sample must, on average, resemble the OTHER samples. This catches a profile that
     * mixes two people (each person's samples look unlike the other person's) and a stray frame
     * of a bystander, which a comparison against the overall average would let through.
     */
    @Synchronized
    fun isConsistent(): Boolean {
        if (samples.isEmpty()) return false
        if (samples.size == 1) return true
        return samples.indices.all { i ->
            var sum = 0f
            for (j in samples.indices) if (j != i) sum += FaceMath.cosine(samples[i], samples[j])
            sum / (samples.size - 1) >= minConsistency
        }
    }

    @Synchronized
    fun templates(): List<FloatArray> = (samples + extras).map { it.copyOf() }

    @Synchronized
    fun reset() {
        samples.clear()
        extras.clear()
        lastAt = Long.MIN_VALUE / 2
    }
}
