package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionFilterTest {

    private val strictProfile = DetectionProfileValues.STRICT

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
        assertEquals(0.75f, DetectionFilter.getThreshold("car", DetectionSensitivity.NORMAL, strictProfile), 0.001f)
        assertEquals(0.75f, DetectionFilter.getThreshold("bus", DetectionSensitivity.NORMAL, strictProfile), 0.001f)

        // Person threshold is 0.80
        assertEquals(0.80f, DetectionFilter.getThreshold("person", DetectionSensitivity.NORMAL, strictProfile), 0.001f)

        // Other threshold is 0.80
        assertEquals(0.80f, DetectionFilter.getThreshold("chair", DetectionSensitivity.NORMAL, strictProfile), 0.001f)

        // Vehicle acceptance
        assertTrue(DetectionFilter.isAccepted("car", 0.75f, profileValues = strictProfile))
        assertFalse(DetectionFilter.isAccepted("car", 0.74f, profileValues = strictProfile))

        // Person acceptance
        assertTrue(DetectionFilter.isAccepted("person", 0.80f, profileValues = strictProfile))
        assertFalse(DetectionFilter.isAccepted("person", 0.79f, profileValues = strictProfile))
    }

    @Test
    fun testSensitivityLevels() {
        // High sensitivity lowers threshold slightly
        assertEquals(0.70f, DetectionFilter.getThreshold("car", DetectionSensitivity.HIGH, strictProfile), 0.001f)
        assertEquals(0.75f, DetectionFilter.getThreshold("person", DetectionSensitivity.HIGH, strictProfile), 0.001f)

        // Low sensitivity raises threshold
        assertEquals(0.80f, DetectionFilter.getThreshold("car", DetectionSensitivity.LOW, strictProfile), 0.001f)
        assertEquals(0.85f, DetectionFilter.getThreshold("person", DetectionSensitivity.LOW, strictProfile), 0.001f)
    }

    @Test
    fun testRejectReasonFunction() {
        // Not whitelisted
        assertEquals("NOT_WHITELISTED", DetectionFilter.rejectReason("cat", 0.95f, 0f, 0f, 100f, 100f, 1000f, 1000f, profileValues = strictProfile))

        // Below threshold
        assertNotNull(DetectionFilter.rejectReason("person", 0.70f, 0f, 0f, 300f, 300f, 1000f, 1000f, profileValues = strictProfile))

        // Too small (area ratio < 0.02)
        assertEquals("TOO_SMALL", DetectionFilter.rejectReason("person", 0.90f, 0f, 0f, 10f, 10f, 1000f, 1000f, profileValues = strictProfile))

        // Accepted -> null
        assertNull(DetectionFilter.rejectReason("person", 0.90f, 200f, 200f, 500f, 500f, 1000f, 1000f, profileValues = strictProfile))
    }

    @Test
    fun testEdgeBoxFilteringRules() {
        val width = 1000f
        val height = 1000f

        // Box touching top edge (top = 0f <= 20f), area ratio = 300*300 / 1000000 = 0.09 (>= 0.02)
        // Person/Vehicle with score 0.80 touching top/bottom edge must NOT be rejected
        assertTrue(DetectionFilter.isAccepted("person", 0.80f, 350f, 0f, 650f, 300f, width, height, profileValues = strictProfile))
        assertTrue(DetectionFilter.isAccepted("car", 0.80f, 350f, 0f, 650f, 300f, width, height, profileValues = strictProfile))

        // Other object (e.g. chair) with score 0.80 touching top edge must be rejected (needs >= 0.85)
        assertFalse(DetectionFilter.isAccepted("chair", 0.80f, 350f, 0f, 650f, 300f, width, height, profileValues = strictProfile))
        assertTrue(DetectionFilter.isAccepted("chair", 0.85f, 350f, 0f, 650f, 300f, width, height, profileValues = strictProfile))

        // Box touching left/right edge (left = 0f) with score < 0.85 must be rejected
        assertFalse(DetectionFilter.isAccepted("person", 0.80f, 0f, 350f, 300f, 650f, width, height, profileValues = strictProfile))
        assertTrue(DetectionFilter.isAccepted("person", 0.85f, 0f, 350f, 300f, 650f, width, height, profileValues = strictProfile))
    }
}
