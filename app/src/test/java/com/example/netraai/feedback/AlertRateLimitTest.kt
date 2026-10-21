package com.example.netraai.feedback

import com.example.netraai.detection.AlertPriority
import com.example.netraai.detection.Direction
import com.example.netraai.detection.Distance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlertRateLimitTest {

    private lateinit var tracker: AlertCooldownTracker

    @Before
    fun setUp() {
        tracker = AlertCooldownTracker(globalRateLimitMs = 2000L, repeatCooldownMs = 5000L)
    }

    @Test
    fun testGlobalRateLimitSuppressesRapidAlerts() {
        val t0 = 1000L
        val allowed1 = tracker.shouldAlert("chair", Direction.AHEAD, Distance.NEAR, 3.0f, AlertPriority.PRIORITY_3, currentTimeMs = t0)
        assertTrue(allowed1)

        // 1 second later (< 2s global limit), second alert suppressed
        val allowed2 = tracker.shouldAlert("table", Direction.RIGHT, Distance.NEAR, 3.0f, AlertPriority.PRIORITY_3, currentTimeMs = t0 + 1000L)
        assertFalse(allowed2)

        // 2.5 seconds later (>= 2s global limit), allowed
        val allowed3 = tracker.shouldAlert("table", Direction.RIGHT, Distance.NEAR, 3.0f, AlertPriority.PRIORITY_3, currentTimeMs = t0 + 2500L)
        assertTrue(allowed3)
    }

    @Test
    fun testDistanceDropBypassesRepeatCooldown() {
        val t0 = 1000L
        tracker.shouldAlert("car", Direction.AHEAD, Distance.NEAR, 4.0f, AlertPriority.PRIORITY_1, currentTimeMs = t0)

        // 1 second later, distance dropped by 1.5 meters (4.0 -> 2.5) -> should alert immediately
        val allowedCloser = tracker.shouldAlert("car", Direction.AHEAD, Distance.VERY_CLOSE, 2.5f, AlertPriority.PRIORITY_1, currentTimeMs = t0 + 1000L)
        assertTrue(allowedCloser)
    }
}
