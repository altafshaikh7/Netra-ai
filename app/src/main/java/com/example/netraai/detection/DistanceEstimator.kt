package com.example.netraai.detection

/**
 * Pure Kotlin distance estimation with hysteresis to eliminate rapid state flipping
 * between FAR, NEAR, and VERY_CLOSE caused by boundary bounding-box jitter.
 */
class DistanceEstimator(
    val nearEnterRatio: Float = DEFAULT_NEAR_ENTER,
    val nearExitRatio: Float = DEFAULT_NEAR_EXIT,
    val veryCloseEnterRatio: Float = DEFAULT_VERY_CLOSE_ENTER,
    val veryCloseExitRatio: Float = DEFAULT_VERY_CLOSE_EXIT
) {
    private val distanceHistory = mutableMapOf<Pair<String, Direction>, Distance>()

    /**
     * Computes the distance for an area ratio, applying hysteresis against an optional previous state.
     */
    fun computeDistanceWithHysteresis(areaRatio: Float, previous: Distance?): Distance {
        val ratio = areaRatio.coerceIn(0f, 1f)

        return when (previous) {
            null -> when {
                ratio >= 0.20f -> Distance.VERY_CLOSE
                ratio >= 0.05f -> Distance.NEAR
                else -> Distance.FAR
            }

            Distance.FAR -> when {
                ratio >= veryCloseEnterRatio -> Distance.VERY_CLOSE
                ratio >= nearEnterRatio -> Distance.NEAR
                else -> Distance.FAR
            }

            Distance.NEAR -> when {
                ratio >= veryCloseEnterRatio -> Distance.VERY_CLOSE
                ratio < nearExitRatio -> Distance.FAR
                else -> Distance.NEAR
            }

            Distance.VERY_CLOSE -> when {
                ratio < nearExitRatio -> Distance.FAR
                ratio < veryCloseExitRatio -> Distance.NEAR
                else -> Distance.VERY_CLOSE
            }
        }
    }

    /**
     * Resolves distance for a detected obstacle, maintaining state per (label, direction).
     */
    @Synchronized
    fun estimateDistance(label: String, direction: Direction, areaRatio: Float): Distance {
        val key = Pair(label.trim().lowercase(), direction)
        val previous = distanceHistory[key]
        val resolved = computeDistanceWithHysteresis(areaRatio, previous)
        distanceHistory[key] = resolved
        return resolved
    }

    @Synchronized
    fun reset() {
        distanceHistory.clear()
    }

    companion object {
        const val DEFAULT_NEAR_ENTER = 0.06f
        const val DEFAULT_NEAR_EXIT = 0.04f
        const val DEFAULT_VERY_CLOSE_ENTER = 0.22f
        const val DEFAULT_VERY_CLOSE_EXIT = 0.18f
    }
}
