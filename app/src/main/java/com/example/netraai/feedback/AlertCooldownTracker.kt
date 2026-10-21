package com.example.netraai.feedback

import com.example.netraai.detection.AlertPriority
import com.example.netraai.detection.Direction
import com.example.netraai.detection.Distance

/**
 * Tracks alert history to implement global rate limiting and repetition rules.
 *
 * Rules:
 * - At most one spoken alert per 2 seconds (very close vehicles may interrupt).
 * - Same alert repeated only after 5 seconds, or earlier if distanceMeters dropped by at least 1 meter.
 */
class AlertCooldownTracker(
    val globalRateLimitMs: Long = 2000L,
    val repeatCooldownMs: Long = 5000L
) {
    data class AlertRecord(
        val distance: Distance,
        val distanceMeters: Float?,
        val timestampMs: Long
    )

    private val alertHistory = mutableMapOf<Pair<String, Direction>, AlertRecord>()
    private var lastGlobalAlertTimestampMs: Long = -999999L

    @Synchronized
    fun shouldAlert(
        label: String,
        direction: Direction,
        distance: Distance,
        distanceMeters: Float? = null,
        priority: AlertPriority = AlertPriority.PRIORITY_2,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        val isVeryCloseVehicle = priority == AlertPriority.PRIORITY_1 && (distance == Distance.VERY_CLOSE || (distanceMeters != null && distanceMeters < 1.5f))
        val elapsedGlobal = currentTimeMs - lastGlobalAlertTimestampMs

        if (elapsedGlobal < globalRateLimitMs && !isVeryCloseVehicle) {
            return false
        }

        val key = Pair(label.trim().lowercase(), direction)
        val lastRecord = alertHistory[key]

        if (lastRecord == null) {
            alertHistory[key] = AlertRecord(distance, distanceMeters, currentTimeMs)
            lastGlobalAlertTimestampMs = currentTimeMs
            return true
        }

        val elapsedRepeat = currentTimeMs - lastRecord.timestampMs
        val isCloser = distance.isCloserThan(lastRecord.distance)
        val droppedMeters = if (distanceMeters != null && lastRecord.distanceMeters != null) {
            lastRecord.distanceMeters - distanceMeters >= 1.0f
        } else {
            false
        }

        if (isCloser || droppedMeters) {
            alertHistory[key] = AlertRecord(distance, distanceMeters, currentTimeMs)
            lastGlobalAlertTimestampMs = currentTimeMs
            return true
        }

        if (elapsedRepeat >= repeatCooldownMs) {
            alertHistory[key] = AlertRecord(distance, distanceMeters, currentTimeMs)
            lastGlobalAlertTimestampMs = currentTimeMs
            return true
        }

        return false
    }

    @Synchronized
    fun reset() {
        alertHistory.clear()
        lastGlobalAlertTimestampMs = -999999L
    }
}
