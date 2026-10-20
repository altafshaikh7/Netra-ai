package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectionTest {

    private val frameWidth = 300f

    @Test
    fun testLeftThird() {
        // Frame width 300: Left third is [0, 100)
        assertEquals(Direction.LEFT, Direction.fromBoundingBoxCenter(0f, frameWidth))
        assertEquals(Direction.LEFT, Direction.fromBoundingBoxCenter(50f, frameWidth))
        assertEquals(Direction.LEFT, Direction.fromBoundingBoxCenter(99.9f, frameWidth))
    }

    @Test
    fun testAheadMiddleThird() {
        // Frame width 300: Middle third is [100, 200]
        assertEquals(Direction.AHEAD, Direction.fromBoundingBoxCenter(100f, frameWidth))
        assertEquals(Direction.AHEAD, Direction.fromBoundingBoxCenter(150f, frameWidth))
        assertEquals(Direction.AHEAD, Direction.fromBoundingBoxCenter(200f, frameWidth))
    }

    @Test
    fun testRightThird() {
        // Frame width 300: Right third is (200, 300]
        assertEquals(Direction.RIGHT, Direction.fromBoundingBoxCenter(200.1f, frameWidth))
        assertEquals(Direction.RIGHT, Direction.fromBoundingBoxCenter(250f, frameWidth))
        assertEquals(Direction.RIGHT, Direction.fromBoundingBoxCenter(300f, frameWidth))
    }

    @Test
    fun testNormalizedCoordinates() {
        assertEquals(Direction.LEFT, Direction.fromNormalizedCenterX(0.15f))
        assertEquals(Direction.AHEAD, Direction.fromNormalizedCenterX(0.50f))
        assertEquals(Direction.RIGHT, Direction.fromNormalizedCenterX(0.85f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidFrameWidthThrows() {
        Direction.fromBoundingBoxCenter(50f, 0f)
    }
}
