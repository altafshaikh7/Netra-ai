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
        estimator = DistanceMetersEstimator(windowSize = 5)
    }

    @Test
    fun testUnknownLabelReturnsNull() {
        val dist = estimator.estimateDistance("spaceship", boxHeightPixels = 100f, focalLengthPixels = 500f)
        assertNull(dist)
    }

    @Test
    fun testKnownBoxHeightExpectedMeters() {
        // person height = 1.6m, focal = 500, boxHeight = 400 -> (1.6 * 500) / 400 = 2.0m
        val dist = estimator.estimateDistance("person", boxHeightPixels = 400f, focalLengthPixels = 500f)
        assertNotNull(dist)
        assertEquals(2.0f, dist!!, 0.001f)
    }

    @Test
    fun testMedianSmoothing() {
        // Feed fluctuating values, median should prevail
        estimator.estimateDistance("car", boxHeightPixels = 200f, focalLengthPixels = 500f) // raw 3.75 -> 4.0
        estimator.estimateDistance("car", boxHeightPixels = 200f, focalLengthPixels = 500f) // raw 3.75 -> 4.0
        estimator.estimateDistance("car", boxHeightPixels = 150f, focalLengthPixels = 500f) // raw 5.0 -> 5.0 (outlier)
        estimator.estimateDistance("car", boxHeightPixels = 200f, focalLengthPixels = 500f) // raw 3.75 -> 4.0
        val dist = estimator.estimateDistance("car", boxHeightPixels = 200f, focalLengthPixels = 500f) // raw 3.75 -> 4.0

        assertNotNull(dist)
        // sorted history will have 3.75, 3.75, 3.75, 3.75, 5.0 -> median is 3.75 -> rounded to nearest 0.5 is 4.0
        assertEquals(4.0f, dist!!, 0.001f)
    }
}
