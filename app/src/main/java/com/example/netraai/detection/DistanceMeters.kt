package com.example.netraai.detection

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

data class TrackedObject(
    val trackId: String,
    val label: String,
    var boxHeight: Float,
    var centerX: Float,
    var centerY: Float,
    var lastSeenFrame: Int,
    val history: ArrayDeque<Float> = ArrayDeque()
)

class DistanceMetersEstimator(
    val windowSize: Int = 5,
    val maxMissedFrames: Int = 5
) {
    private val activeTracks = mutableListOf<TrackedObject>()
    private var frameCounter = 0
    private var nextId = 1
    private val matchedTrackIdsThisFrame = mutableSetOf<String>()

    /**
     * Increments frame counter once per frame and expires old tracks not seen for maxMissedFrames.
     */
    @Synchronized
    fun beginFrame() {
        frameCounter++
        matchedTrackIdsThisFrame.clear()
        activeTracks.removeAll { (frameCounter - it.lastSeenFrame) >= maxMissedFrames }
    }

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

        /**
         * Computes focal length in pixels for the vertical axis of an upright portrait frame.
         * Formula: focalPx = (focalLengthMm / sensorLongSideMm) * frameLongSide
         * Explanation: For an upright portrait frame (height > width), the vertical axis aligns with
         * the frame's long side, mapping to the sensor's long physical dimension.
         */
        fun computeFocalPixels(focalLengthMm: Float, sensorWidthMm: Float, sensorHeightMm: Float, frameWidth: Float, frameHeight: Float): Float {
            val sensorLongSide = max(sensorWidthMm, sensorHeightMm)
            val frameLongSide = max(frameWidth, frameHeight)
            if (sensorLongSide <= 0f || frameLongSide <= 0f) return frameHeight * 1.2f
            return (focalLengthMm / sensorLongSide) * frameLongSide
        }
    }

    @Synchronized
    fun estimateDistance(
        label: String,
        boxHeightPixels: Float,
        focalLengthPixels: Float,
        calibrationMultiplier: Float = 1.0f,
        centerX: Float = 0f,
        centerY: Float = 0f
    ): Pair<Float?, String> {
        val realHeight = getRealHeight(label) ?: return Pair(null, "")
        val boxH = boxHeightPixels.coerceAtLeast(1f)
        if (focalLengthPixels <= 0f) return Pair(null, "")

        var matchedTrack: TrackedObject? = null
        var bestScore = -1f

        // A track may match at most one detection per frame; prefer closest center match
        for (track in activeTracks) {
            if (!track.label.equals(label, ignoreCase = true)) continue
            if (track.trackId in matchedTrackIdsThisFrame) continue

            val centerDist = abs(track.centerX - centerX) + abs(track.centerY - centerY)
            val maxDim = max(track.boxHeight, boxH).coerceAtLeast(1f)
            val normalizedDist = centerDist / maxDim

            if (normalizedDist <= 0.5f) {
                val score = 1f - normalizedDist.coerceIn(0f, 1f)
                if (score > bestScore) {
                    bestScore = score
                    matchedTrack = track
                }
            }
        }

        val track = if (matchedTrack != null) {
            matchedTrack.boxHeight = boxH
            matchedTrack.centerX = centerX
            matchedTrack.centerY = centerY
            matchedTrack.lastSeenFrame = frameCounter
            matchedTrack
        } else {
            val newTrack = TrackedObject(
                trackId = "${label}_${nextId++}",
                label = label,
                boxHeight = boxH,
                centerX = centerX,
                centerY = centerY,
                lastSeenFrame = frameCounter
            )
            activeTracks.add(newTrack)
            newTrack
        }

        matchedTrackIdsThisFrame.add(track.trackId)

        val rawMeters = (realHeight * focalLengthPixels) / boxH * calibrationMultiplier

        track.history.addLast(rawMeters)
        while (track.history.size > windowSize) {
            track.history.removeFirst()
        }

        val sorted = track.history.sorted()
        val medianMeters = sorted[sorted.size / 2]

        return Pair(roundToNearestHalfMeter(medianMeters), track.trackId)
    }

    @Synchronized
    fun reset() {
        activeTracks.clear()
        frameCounter = 0
        nextId = 1
        matchedTrackIdsThisFrame.clear()
    }
}
