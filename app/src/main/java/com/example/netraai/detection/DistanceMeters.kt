package com.example.netraai.detection

import kotlin.math.round

class DistanceMetersEstimator(
    val windowSize: Int = 5
) {
    private val historyMap = mutableMapOf<String, ArrayDeque<Float>>()

    companion object {
        val REAL_HEIGHTS_METERS = mapOf(
            "person" to 1.6f,
            "car" to 1.5f,
            "bus" to 3.0f,
            "truck" to 3.0f,
            "motorcycle" to 1.1f,
            "bicycle" to 1.0f,
            "dog" to 0.5f,
            "cow" to 1.3f,
            "horse" to 1.6f,
            "chair" to 0.9f,
            "bench" to 0.8f
        )

        fun getRealHeight(label: String): Float? {
            return REAL_HEIGHTS_METERS[label.trim().lowercase()]
        }

        fun roundToNearestHalfMeter(meters: Float): Float {
            return round(meters * 2f) / 2f
        }
    }

    @Synchronized
    fun estimateDistance(
        label: String,
        boxHeightPixels: Float,
        focalLengthPixels: Float,
        trackKey: String = label
    ): Float? {
        val realHeight = getRealHeight(label) ?: return null
        if (boxHeightPixels <= 0f || focalLengthPixels <= 0f) return null

        val rawMeters = (realHeight * focalLengthPixels) / boxHeightPixels

        val history = historyMap.getOrPut(trackKey) { ArrayDeque() }
        history.addLast(rawMeters)
        while (history.size > windowSize) {
            history.removeFirst()
        }

        val sorted = history.sorted()
        val medianMeters = sorted[sorted.size / 2]

        return roundToNearestHalfMeter(medianMeters)
    }

    @Synchronized
    fun reset() {
        historyMap.clear()
    }
}
