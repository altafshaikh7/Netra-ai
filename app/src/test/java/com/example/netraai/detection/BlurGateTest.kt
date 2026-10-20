package com.example.netraai.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurGateTest {

    @Test
    fun testIsBlurryThreshold() {
        // High sharpness score (> 80.0) -> Sharp (Not blurry)
        assertFalse(BlurGate.isBlurry(150.0f, threshold = 80.0f))
        assertFalse(BlurGate.isBlurry(80.0f, threshold = 80.0f))

        // Low sharpness score (< 80.0) -> Blurry
        assertTrue(BlurGate.isBlurry(40.0f, threshold = 80.0f))
        assertTrue(BlurGate.isBlurry(0.0f, threshold = 80.0f))
    }
}
