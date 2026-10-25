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
    val timeProvider: () -> Long = { System.currentTimeMillis() },
    val globalRateLimitMs: Long = 2000L,
    val sameSentenceCooldownMs: Long = 5000L
) {
    var isEnabled: Boolean = true

    private var lastSpokenTimeMs: Long = -999999L
    private var lastSpokenSentence: String = ""
    private var lastSpokenNearestMeters: Float? = null

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
        if (!isEnabled) return null
        if (gateStatus.isPaused) return null

        val isHindi = language.equals("hi", ignoreCase = true)
        val zones = evaluateZones(obstacles)
        val centerBlocked = isZoneBlocked(zones[Zone.CENTER]!!)
        val leftBlocked = isZoneBlocked(zones[Zone.LEFT]!!)
        val rightBlocked = isZoneBlocked(zones[Zone.RIGHT]!!)

        if (centerBlocked) {
            val centerObs = zones[Zone.CENTER]!!.nearestObstacle!!
            val labelStr = AlertStrings.getLabel(centerObs.label, language)
            val distMeters = centerObs.distanceMeters

            val rawSentence = when {
                leftBlocked && rightBlocked -> if (isHindi) "रुकें।" else "Stop."
                else -> {
                    val metersStr = if (distMeters != null) {
                        if (distMeters == distMeters.toInt().toFloat()) "${distMeters.toInt()}" else "$distMeters"
                    } else {
                        null
                    }
                    if (isHindi) {
                        if (metersStr != null) "$labelStr सामने, करीब $metersStr मीटर।" else "$labelStr सामने, पास।"
                    } else {
                        if (metersStr != null) "$labelStr ahead, about $metersStr meters." else "$labelStr ahead, close."
                    }
                }
            }

            if (shouldSpeak(rawSentence, distMeters, currentTimeMs)) {
                recordSpoken(rawSentence, distMeters, currentTimeMs)
                return rawSentence
            }
        }

        return null
    }

    private fun shouldSpeak(sentence: String, currentNearestMeters: Float?, currentTimeMs: Long): Boolean {
        val elapsedGlobal = currentTimeMs - lastSpokenTimeMs
        if (elapsedGlobal < globalRateLimitMs) return false

        if (sentence == lastSpokenSentence) {
            val elapsedSentence = currentTimeMs - lastSpokenTimeMs
            val droppedMeters = if (currentNearestMeters != null && lastSpokenNearestMeters != null) {
                lastSpokenNearestMeters!! - currentNearestMeters >= 1.0f
            } else false

            if (!droppedMeters && elapsedSentence < sameSentenceCooldownMs) {
                return false
            }
        }

        return true
    }

    private fun recordSpoken(sentence: String, currentNearestMeters: Float?, currentTimeMs: Long) {
        lastSpokenTimeMs = currentTimeMs
        lastSpokenSentence = sentence
        lastSpokenNearestMeters = currentNearestMeters
    }

    fun reset() {
        lastSpokenTimeMs = -999999L
        lastSpokenSentence = ""
        lastSpokenNearestMeters = null
    }
}
