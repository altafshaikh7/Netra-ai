package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MotionGateTest {

    private var mockTimeMs = 10000L
    private lateinit var motionGate: MotionGate

    @Before
    fun setUp() {
        mockTimeMs = 10000L
        motionGate = MotionGate(
            angularSpeedThreshold = 0.8f,
            settleTimeMs = 500L,
            timeProvider = { mockTimeMs }
        )
    }

    @Test
    fun testSteadyCameraReturnsActive() {
        // Slow movement (0.2 rad/s)
        motionGate.onSensorChangedValues(0.1f, 0.1f, 0.1f)
        assertFalse(motionGate.isMovingFast)
        assertEquals(GateStatus.ACTIVE, motionGate.evaluateStatus(mockTimeMs))
    }

    @Test
    fun testFastMotionTriggersPausedMoving() {
        // Fast panning (1.2 rad/s)
        motionGate.onSensorChangedValues(0.8f, 0.8f, 0.5f)
        assertTrue(motionGate.isMovingFast)
        assertEquals(GateStatus.PAUSED_MOVING, motionGate.evaluateStatus(mockTimeMs))
    }

    @Test
    fun testSettleTimeDelayAfterMotionStops() {
        // 1. Fast motion at t=10000ms
        mockTimeMs = 10000L
        motionGate.onSensorChangedValues(1.0f, 1.0f, 1.0f)
        assertEquals(GateStatus.PAUSED_MOVING, motionGate.evaluateStatus(mockTimeMs))

        // 2. Motion stops at t=10100ms
        mockTimeMs = 10100L
        motionGate.onSensorChangedValues(0.0f, 0.0f, 0.0f)
        assertFalse(motionGate.isMovingFast)

        // 3. At t=10300ms (200ms after motion stop < 500ms settle time) -> PAUSED_SETTLING
        mockTimeMs = 10300L
        assertEquals(GateStatus.PAUSED_SETTLING, motionGate.evaluateStatus(mockTimeMs))

        // 4. At t=10600ms (500ms after motion stop) -> ACTIVE
        mockTimeMs = 10600L
        assertEquals(GateStatus.ACTIVE, motionGate.evaluateStatus(mockTimeMs))
    }
}
