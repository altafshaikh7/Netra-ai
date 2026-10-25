package com.example.netraai.detection

import com.example.netraai.feedback.AlertStrings

enum class Zone {
    LEFT, CENTER, RIGHT
}

data class ZoneStatus(
    val zone: Zone,
    val nearestObstacle: DetectedObstacle?,
    val nearestDistanceMeters: Float?
)

class PathGuidance(
    val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var gateActiveStartTimeMs: Long = 0L
    private var gatePausedStartTimeMs: Long = 0L
    private var lastClearSpokenTimeMs: Long = -10000L
    private var hasSpokenUnsteadyWarning: Boolean = false

    fun evaluateZones(obstacles: List<DetectedObstacle>): Map<Zone, ZoneStatus> {
        val leftObs = mutableListOf<DetectedObstacle>()
        val centerObs = mutableListOf<DetectedObstacle>()
        val rightObs = mutableListOf<DetectedObstacle>()

        for (obs in obstacles) {
            when {
                obs.normalizedCenterX < 0.333f -> leftObs.add(obs)
                obs.normalizedCenterX in 0.333f..0.667f -> centerObs.add(obs)
                else -> rightObs.add(obs)
            }
        }

        fun findNearest(list: List<DetectedObstacle>): Pair<DetectedObstacle?, Float?> {
            if (list.isEmpty()) return Pair(null, null)
            val nearest = list.minByOrNull { it.distanceMeters ?: if (it.distance == Distance.VERY_CLOSE) 0.5f else 1.5f }
            return Pair(nearest, nearest?.distanceMeters)
        }

        val (leftNearest, leftDist) = findNearest(leftObs)
        val (centerNearest, centerDist) = findNearest(centerObs)
        val (rightNearest, rightDist) = findNearest(rightObs)

        return mapOf(
            Zone.LEFT to ZoneStatus(Zone.LEFT, leftNearest, leftDist),
            Zone.CENTER to ZoneStatus(Zone.CENTER, centerNearest, centerDist),
            Zone.RIGHT to ZoneStatus(Zone.RIGHT, rightNearest, rightDist)
        )
    }

    fun isZoneBlocked(status: ZoneStatus, thresholdMeters: Float = 2.5f): Boolean {
        val obs = status.nearestObstacle ?: return false
        val meters = status.nearestDistanceMeters
        if (meters != null) {
            return meters <= thresholdMeters
        }
        return obs.distance == Distance.VERY_CLOSE || obs.distance == Distance.NEAR
    }

    fun evaluateGuidanceSentence(
        obstacles: List<DetectedObstacle>,
        gateStatus: GateStatus,
        language: String,
        currentTimeMs: Long = timeProvider()
    ): String? {
        val isHindi = language.equals("hi", ignoreCase = true)

        if (gateStatus.isPaused) {
            gateActiveStartTimeMs = 0L
            if (gatePausedStartTimeMs == 0L) {
                gatePausedStartTimeMs = currentTimeMs
            } else if (currentTimeMs - gatePausedStartTimeMs >= 2000L && !hasSpokenUnsteadyWarning) {
                hasSpokenUnsteadyWarning = true
                return if (isHindi) "कैमरा स्थिर नहीं है, धीरे चलें।" else "Camera not steady, slow down."
            }
            return null
        } else {
            gatePausedStartTimeMs = 0L
            hasSpokenUnsteadyWarning = false
            if (gateActiveStartTimeMs == 0L) {
                gateActiveStartTimeMs = currentTimeMs
            }
        }

        val zones = evaluateZones(obstacles)
        val centerBlocked = isZoneBlocked(zones[Zone.CENTER]!!)
        val leftBlocked = isZoneBlocked(zones[Zone.LEFT]!!)
        val rightBlocked = isZoneBlocked(zones[Zone.RIGHT]!!)

        if (centerBlocked) {
            val centerObs = zones[Zone.CENTER]!!.nearestObstacle!!
            val labelStr = AlertStrings.getLabel(centerObs.label, language)
            val distMeters = centerObs.distanceMeters
            val metersStr = if (distMeters != null) {
                if (distMeters == distMeters.toInt().toFloat()) "${distMeters.toInt()}" else "$distMeters"
            } else {
                null
            }

            val prefix = if (isHindi) {
                if (metersStr != null) "$labelStr सामने, करीब $metersStr मीटर।" else "$labelStr सामने, पास।"
            } else {
                if (metersStr != null) "$labelStr ahead, about $metersStr meters." else "$labelStr ahead, close."
            }

            return when {
                !leftBlocked -> "$prefix ${if (isHindi) "बायां रास्ता साफ़ लग रहा है।" else "Left looks clear."}"
                !rightBlocked -> "$prefix ${if (isHindi) "दायां रास्ता साफ़ लग रहा है।" else "Right looks clear."}"
                else -> if (isHindi) "रुकें।" else "Stop."
            }
        }

        val activeDuration = currentTimeMs - gateActiveStartTimeMs
        val elapsedClearSpoken = currentTimeMs - lastClearSpokenTimeMs

        val corridorEmpty = obstacles.none { CorridorFilter.isValidForAlert(it) }

        if (activeDuration >= 4000L && corridorEmpty && elapsedClearSpoken >= 10000L) {
            lastClearSpokenTimeMs = currentTimeMs
            return if (isHindi) "रास्ता साफ़ लग रहा है।" else "Path looks clear."
        }

        return null
    }

    fun reset() {
        gateActiveStartTimeMs = 0L
        gatePausedStartTimeMs = 0L
        lastClearSpokenTimeMs = -10000L
        hasSpokenUnsteadyWarning = false
    }
}
