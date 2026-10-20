package com.example.netraai.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlashlightAutoControllerTest {

    private lateinit var controller: FlashlightAutoController

    @Before
    fun setUp() {
        controller = FlashlightAutoController(
            lowThreshold = 45f,
            highThreshold = 75f,
            minHoldDurationMs = 5000L
        )
    }

    @Test
    fun testTurnsOnInLowLight() {
        // Scene luma 30 is below lowThreshold (45) -> turns ON (returns true)
        val result = controller.evaluate(30f, currentTimeMs = 1000L)
        assertEquals(true, result)
        assertTrue(controller.isTorchActive)
    }

    @Test
    fun testHysteresisHoldPreventsRapidOff() {
        // Turn ON at t = 1000
        controller.evaluate(30f, currentTimeMs = 1000L)
        assertTrue(controller.isTorchActive)

        // Rapid brightness spike to 90 at t = 3000 (only 2000 ms elapsed, < 5000 ms hold)
        val result = controller.evaluate(90f, currentTimeMs = 3000L)
        assertNull("Torch must NOT switch OFF before min hold duration (5000 ms)", result)
        assertTrue("Torch must remain ON during hold period", controller.isTorchActive)
    }

    @Test
    fun testTurnsOffAfterHoldDurationInBrightLight() {
        // Turn ON at t = 1000
        controller.evaluate(30f, currentTimeMs = 1000L)
        assertTrue(controller.isTorchActive)

        // At t = 6500 (5500 ms elapsed >= 5000 ms hold), bright light 85f
        val result = controller.evaluate(85f, currentTimeMs = 6500L)
        assertEquals(false, result)
        assertFalse(controller.isTorchActive)
    }

    @Test
    fun testIntermediateLumaDoesNotTriggerChange() {
        // Intermediate luma 60f (between 45 and 75) when initially OFF
        val resultOff = controller.evaluate(60f, currentTimeMs = 1000L)
        assertNull("Intermediate luma should not turn torch ON", resultOff)
        assertFalse(controller.isTorchActive)

        // Turn ON in darkness at t = 2000
        controller.evaluate(20f, currentTimeMs = 2000L)
        assertTrue(controller.isTorchActive)

        // At t = 8000 (hold expired), intermediate luma 60f
        val resultOn = controller.evaluate(60f, currentTimeMs = 8000L)
        assertNull("Intermediate luma should not turn torch OFF (hysteresis band)", resultOn)
        assertTrue("Torch must remain ON in intermediate band", controller.isTorchActive)
    }

    @Test
    fun testResetClearsState() {
        controller.evaluate(30f, currentTimeMs = 1000L)
        assertTrue(controller.isTorchActive)

        controller.reset(torchState = false)
        assertFalse(controller.isTorchActive)
    }
}
