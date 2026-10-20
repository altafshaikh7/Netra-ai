package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionFilterTest {

    @Test
    fun testWhitelistedLabels() {
        val expected = listOf(
            "person", "bicycle", "car", "motorcycle", "bus", "truck",
            "dog", "cow", "horse", "chair", "bench", "couch",
            "dining table", "potted plant", "traffic light", "stop sign"
        )
        for (label in expected) {
            assertTrue("Expected $label to be whitelisted", DetectionFilter.isWhitelisted(label))
            assertTrue("Expected uppercase $label to be whitelisted", DetectionFilter.isWhitelisted(label.uppercase()))
        }

        // Non-whitelisted items must be ignored
        val nonWhitelisted = listOf("cat", "cell phone", "bottle", "cup", "fork", "book", "backpack", "umbrella")
        for (label in nonWhitelisted) {
            assertFalse("Expected $label to NOT be whitelisted", DetectionFilter.isWhitelisted(label))
        }
    }

    @Test
    fun testVehicleVsOtherThresholdsNormal() {
        // Vehicle threshold is 0.55
        assertEquals(0.55f, DetectionFilter.getThreshold("car", DetectionSensitivity.NORMAL), 0.001f)
        assertEquals(0.55f, DetectionFilter.getThreshold("bus", DetectionSensitivity.NORMAL), 0.001f)

        // Other threshold is 0.60
        assertEquals(0.60f, DetectionFilter.getThreshold("person", DetectionSensitivity.NORMAL), 0.001f)
        assertEquals(0.60f, DetectionFilter.getThreshold("chair", DetectionSensitivity.NORMAL), 0.001f)

        // Vehicle acceptance
        assertTrue(DetectionFilter.isAccepted("car", 0.55f))
        assertFalse(DetectionFilter.isAccepted("car", 0.54f))

        // Person acceptance
        assertTrue(DetectionFilter.isAccepted("person", 0.60f))
        assertFalse(DetectionFilter.isAccepted("person", 0.59f))
    }

    @Test
    fun testBorderlineConfidence() {
        // Car: threshold is 0.55, borderline is [0.55, 0.67)
        assertTrue(DetectionFilter.isBorderline("car", 0.56f))
        assertTrue(DetectionFilter.isBorderline("car", 0.65f))
        assertFalse(DetectionFilter.isBorderline("car", 0.75f)) // Confident
        assertFalse(DetectionFilter.isBorderline("car", 0.50f)) // Rejected

        // Person: threshold is 0.60, borderline is [0.60, 0.72)
        assertTrue(DetectionFilter.isBorderline("person", 0.62f))
        assertFalse(DetectionFilter.isBorderline("person", 0.78f))
    }

    @Test
    fun testSensitivityLevels() {
        // High sensitivity lowers threshold
        assertEquals(0.45f, DetectionFilter.getThreshold("car", DetectionSensitivity.HIGH), 0.001f)
        assertEquals(0.50f, DetectionFilter.getThreshold("person", DetectionSensitivity.HIGH), 0.001f)

        // Low sensitivity raises threshold
        assertEquals(0.65f, DetectionFilter.getThreshold("car", DetectionSensitivity.LOW), 0.001f)
        assertEquals(0.70f, DetectionFilter.getThreshold("person", DetectionSensitivity.LOW), 0.001f)
    }
}
