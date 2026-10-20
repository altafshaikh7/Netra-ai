package com.example.netraai.feedback

import com.example.netraai.detection.Direction
import com.example.netraai.detection.Distance

/**
 * Tracks alert history to implement cooldown and urgency repetition rules.
 *
 * Rules:
 * - Cooldown per (label + direction + distance): default ~4000 ms.
 * - Suppresses repetitive alerts within the cooldown period.
 * - Repeats immediately (bypassing cooldown) if the obstacle distance gets closer.
 *
 * @param cooldownMs Cooldown window in milliseconds (default 4000ms).
 */
class AlertCooldownTracker(
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS
) {
    data class AlertRecord(
        val distance: Distance,
        val timestampMs: Long
    )

    private val alertHistory = mutableMapOf<Pair<String, Direction>, AlertRecord>()

    /**
     * Determines whether an alert should be triggered.
     *
     * @param label Object class name (case-insensitive).
     * @param direction Horizontal direction (LEFT, AHEAD, RIGHT).
     * @param distance Rough distance (VERY_CLOSE, NEAR, FAR).
     * @param currentTimeMs Current timestamp in milliseconds.
     * @return `true` if the alert should be spoken, `false` if it should be suppressed.
     */
    @Synchronized
    fun shouldAlert(
        label: String,
        direction: Direction,
        distance: Distance,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Boolean {
        val key = Pair(label.trim().lowercase(), direction)
        val lastRecord = alertHistory[key]

        if (lastRecord == null) {
            // First time detecting this obstacle in this direction
            alertHistory[key] = AlertRecord(distance, currentTimeMs)
            return true
        }

        // Check if distance has gotten closer (higher urgency)
        val isCloser = distance.isCloserThan(lastRecord.distance)
        val elapsed = currentTimeMs - lastRecord.timestampMs

        if (isCloser) {
            // Distance got closer: alert immediately even within cooldown window
            alertHistory[key] = AlertRecord(distance, currentTimeMs)
            return true
        }

        if (elapsed >= cooldownMs) {
            // Cooldown expired: allow repeat alert
            alertHistory[key] = AlertRecord(distance, currentTimeMs)
            return true
        }

        // Within cooldown and not closer: suppress
        return false
    }

    /**
     * Clears all recorded alerts (e.g. when walking mode is stopped).
     */
    @Synchronized
    fun reset() {
        alertHistory.clear()
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 4000L
    }
}
