package com.example.netraai.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrowdDetectorTest {

    private lateinit var detector: CrowdDetector

    @Before
    fun setUp() {
        detector = CrowdDetector(personCountThreshold = 3, crowdCooldownMs = 15000L)
    }

    @Test
    fun testCrowdTriggerWhenThreeOrMorePeople() {
        val people = listOf(
            DetectedObstacle("person", 0.9f, Direction.LEFT, Distance.NEAR, AlertPriority.PRIORITY_2, normalizedCenterX = 0.4f),
            DetectedObstacle("person", 0.9f, Direction.AHEAD, Distance.NEAR, AlertPriority.PRIORITY_2, normalizedCenterX = 0.5f),
            DetectedObstacle("person", 0.9f, Direction.RIGHT, Distance.NEAR, AlertPriority.PRIORITY_2, normalizedCenterX = 0.6f)
        )

        val (isCrowd, _) = detector.evaluate(people, currentTimeMs = 20000L, lastCrowdSpokenTimeMs = 0L)
        assertTrue(isCrowd)
    }

    @Test
    fun testNoCrowdWhenFewPeople() {
        val people = listOf(
            DetectedObstacle("person", 0.9f, Direction.LEFT, Distance.NEAR, AlertPriority.PRIORITY_2, normalizedCenterX = 0.4f)
        )

        val (isCrowd, _) = detector.evaluate(people, currentTimeMs = 20000L, lastCrowdSpokenTimeMs = 0L)
        assertFalse(isCrowd)
    }
}
