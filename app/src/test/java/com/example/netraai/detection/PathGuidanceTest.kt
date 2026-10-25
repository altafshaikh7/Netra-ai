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
    fun testCenterBlockedWithLeftClear() {
        val centerCar = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.NEAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 2.0f)
        val sentence = guidance.evaluateGuidanceSentence(listOf(centerCar), GateStatus.ACTIVE, "en", currentTime)

        assertNotNull(sentence)
        assertTrue(sentence!!.contains("Car ahead, about 2 meters."))
        assertTrue(sentence.contains("Left looks clear."))
    }

    @Test
    fun testAllThreeBlockedSaysStop() {
        val leftObs = DetectedObstacle("chair", 0.9f, Direction.LEFT, Distance.NEAR, AlertPriority.PRIORITY_3, normalizedCenterX = 0.2f, distanceMeters = 1.5f)
        val centerObs = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.NEAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 1.5f)
        val rightObs = DetectedObstacle("chair", 0.9f, Direction.RIGHT, Distance.NEAR, AlertPriority.PRIORITY_3, normalizedCenterX = 0.8f, distanceMeters = 1.5f)

        val sentence = guidance.evaluateGuidanceSentence(listOf(leftObs, centerObs, rightObs), GateStatus.ACTIVE, "en", currentTime)

        assertNotNull(sentence)
        assertTrue(sentence!!.contains("Stop."))
    }

    @Test
    fun testPathLooksClearContinuousActiveGateFourSeconds() {
        // At t = 1000ms: gate starts active
        guidance.evaluateGuidanceSentence(emptyList(), GateStatus.ACTIVE, "en", currentTime)

        // At t = 3000ms (2s active) -> not yet 4s, return null
        currentTime = 3000L
        val res2 = guidance.evaluateGuidanceSentence(emptyList(), GateStatus.ACTIVE, "en", currentTime)
        assertNull(res2)

        // At t = 5000ms (4s active) -> returns "Path looks clear."
        currentTime = 5000L
        val res4 = guidance.evaluateGuidanceSentence(emptyList(), GateStatus.ACTIVE, "en", currentTime)
        assertNotNull(res4)
        assertEquals("Path looks clear.", res4)
    }

    @Test
    fun testPausedGateUnsteadyWarningAfterTwoSeconds() {
        // Paused gate at t = 1000ms
        guidance.evaluateGuidanceSentence(emptyList(), GateStatus.PAUSED_MOVING, "en", currentTime)

        // At t = 2000ms (1s paused) -> null
        currentTime = 2000L
        val res1 = guidance.evaluateGuidanceSentence(emptyList(), GateStatus.PAUSED_MOVING, "en", currentTime)
        assertNull(res1)

        // At t = 3100ms (>2s paused) -> speaks "Camera not steady, slow down." once
        currentTime = 3100L
        val res2 = guidance.evaluateGuidanceSentence(emptyList(), GateStatus.PAUSED_MOVING, "en", currentTime)
        assertNotNull(res2)
        assertEquals("Camera not steady, slow down.", res2)

        // Subsequent paused checks return null
        currentTime = 4000L
        val res3 = guidance.evaluateGuidanceSentence(emptyList(), GateStatus.PAUSED_MOVING, "en", currentTime)
        assertNull(res3)
    }
}
