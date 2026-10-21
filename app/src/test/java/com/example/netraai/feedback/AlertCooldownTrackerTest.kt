package com.example.netraai.feedback

import com.example.netraai.detection.Direction
import com.example.netraai.detection.Distance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertCooldownTrackerTest {

    private val tracker = AlertCooldownTracker(globalRateLimitMs = 0L, repeatCooldownMs = 4000L)

    @Test
    fun testFirstAlertAllowed() {
        val allowed = tracker.shouldAlert(
            label = "car",
            direction = Direction.AHEAD,
            distance = Distance.FAR,
            currentTimeMs = 1000L
        )
        assertTrue("First alert should be allowed", allowed)
    }

    @Test
    fun testSameAlertWithinCooldownSuppressed() {
        // Initial alert
        tracker.shouldAlert("car", Direction.AHEAD, Distance.FAR, currentTimeMs = 1000L)

        // Same obstacle after 2000 ms (< 4000 ms cooldown)
        val repeat1 = tracker.shouldAlert("car", Direction.AHEAD, Distance.FAR, currentTimeMs = 3000L)
        assertFalse("Same alert within cooldown should be suppressed", repeat1)

        // Different direction should be treated separately
        val diffDir = tracker.shouldAlert("car", Direction.LEFT, Distance.FAR, currentTimeMs = 3000L)
        assertTrue("Different direction should be allowed", diffDir)
    }

    @Test
    fun testSameAlertAfterCooldownAllowed() {
        // Initial alert
        tracker.shouldAlert("car", Direction.AHEAD, Distance.FAR, currentTimeMs = 1000L)

        // After cooldown (1000 + 4000 = 5000 ms)
        val repeatAfterCooldown = tracker.shouldAlert("car", Direction.AHEAD, Distance.FAR, currentTimeMs = 5000L)
        assertTrue("Alert after cooldown should be allowed", repeatAfterCooldown)
    }

    @Test
    fun testCloserDistanceRepeatsImmediatelyWithinCooldown() {
        // Initial alert at FAR
        tracker.shouldAlert("car", Direction.AHEAD, Distance.FAR, currentTimeMs = 1000L)

        // Distance gets closer to NEAR at 2000 ms (within 4s cooldown window)
        val nearAllowed = tracker.shouldAlert("car", Direction.AHEAD, Distance.NEAR, currentTimeMs = 2000L)
        assertTrue("Closer distance (NEAR) should alert immediately within cooldown", nearAllowed)

        // Distance gets even closer to VERY_CLOSE at 2500 ms
        val veryCloseAllowed = tracker.shouldAlert("car", Direction.AHEAD, Distance.VERY_CLOSE, currentTimeMs = 2500L)
        assertTrue("Closer distance (VERY_CLOSE) should alert immediately within cooldown", veryCloseAllowed)
    }

    @Test
    fun testFartherDistanceSuppressedWithinCooldown() {
        // Initial alert at VERY_CLOSE
        tracker.shouldAlert("car", Direction.AHEAD, Distance.VERY_CLOSE, currentTimeMs = 1000L)

        // Distance gets farther (NEAR) at 2000 ms (< 4s cooldown)
        val fartherAllowed = tracker.shouldAlert("car", Direction.AHEAD, Distance.NEAR, currentTimeMs = 2000L)
        assertFalse("Farther distance should NOT trigger alert within cooldown", fartherAllowed)
    }

    @Test
    fun testResetClearsHistory() {
        tracker.shouldAlert("car", Direction.AHEAD, Distance.FAR, currentTimeMs = 1000L)
        tracker.reset()

        val allowed = tracker.shouldAlert("car", Direction.AHEAD, Distance.FAR, currentTimeMs = 1500L)
        assertTrue("After reset, alert should be allowed immediately", allowed)
    }
}
