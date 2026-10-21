package com.example.netraai.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorridorFilterTest {

    @Test
    fun testCorridorBoundary() {
        assertTrue(CorridorFilter.isInCorridor(0.50f))
        assertTrue(CorridorFilter.isInCorridor(0.30f))
        assertTrue(CorridorFilter.isInCorridor(0.70f))
        assertFalse(CorridorFilter.isInCorridor(0.20f))
        assertFalse(CorridorFilter.isInCorridor(0.80f))
    }

    @Test
    fun testIsValidForAlertDistance() {
        val inCorridorClose = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.NEAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 2.0f)
        val inCorridorFar = DetectedObstacle("car", 0.9f, Direction.AHEAD, Distance.FAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.5f, distanceMeters = 5.0f)
        val outOfCorridor = DetectedObstacle("car", 0.9f, Direction.LEFT, Distance.NEAR, AlertPriority.PRIORITY_1, normalizedCenterX = 0.1f, distanceMeters = 2.0f)

        assertTrue(CorridorFilter.isValidForAlert(inCorridorClose))
        assertFalse(CorridorFilter.isValidForAlert(inCorridorFar))
        assertFalse(CorridorFilter.isValidForAlert(outOfCorridor))
    }
}
