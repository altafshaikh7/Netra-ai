package com.example.netraai.detection

import kotlin.math.abs

/**
 * Tracks detected obstacles over a sliding window of frames to filter out transient false positives.
 * An obstacle is confirmed only if it appears in at least [minConfirmations] out of the last [windowSize] frames
 * with the same label and similar position.
 *
 * @param windowSize Total frames kept in sliding history (default: 5).
 * @param minConfirmations Minimum frame appearances required for confirmation (default: 3).
 * @param positionTolerance Normalized horizontal delta within which positions are considered similar (default: 0.35f).
 */
class TemporalTracker(
    val windowSize: Int = DEFAULT_WINDOW_SIZE,
    val minConfirmations: Int = DEFAULT_MIN_CONFIRMATIONS,
    val positionTolerance: Float = DEFAULT_POSITION_TOLERANCE
) {
    private val frameHistory = ArrayDeque<List<DetectedObstacle>>()

    /**
     * Ingests the raw accepted detections for the current frame and returns only those that are
     * temporally confirmed across the history window.
     *
     * @param currentDetections Detections from the current frame.
     * @return List of detections confirmed by temporal persistence.
     */
    @Synchronized
    fun filterConfirmed(currentDetections: List<DetectedObstacle>): List<DetectedObstacle> {
        // Add current frame to history
        frameHistory.addLast(currentDetections)
        while (frameHistory.size > windowSize) {
            frameHistory.removeFirst()
        }

        if (currentDetections.isEmpty()) {
            return emptyList()
        }

        // Evaluate each detection in the current frame
        return currentDetections.filter { candidate ->
            val matchingFramesCount = frameHistory.count { historicalFrame ->
                historicalFrame.any { isSimilar(it, candidate) }
            }
            matchingFramesCount >= minConfirmations
        }
    }

    /**
     * Checks if two obstacles match in label and position.
     * Considers position similar if either:
     * 1. They have the identical Direction (LEFT, AHEAD, RIGHT), OR
     * 2. Their normalized horizontal center distance is within [positionTolerance].
     */
    fun isSimilar(a: DetectedObstacle, b: DetectedObstacle): Boolean {
        if (!a.label.equals(b.label, ignoreCase = true)) return false
        val sameDirection = a.direction == b.direction
        val closeCenterX = abs(a.normalizedCenterX - b.normalizedCenterX) <= positionTolerance
        return sameDirection || closeCenterX
    }

    @Synchronized
    fun reset() {
        frameHistory.clear()
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 5
        const val DEFAULT_MIN_CONFIRMATIONS = 3
        const val DEFAULT_POSITION_TOLERANCE = 0.35f
    }
}
