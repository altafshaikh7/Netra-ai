package com.example.netraai.feedback

import com.example.netraai.detection.AlertPriority
import com.example.netraai.detection.CorridorFilter
import com.example.netraai.detection.CrowdDetector
import com.example.netraai.detection.DetectedObstacle
import com.example.netraai.detection.Distance
import com.example.netraai.voice.TtsManager

/**
 * AlertEngine manages obstacle prioritization, speech queueing/interruptions,
 * corridor filtering, crowd mode detection, and global rate limiting.
 */
class AlertEngine(
    private val speakAction: (text: String, interrupt: Boolean) -> Unit,
    private val isTtsSpeaking: () -> Boolean,
    val cooldownTracker: AlertCooldownTracker = AlertCooldownTracker(),
    val crowdDetector: CrowdDetector = CrowdDetector()
) {
    private var lastCrowdSpokenTimeMs: Long = 0L

    constructor(
        ttsManager: TtsManager,
        cooldownTracker: AlertCooldownTracker = AlertCooldownTracker(),
        crowdDetector: CrowdDetector = CrowdDetector()
    ) : this(
        speakAction = { text, interrupt -> ttsManager.speak(text, interrupt) },
        isTtsSpeaking = { ttsManager.isSpeaking },
        cooldownTracker = cooldownTracker,
        crowdDetector = crowdDetector
    )

    fun processObstacles(
        obstacles: List<DetectedObstacle>,
        language: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): DetectedObstacle? {
        if (obstacles.isEmpty()) return null

        // 1. Filter by corridor and distance
        val corridorObstacles = obstacles.filter { CorridorFilter.isValidForAlert(it) }
        if (corridorObstacles.isEmpty()) return null

        // 2. Evaluate Crowd Mode
        val (isCrowd, newCrowdTime) = crowdDetector.evaluate(corridorObstacles, currentTimeMs, lastCrowdSpokenTimeMs)
        if (isCrowd && newCrowdTime != lastCrowdSpokenTimeMs) {
            lastCrowdSpokenTimeMs = newCrowdTime
            val crowdMsg = AlertStrings.getCrowdMessage(language)
            speakAction(crowdMsg, false)
        }

        // If crowd mode active, stop announcing individual people
        val effectiveObstacles = if (isCrowd) {
            corridorObstacles.filter { !it.label.equals("person", ignoreCase = true) }
        } else {
            corridorObstacles.filter { !(it.label.equals("person", ignoreCase = true) && it.distance == Distance.FAR) }
        }

        if (effectiveObstacles.isEmpty()) return null

        // 3. Sort candidates by urgency
        val sortedCandidates = effectiveObstacles.sortedWith(
            compareBy<DetectedObstacle> { it.priority.level }
                .thenByDescending { it.distance.urgencyLevel }
                .thenByDescending { it.confidence }
        )

        for (candidate in sortedCandidates) {
            if (candidate.distanceMeters != null && candidate.distanceMeters > 4.0f) {
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

            if (!shouldAlert) continue

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
                    if (isTtsSpeaking()) continue
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
        lastCrowdSpokenTimeMs = 0L
    }
}
