package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PathGuidanceTest {

    private var currentTime = 1000L
    private lateinit var guidance: PathGuidance

    @Before
    fun setUp() {
        currentTime = 1000L
        guidance = PathGuidance(timeProvider = { currentTime })
    }

    @Test
    fun testZoneSplit() {
        val leftPerson = DetectedObstacle("person", 0.9f, Direction.LEFT, Distance.NEAR, AlertPriority.PRIORITY_2, normalizedCenterX = 0.2f, distanceMeters = 2.0f)
        val centerCar = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.NEAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 2.0f)
        val rightChair = DetectedObstacle("chair", 0.9f, Direction.RIGHT, Distance.NEAR, AlertPriority.PRIORITY_3, normalizedCenterX = 0.8f, distanceMeters = 2.0f)

        val zones = guidance.evaluateZones(listOf(leftPerson, centerCar, rightChair))

        assertEquals("person", zones[Zone.LEFT]?.nearestObstacle?.label)
        assertEquals("car", zones[Zone.CENTER]?.nearestObstacle?.label)
        assertEquals("chair", zones[Zone.RIGHT]?.nearestObstacle?.label)
    }

    @Test
    fun testCenterBlockedReturnsObstacleSentence() {
        val centerCar = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.NEAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 2.0f)
        val sentence = guidance.evaluateGuidanceSentence(listOf(centerCar), GateStatus.ACTIVE, "en", currentTime)

        assertNotNull(sentence)
        assertTrue(sentence!!.contains("Car ahead, about 2 meters."))
    }

    @Test
    fun testAllThreeBlockedSaysStop() {
        val leftObs = DetectedObstacle("chair", 0.9f, Direction.LEFT, Distance.NEAR, AlertPriority.PRIORITY_3, normalizedCenterX = 0.2f, distanceMeters = 1.5f)
        val centerObs = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.NEAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 1.5f)
        val rightObs = DetectedObstacle("chair", 0.9f, Direction.RIGHT, Distance.NEAR, AlertPriority.PRIORITY_3, normalizedCenterX = 0.8f, distanceMeters = 1.5f)

        val sentence = guidance.evaluateGuidanceSentence(listOf(leftObs, centerObs, rightObs), GateStatus.ACTIVE, "en", currentTime)

        assertNotNull(sentence)
        assertEquals("Stop.", sentence)
    }

    @Test
    fun testNoClearAnnouncementsWhenEmpty() {
        val res = guidance.evaluateGuidanceSentence(emptyList(), GateStatus.ACTIVE, "en", currentTime)
        assertNull(res)
    }

    @Test
    fun testGuidanceRateLimiting() {
        val car1 = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.NEAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 2.0f)

        // t = 1000ms -> spoke
        val s1 = guidance.evaluateGuidanceSentence(listOf(car1), GateStatus.ACTIVE, "en", 1000L)
        assertNotNull(s1)

        // t = 2000ms (< 2s global limit) -> null
        val s2 = guidance.evaluateGuidanceSentence(listOf(car1), GateStatus.ACTIVE, "en", 2000L)
        assertNull(s2)

        // t = 3500ms (same sentence, distance not dropped by 1m, < 5s cooldown) -> null
        val s3 = guidance.evaluateGuidanceSentence(listOf(car1), GateStatus.ACTIVE, "en", 3500L)
        assertNull(s3)

        // t = 3500ms with distance dropped to 0.8m (dropped >= 1m) -> spoke immediately
        val carCloser = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.VERY_CLOSE, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 0.8f)
        val s4 = guidance.evaluateGuidanceSentence(listOf(carCloser), GateStatus.ACTIVE, "en", 3500L)
        assertNotNull(s4)
    }
}
