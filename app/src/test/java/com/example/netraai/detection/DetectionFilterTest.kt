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
    fun testStrictThresholdsNormal() {
        // Vehicle threshold is 0.75
        assertEquals(0.75f, DetectionFilter.getThreshold("car", DetectionSensitivity.NORMAL), 0.001f)
        assertEquals(0.75f, DetectionFilter.getThreshold("bus", DetectionSensitivity.NORMAL), 0.001f)

        // Person threshold is 0.80
        assertEquals(0.80f, DetectionFilter.getThreshold("person", DetectionSensitivity.NORMAL), 0.001f)

        // Other threshold is 0.80
        assertEquals(0.80f, DetectionFilter.getThreshold("chair", DetectionSensitivity.NORMAL), 0.001f)

        // Vehicle acceptance
        assertTrue(DetectionFilter.isAccepted("car", 0.75f))
        assertFalse(DetectionFilter.isAccepted("car", 0.74f))

        // Person acceptance
        assertTrue(DetectionFilter.isAccepted("person", 0.80f))
        assertFalse(DetectionFilter.isAccepted("person", 0.79f))
    }

    @Test
    fun testSensitivityLevels() {
        // High sensitivity lowers threshold slightly
        assertEquals(0.70f, DetectionFilter.getThreshold("car", DetectionSensitivity.HIGH), 0.001f)
        assertEquals(0.75f, DetectionFilter.getThreshold("person", DetectionSensitivity.HIGH), 0.001f)

        // Low sensitivity raises threshold
        assertEquals(0.80f, DetectionFilter.getThreshold("car", DetectionSensitivity.LOW), 0.001f)
        assertEquals(0.85f, DetectionFilter.getThreshold("person", DetectionSensitivity.LOW), 0.001f)
    }
}
