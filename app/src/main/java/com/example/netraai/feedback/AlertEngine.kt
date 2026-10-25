package com.example.netraai.feedback

import android.util.Log
import com.example.netraai.detection.AlertPriority
import com.example.netraai.detection.CorridorFilter
import com.example.netraai.detection.CrowdDetector
import com.example.netraai.detection.DetectionProfile
import com.example.netraai.detection.DetectionProfileValues
import com.example.netraai.detection.DetectedObstacle
import com.example.netraai.detection.Distance
import com.example.netraai.detection.GateStatus
import com.example.netraai.detection.PathGuidance
import com.example.netraai.voice.TtsManager

/**
 * AlertEngine manages obstacle prioritization, speech queueing/interruptions,
 * corridor filtering, crowd mode detection, global rate limiting, path guidance, and "why silent" diagnostics.
 */
class AlertEngine(
    private val speakAction: (text: String, interrupt: Boolean) -> Unit,
    private val isTtsSpeaking: () -> Boolean,
    val cooldownTracker: AlertCooldownTracker = AlertCooldownTracker(),
    val crowdDetector: CrowdDetector = CrowdDetector(),
    val pathGuidance: PathGuidance = PathGuidance(),
    var detectionProfile: DetectionProfile = DetectionProfile.BALANCED,
    var allObjectsMode: Boolean = false,
    var onWhyLogged: ((String) -> Unit)? = null
) {
    private var lastCrowdSpokenTimeMs: Long = 0L

    constructor(
        ttsManager: TtsManager,
        cooldownTracker: AlertCooldownTracker = AlertCooldownTracker(),
        crowdDetector: CrowdDetector = CrowdDetector(),
        pathGuidance: PathGuidance = PathGuidance()
    ) : this(
        speakAction = { text, interrupt -> ttsManager.speak(text, interrupt) },
        isTtsSpeaking = { ttsManager.isSpeaking },
        cooldownTracker = cooldownTracker,
        crowdDetector = crowdDetector,
        pathGuidance = pathGuidance
    )

    fun onGateStatus(gateStatus: GateStatus, language: String, currentTimeMs: Long = System.currentTimeMillis()) {
        val guidanceSentence = pathGuidance.evaluateGuidanceSentence(emptyList(), gateStatus, language, currentTimeMs)
        if (guidanceSentence != null) {
            val isUrgent = guidanceSentence.contains("Stop") || guidanceSentence.contains("रुकें")
            speakAction(guidanceSentence, isUrgent)
        }
    }

    fun processObstacles(
        obstacles: List<DetectedObstacle>,
        language: String,
        gateStatus: GateStatus = GateStatus.ACTIVE,
        currentTimeMs: Long = System.currentTimeMillis()
    ): DetectedObstacle? {
        val guidanceSentence = pathGuidance.evaluateGuidanceSentence(obstacles, gateStatus, language, currentTimeMs)
        if (guidanceSentence != null) {
            val isUrgent = guidanceSentence.contains("Stop") || guidanceSentence.contains("रुकें")
            speakAction(guidanceSentence, isUrgent)
            // Item 2b: Do not also speak single-obstacle alert if guidance spoke this frame
            return null
        }

        if (obstacles.isEmpty()) return null

        val profileValues = DetectionProfileValues.forProfile(detectionProfile)

        // 1. Filter by corridor and distance
        for (obstacle in obstacles) {
            if (!CorridorFilter.isInCorridor(obstacle.normalizedCenterX, profileValues.corridorLeft, profileValues.corridorRight)) {
                try {
                    Log.d("NetraWhy", "Dropped: label=${obstacle.label}, reason=OUTSIDE_CORRIDOR")
                } catch (_: Exception) {}
                onWhyLogged?.invoke("OUTSIDE_CORRIDOR")
            } else if (obstacle.distanceMeters != null && obstacle.distanceMeters > 4.0f) {
                try {
                    Log.d("NetraWhy", "Dropped: label=${obstacle.label}, reason=TOO_FAR")
                } catch (_: Exception) {}
                onWhyLogged?.invoke("TOO_FAR")
            }
        }

        val corridorObstacles = obstacles.filter { CorridorFilter.isValidForAlert(it, profileValues.corridorLeft, profileValues.corridorRight) }
        if (corridorObstacles.isEmpty()) return null

        // 2. Evaluate Crowd Mode
        val (isCrowd, newCrowdTime) = if (!allObjectsMode) {
            crowdDetector.evaluate(corridorObstacles, currentTimeMs, lastCrowdSpokenTimeMs)
        } else {
            Pair(false, lastCrowdSpokenTimeMs)
        }

        if (isCrowd && newCrowdTime != lastCrowdSpokenTimeMs) {
            lastCrowdSpokenTimeMs = newCrowdTime
            val crowdMsg = AlertStrings.getCrowdMessage(language)
            speakAction(crowdMsg, false)
        }

        val effectiveObstacles = if (allObjectsMode) {
            val closest = corridorObstacles.minByOrNull { it.distanceMeters ?: 100f }
            if (closest != null) listOf(closest) else emptyList()
        } else if (isCrowd) {
            val nonPeople = corridorObstacles.filter { !it.label.equals("person", ignoreCase = true) }
            val people = corridorObstacles.filter { it.label.equals("person", ignoreCase = true) }
            for (p in people) {
                try {
                    Log.d("NetraWhy", "Dropped: label=${p.label}, reason=CROWD_PERSON_SUPPRESSED")
                } catch (_: Exception) {}
                onWhyLogged?.invoke("CROWD_PERSON_SUPPRESSED")
            }
            nonPeople
        } else {
            corridorObstacles.filter {
                val isFarPerson = it.label.equals("person", ignoreCase = true) && it.distance == Distance.FAR
                if (isFarPerson) {
                    try {
                        Log.d("NetraWhy", "Dropped: label=${it.label}, reason=FAR_PERSON")
                    } catch (_: Exception) {}
                    onWhyLogged?.invoke("FAR_PERSON")
                }
                !isFarPerson
            }
        }

        if (effectiveObstacles.isEmpty()) return null

        val sortedCandidates = effectiveObstacles.sortedWith(
            compareBy<DetectedObstacle> { it.priority.level }
                .thenByDescending { it.distance.urgencyLevel }
                .thenByDescending { it.confidence }
        )

        for (candidate in sortedCandidates) {
            if (candidate.distanceMeters != null && candidate.distanceMeters > 4.0f) {
                try {
                    Log.d("NetraWhy", "Dropped: label=${candidate.label}, reason=TOO_FAR")
                } catch (_: Exception) {}
                onWhyLogged?.invoke("TOO_FAR")
                continue
            }

            val shouldAlert = cooldownTracker.shouldAlert(
                label = candidate.label,
                direction = candidate.direction,
                distance = candidate.distance,
                distanceMeters = candidate.distanceMeters,
                priority = candidate.priority,
                currentTimeMs = currentTimeMs
            )

            if (!shouldAlert) {
                try {
                    Log.d("NetraWhy", "Dropped: label=${candidate.label}, reason=RATE_LIMITED")
                } catch (_: Exception) {}
                onWhyLogged?.invoke("RATE_LIMITED")
                continue
            }

            val sentence = AlertStrings.buildAlertSentence(
                candidate.label,
                candidate.direction,
                candidate.distance,
                language,
                candidate.distanceMeters
            )

            when (candidate.priority) {
                AlertPriority.PRIORITY_1 -> {
                    speakAction(sentence, true)
                    return candidate
                }
                AlertPriority.PRIORITY_2 -> {
                    speakAction(sentence, false)
                    return candidate
                }
                AlertPriority.PRIORITY_3 -> {
                    if (isTtsSpeaking()) {
                        try {
                            Log.d("NetraWhy", "Dropped: label=${candidate.label}, reason=TTS_BUSY")
                        } catch (_: Exception) {}
                        onWhyLogged?.invoke("TTS_BUSY")
                        continue
                    }
                    speakAction(sentence, false)
                    return candidate
                }
            }
        }

        return null
    }

    fun reset() {
        cooldownTracker.reset()
        crowdDetector.reset()
        pathGuidance.reset()
        lastCrowdSpokenTimeMs = 0L
    }
}
