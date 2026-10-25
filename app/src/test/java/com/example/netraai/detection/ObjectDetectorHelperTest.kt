package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectDetectorHelperTest {

    @Test
    fun testComputeUprightSizeRotations() {
        val rawW = 1080f
        val rawH = 1920f

        // Rotation 0 -> (1080, 1920)
        val (w0, h0) = ObjectDetectorHelper.computeUprightSize(rawW, rawH, 0)
        assertEquals(1080f, w0, 0.001f)
        assertEquals(1920f, h0, 0.001f)

        // Rotation 90 -> (1920, 1080)
        val (w90, h90) = ObjectDetectorHelper.computeUprightSize(rawW, rawH, 90)
        assertEquals(1920f, w90, 0.001f)
        assertEquals(1080f, h90, 0.001f)

        // Rotation 180 -> (1080, 1920)
        val (w180, h180) = ObjectDetectorHelper.computeUprightSize(rawW, rawH, 180)
        assertEquals(1080f, w180, 0.001f)
        assertEquals(1920f, h180, 0.001f)

        // Rotation 270 -> (1920, 1080)
        val (w270, h270) = ObjectDetectorHelper.computeUprightSize(rawW, rawH, 270)
        assertEquals(1920f, w270, 0.001f)
        assertEquals(1080f, h270, 0.001f)
    }
}
