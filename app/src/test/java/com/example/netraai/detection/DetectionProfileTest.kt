package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionProfileTest {

    @Test
    fun testProfileValues() {
        val strict = DetectionProfileValues.forProfile(DetectionProfile.STRICT)
        assertEquals(0.80f, strict.personThreshold, 0.001f)
        assertEquals(4, strict.trackerMinConfirmations)
        assertTrue(strict.isBlurGateEnabled)

        val balanced = DetectionProfileValues.forProfile(DetectionProfile.BALANCED)
        assertEquals(0.55f, balanced.personThreshold, 0.001f)
        assertEquals(3, balanced.trackerMinConfirmations)
        assertTrue(balanced.isBlurGateEnabled)

        val sensitive = DetectionProfileValues.forProfile(DetectionProfile.SENSITIVE)
        assertEquals(0.45f, sensitive.personThreshold, 0.001f)
        assertEquals(2, sensitive.trackerMinConfirmations)
        assertFalse(sensitive.isBlurGateEnabled)
    }
}
