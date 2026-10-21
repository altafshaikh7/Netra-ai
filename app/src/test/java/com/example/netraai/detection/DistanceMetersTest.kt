package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DistanceMetersTest {

    private lateinit var estimator: DistanceMetersEstimator

    @Before
    fun setUp() {
        estimator = DistanceMetersEstimator(windowSize = 5, maxMissedFrames = 5)
    }

    @Test
    fun testUnknownLabelReturnsNull() {
        estimator.beginFrame()
        val (dist, _) = estimator.estimateDistance("spaceship", boxHeightPixels = 100f, focalLengthPixels = 500f)
        assertNull(dist)
    }

    @Test
    fun testRealMedianSmoothingWithOutlier() {
        // person height = 1.6m, focal = 400. boxHeight = 200 -> raw = 3.2 -> 3.0m
        // Outlier boxHeight = 100 -> raw = 6.4 -> 6.5m
        estimator.beginFrame()
        estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 50f, centerY = 50f)
        estimator.beginFrame()
        estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 50f, centerY = 50f)
        estimator.beginFrame()
        estimator.estimateDistance("person", boxHeightPixels = 100f, focalLengthPixels = 400f, centerX = 50f, centerY = 50f) // outlier
        estimator.beginFrame()
        estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 50f, centerY = 50f)
        estimator.beginFrame()
        val (dist, _) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 50f, centerY = 50f)

        assertNotNull(dist)
        // Median of [3.0, 3.0, 6.5, 3.0, 3.0] sorted is [3.0, 3.0, 3.0, 3.0, 6.5] -> median is 3.0
        assertEquals(3.0f, dist!!, 0.001f)
    }

    @Test
    fun testTwoDetectionsInOneFrameGetSeparateTracks() {
        estimator.beginFrame()
        val (_, id1) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 100f, centerY = 100f)
        val (_, id2) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 400f, centerY = 400f)
        assertNotEquals(id1, id2)
    }

    @Test
    fun testTrackExpiresOnlyAfterMaxMissedFrames() {
        // Track created at frame 1
        estimator.beginFrame()
        val (_, id1) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 100f, centerY = 100f)

        // Miss 4 frames (frames 2, 3, 4, 5) - track should still be alive (maxMissedFrames = 5)
        repeat(4) {
            estimator.beginFrame()
        }
        val (_, idActive) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 102f, centerY = 102f)
        assertEquals(id1, idActive)

        // Miss 5th frame (frame 6) -> track expires
        repeat(5) {
            estimator.beginFrame()
        }
        val (_, idExpired) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 100f, centerY = 100f)
        assertNotEquals(id1, idExpired)
    }
}
