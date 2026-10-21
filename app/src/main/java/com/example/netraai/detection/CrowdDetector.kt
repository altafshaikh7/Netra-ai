package com.example.netraai.detection

class CrowdDetector(
    val personCountThreshold: Int = 3,
    val crowdCooldownMs: Long = 15000L
) {
    private val recentFramesPersonCounts = mutableListOf<Int>()

    fun evaluate(obstacles: List<DetectedObstacle>, currentTimeMs: Long, lastCrowdSpokenTimeMs: Long): Pair<Boolean, Long> {
        val personCount = obstacles.count { it.label.equals("person", ignoreCase = true) }
        recentFramesPersonCounts.add(personCount)
        while (recentFramesPersonCounts.size > 5) {
            recentFramesPersonCounts.removeAt(0)
        }

        val avgPersons = if (recentFramesPersonCounts.isNotEmpty()) recentFramesPersonCounts.average() else 0.0
        val isCrowd = avgPersons >= personCountThreshold || personCount >= personCountThreshold

        if (isCrowd) {
            if (currentTimeMs - lastCrowdSpokenTimeMs >= crowdCooldownMs) {
                return Pair(true, currentTimeMs)
            }
        }
        return Pair(false, lastCrowdSpokenTimeMs)
    }

    fun reset() {
        recentFramesPersonCounts.clear()
    }
}
