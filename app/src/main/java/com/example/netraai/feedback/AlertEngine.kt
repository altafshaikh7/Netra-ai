package com.example.netraai.feedback

import com.example.netraai.detection.AlertPriority
import com.example.netraai.detection.DetectedObstacle
import com.example.netraai.detection.Distance
import com.example.netraai.voice.TtsManager

/**
 * AlertEngine manages obstacle prioritization, speech queueing/interruptions,
 * cooldown suppression, and multi-language spoken feedback.
 *
 * Prioritization and Queue Rules:
 * - Priority 1 (Vehicles): Interrupts current speech immediately (QUEUE_FLUSH / interrupt=true).
 * - Priority 2 (Living beings): Queued behind current speech (QUEUE_ADD / interrupt=false).
 * - Priority 3 (Other obstacles): Spoken only when TTS is idle; skipped if TTS is currently speaking.
 *
 * Cooldown Rules:
 * - Around 4 seconds cooldown per (label + direction).
 * - Bypasses cooldown and alerts immediately if obstacle distance gets closer (e.g. FAR -> NEAR or NEAR -> VERY_CLOSE).
 */
class AlertEngine(
    private val speakAction: (text: String, interrupt: Boolean) -> Unit,
    private val isTtsSpeaking: () -> Boolean,
    val cooldownTracker: AlertCooldownTracker = AlertCooldownTracker()
) {

    constructor(
        ttsManager: TtsManager,
        cooldownTracker: AlertCooldownTracker = AlertCooldownTracker()
    ) : this(
        speakAction = { text, interrupt -> ttsManager.speak(text, interrupt) },
        isTtsSpeaking = { ttsManager.isSpeaking },
        cooldownTracker = cooldownTracker
    )

    /**
     * Evaluates a list of detected obstacles in the current frame and speaks the highest priority
     * valid alert based on cooldown and queue rules.
     *
     * @param obstacles List of obstacles detected in the current camera frame.
     * @param language Current language code ("en" or "hi").
     * @param currentTimeMs Current timestamp in ms.
     * @return The [DetectedObstacle] that triggered the alert, or `null` if none was spoken.
     */
    fun processObstacles(
        obstacles: List<DetectedObstacle>,
        language: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): DetectedObstacle? {
        if (obstacles.isEmpty()) return null

        // 1. Sort all candidates by urgency:
        //    Priority (1 before 2 before 3), then Distance (VERY_CLOSE before NEAR before FAR), then confidence
        val sortedCandidates = obstacles.sortedWith(
            compareBy<DetectedObstacle> { it.priority.level }
                .thenByDescending { it.distance.urgencyLevel }
                .thenByDescending { it.confidence }
        )

        // 2. Find the highest priority candidate that passes cooldown
        for (candidate in sortedCandidates) {
            val shouldAlert = cooldownTracker.shouldAlert(
                label = candidate.label,
                direction = candidate.direction,
                distance = candidate.distance,
                currentTimeMs = currentTimeMs
            )

            if (!shouldAlert) continue

            // 3. Apply TTS queueing rules based on Priority
            when (candidate.priority) {
                AlertPriority.PRIORITY_1 -> {
                    // Interrupt current speech
                    val sentence = AlertStrings.buildAlertSentence(
                        candidate.label,
                        candidate.direction,
                        candidate.distance,
                        language,
                        candidate.isBorderline
                    )
                    speakAction(sentence, true)
                    return candidate
                }

                AlertPriority.PRIORITY_2 -> {
                    // Queued speech
                    val sentence = AlertStrings.buildAlertSentence(
                        candidate.label,
                        candidate.direction,
                        candidate.distance,
                        language,
                        candidate.isBorderline
                    )
                    speakAction(sentence, false)
                    return candidate
                }

                AlertPriority.PRIORITY_3 -> {
                    // Only speak when TTS is idle
                    if (isTtsSpeaking()) {
                        // Skip speaking this frame because TTS is already busy
                        continue
                    }
                    val sentence = AlertStrings.buildAlertSentence(
                        candidate.label,
                        candidate.direction,
                        candidate.distance,
                        language,
                        candidate.isBorderline
                    )
                    speakAction(sentence, false)
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Resets the cooldown tracking state (e.g. when walking mode is toggled off).
     */
    fun reset() {
        cooldownTracker.reset()
    }
}
