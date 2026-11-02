package com.example.tapcounter.data

/** Pure helpers for the ring and mala bookkeeping. */
object MalaMath {
    /** Number shown inside the ring: progress within the current mala (0 until size-1). */
    fun displayCount(count: Int, size: Int): Int = if (size <= 0) count else count % size

    /** 0f..1f ring progress. */
    fun progress(count: Int, size: Int): Float =
        if (size <= 0) 0f else displayCount(count, size).toFloat() / size

    /** True when [newCount] has just finished a mala. */
    fun completesMala(newCount: Int, size: Int): Boolean = size > 0 && newCount > 0 && newCount % size == 0
}
