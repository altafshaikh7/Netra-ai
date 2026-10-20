package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceTest {

    private val frameArea = 1000f

    @Test
    fun testDistanceVeryClose() {
        // Area ratio >= 0.20 (200 / 1000 = 0.20)
        assertEquals(Distance.VERY_CLOSE, Distance.fromAreaRatio(200f, frameArea))
        assertEquals(Distance.VERY_CLOSE, Distance.fromAreaRatio(500f, frameArea))
        assertEquals(Distance.VERY_CLOSE, Distance.fromAreaRatio(1000f, frameArea))
    }

    @Test
    fun testDistanceNear() {
        // Area ratio in [0.05, 0.20)
        assertEquals(Distance.NEAR, Distance.fromAreaRatio(50f, frameArea))
        assertEquals(Distance.NEAR, Distance.fromAreaRatio(100f, frameArea))
        assertEquals(Distance.NEAR, Distance.fromAreaRatio(199f, frameArea))
    }

    @Test
    fun testDistanceFar() {
        // Area ratio < 0.05
        assertEquals(Distance.FAR, Distance.fromAreaRatio(0f, frameArea))
        assertEquals(Distance.FAR, Distance.fromAreaRatio(20f, frameArea))
        assertEquals(Distance.FAR, Distance.fromAreaRatio(49.9f, frameArea))
    }

    @Test
    fun testIsCloserThanOrdering() {
        assertTrue(Distance.VERY_CLOSE.isCloserThan(Distance.NEAR))
        assertTrue(Distance.VERY_CLOSE.isCloserThan(Distance.FAR))
        assertTrue(Distance.NEAR.isCloserThan(Distance.FAR))

        assertFalse(Distance.FAR.isCloserThan(Distance.NEAR))
        assertFalse(Distance.FAR.isCloserThan(Distance.VERY_CLOSE))
        assertFalse(Distance.NEAR.isCloserThan(Distance.VERY_CLOSE))

        // Same distance is not closer
        assertFalse(Distance.FAR.isCloserThan(Distance.FAR))
        assertFalse(Distance.NEAR.isCloserThan(Distance.NEAR))
        assertFalse(Distance.VERY_CLOSE.isCloserThan(Distance.VERY_CLOSE))
    }

    @Test
    fun testFromDimensions() {
        // Frame 100 x 100 = 10000
        // Box 50 x 50 = 2500 -> ratio 0.25 -> VERY_CLOSE
        assertEquals(
            Distance.VERY_CLOSE,
            Distance.fromDimensions(50f, 50f, 100f, 100f)
        )

        // Box 30 x 30 = 900 -> ratio 0.09 -> NEAR
        assertEquals(
            Distance.NEAR,
            Distance.fromDimensions(30f, 30f, 100f, 100f)
        )

        // Box 10 x 10 = 100 -> ratio 0.01 -> FAR
        assertEquals(
            Distance.FAR,
            Distance.fromDimensions(10f, 10f, 100f, 100f)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testZeroFrameAreaThrows() {
        Distance.fromAreaRatio(10f, 0f)
    }
}
