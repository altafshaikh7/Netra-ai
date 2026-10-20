package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DistanceHysteresisTest {

    private lateinit var estimator: DistanceEstimator

    @Before
    fun setUp() {
        estimator = DistanceEstimator(
            nearEnterRatio = 0.06f,
            nearExitRatio = 0.04f,
            veryCloseEnterRatio = 0.22f,
            veryCloseExitRatio = 0.18f
        )
    }

    @Test
    fun testInitialEstimateWithoutPreviousState() {
        // Without previous state: >= 0.20 is VERY_CLOSE, >= 0.05 is NEAR, else FAR
        assertEquals(Distance.FAR, estimator.computeDistanceWithHysteresis(0.03f, null))
        assertEquals(Distance.NEAR, estimator.computeDistanceWithHysteresis(0.05f, null))
        assertEquals(Distance.VERY_CLOSE, estimator.computeDistanceWithHysteresis(0.20f, null))
    }

    @Test
    fun testFarToNearTransitionHysteresis() {
        // While FAR: must reach nearEnterRatio (0.06) to become NEAR
        // 0.05 is not enough from FAR
        assertEquals(Distance.FAR, estimator.computeDistanceWithHysteresis(0.05f, Distance.FAR))
        assertEquals(Distance.NEAR, estimator.computeDistanceWithHysteresis(0.06f, Distance.FAR))
        assertEquals(Distance.NEAR, estimator.computeDistanceWithHysteresis(0.10f, Distance.FAR))
    }

    @Test
    fun testNearToFarTransitionHysteresis() {
        // While NEAR: ratio hovering at 0.05 or 0.045 remains NEAR!
        // Only drops to FAR when strictly below nearExitRatio (0.04)
        assertEquals(Distance.NEAR, estimator.computeDistanceWithHysteresis(0.05f, Distance.NEAR))
        assertEquals(Distance.NEAR, estimator.computeDistanceWithHysteresis(0.042f, Distance.NEAR))
        assertEquals(Distance.FAR, estimator.computeDistanceWithHysteresis(0.039f, Distance.NEAR))
    }

    @Test
    fun testNearToVeryCloseTransitionHysteresis() {
        // While NEAR: must reach veryCloseEnterRatio (0.22) to become VERY_CLOSE
        // 0.205 remains NEAR
        assertEquals(Distance.NEAR, estimator.computeDistanceWithHysteresis(0.205f, Distance.NEAR))
        assertEquals(Distance.VERY_CLOSE, estimator.computeDistanceWithHysteresis(0.22f, Distance.NEAR))
        assertEquals(Distance.VERY_CLOSE, estimator.computeDistanceWithHysteresis(0.30f, Distance.NEAR))
    }

    @Test
    fun testVeryCloseToNearTransitionHysteresis() {
        // While VERY_CLOSE: ratio hovering at 0.20 remains VERY_CLOSE
        // Only drops to NEAR when strictly below veryCloseExitRatio (0.18)
        assertEquals(Distance.VERY_CLOSE, estimator.computeDistanceWithHysteresis(0.20f, Distance.VERY_CLOSE))
        assertEquals(Distance.VERY_CLOSE, estimator.computeDistanceWithHysteresis(0.185f, Distance.VERY_CLOSE))
        assertEquals(Distance.NEAR, estimator.computeDistanceWithHysteresis(0.179f, Distance.VERY_CLOSE))
    }

    @Test
    fun testPerObjectTracking() {
        // Test estimateDistance state tracking
        // First observation at 0.20 -> VERY_CLOSE
        val d1 = estimator.estimateDistance("car", Direction.AHEAD, 0.20f)
        assertEquals(Distance.VERY_CLOSE, d1)

        // Slight jitter down to 0.19 -> remains VERY_CLOSE due to hysteresis!
        val d2 = estimator.estimateDistance("car", Direction.AHEAD, 0.19f)
        assertEquals(Distance.VERY_CLOSE, d2)

        // Drop below 0.18 -> transitions to NEAR
        val d3 = estimator.estimateDistance("car", Direction.AHEAD, 0.17f)
        assertEquals(Distance.NEAR, d3)
    }
}
