package com.example.netraai.detection

import org.junit.Assert.assertEquals
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
        val (dist, _) = estimator.estimateDistance("spaceship", boxHeightPixels = 100f, focalLengthPixels = 500f)
        assertNull(dist)
    }

    @Test
    fun testKnownPhoneExampleFocalComputation() {
        // focal = 4.0 mm, sensor = 6.4 x 4.8 mm, upright portrait frame 480 x 640
        val focalPx = DistanceMetersEstimator.computeFocalPixels(
            focalLengthMm = 4.0f,
            sensorWidthMm = 6.4f,
            sensorHeightMm = 4.8f,
            frameWidth = 480f,
            frameHeight = 640f
        )
        // sensorLongSide = 6.4, frameLongSide = 640 -> (4.0 / 6.4) * 640 = 400.0
        assertEquals(400.0f, focalPx, 0.001f)

        // person height = 1.6m, focalPx = 400, boxHeight = 200 -> (1.6 * 400) / 200 = 3.2 -> rounded to 3.0
        val (dist, _) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = focalPx)
        assertNotNull(dist)
        assertEquals(3.0f, dist!!, 0.001f)
    }

    @Test
    fun testTrackedObjectSmoothing() {
        val (d1, id1) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 100f, centerY = 100f)
        val (_, id2) = estimator.estimateDistance("person", boxHeightPixels = 200f, focalLengthPixels = 400f, centerX = 102f, centerY = 102f)
        assertEquals(id1, id2)
        assertNotNull(d1)
    }
}
