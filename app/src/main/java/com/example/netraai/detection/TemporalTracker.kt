package com.example.netraai.detection

import android.graphics.RectF
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Tracks detected obstacles over a sliding window of frames to filter out transient false positives.
 * Policy: Stricter temporal confirmation (4 of 6 frames, consistent position, and average score >= threshold).
 *
 * @param windowSize Total frames kept in sliding history (default: 6).
 * @param minConfirmations Minimum frame appearances required for confirmation (default: 4).
 * @param positionTolerance Normalized horizontal delta within which positions are considered similar (default: 0.20f).
 * @param minIoU Minimum bounding box IoU required for position consistency (default: 0.30f).
 */
class TemporalTracker(
    var windowSize: Int = DEFAULT_WINDOW_SIZE,
    var minConfirmations: Int = DEFAULT_MIN_CONFIRMATIONS,
    val positionTolerance: Float = DEFAULT_POSITION_TOLERANCE,
    val minIoU: Float = DEFAULT_MIN_IOU
) {
    private val frameHistory = ArrayDeque<List<DetectedObstacle>>()

    /**
     * Ingests the raw accepted detections for the current frame and returns only those that are
     * temporally confirmed across the history window.
     *
     * @param currentDetections Detections from the current frame.
     * @param sensitivity Detection sensitivity level.
     * @return List of detections confirmed by temporal persistence.
     */
    @Synchronized
    fun filterConfirmed(
        currentDetections: List<DetectedObstacle>,
        sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL
    ): List<DetectedObstacle> {
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
            val matchingHits = mutableListOf<DetectedObstacle>()

            for (historicalFrame in frameHistory) {
                val match = historicalFrame.firstOrNull { isSimilar(it, candidate) }
                if (match != null) {
                    matchingHits.add(match)
                }
            }

            // Must appear in at least minConfirmations out of windowSize frames
            if (matchingHits.size < minConfirmations) {
                try {
                    Log.d("NetraWhy", "UNCONFIRMED ${matchingHits.size}/$minConfirmations for label=${candidate.label}")
                } catch (_: Exception) {}
                return@filter false
            }

            // Must satisfy average score >= threshold
            val avgScore = matchingHits.map { it.confidence }.average().toFloat()
            val threshold = DetectionFilter.getThreshold(candidate.label, sensitivity)
            if (avgScore < threshold) {
                try {
                    Log.d("NetraWhy", "UNCONFIRMED_SCORE avgScore=$avgScore < threshold=$threshold for label=${candidate.label}")
                } catch (_: Exception) {}
                return@filter false
            }

            true
        }
    }

    /**
     * Checks if two obstacles match in label and position.
     * Position match requires:
     * 1. Same label
     * 2. Close horizontal center (<= positionTolerance) OR IoU >= minIoU
     */
    fun isSimilar(a: DetectedObstacle, b: DetectedObstacle): Boolean {
        if (!a.label.equals(b.label, ignoreCase = true)) return false

        val closeCenterX = abs(a.normalizedCenterX - b.normalizedCenterX) <= positionTolerance
        val boxIoU = computeIoU(a.boundingBox, b.boundingBox)

        return (closeCenterX && a.direction == b.direction) || boxIoU >= minIoU
    }

    fun computeIoU(box1: RectF?, box2: RectF?): Float {
        if (box1 == null || box2 == null) return 0f

        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0f)
        val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0f)
        val intersectionArea = intersectionWidth * intersectionHeight

        val area1 = ((box1.right - box1.left) * (box1.bottom - box1.top)).coerceAtLeast(0f)
        val area2 = ((box2.right - box2.left) * (box2.bottom - box2.top)).coerceAtLeast(0f)

        val unionArea = area1 + area2 - intersectionArea
        if (unionArea <= 0f) return 0f

        return intersectionArea / unionArea
    }

    @Synchronized
    fun reset() {
        frameHistory.clear()
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 6
        const val DEFAULT_MIN_CONFIRMATIONS = 4
        const val DEFAULT_POSITION_TOLERANCE = 0.20f
        const val DEFAULT_MIN_IOU = 0.30f
    }
}
