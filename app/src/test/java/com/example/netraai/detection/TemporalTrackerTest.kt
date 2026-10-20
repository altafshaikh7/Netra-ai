package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemporalTrackerTest {

    private lateinit var tracker: TemporalTracker

    @Before
    fun setUp() {
        tracker = TemporalTracker(windowSize = 5, minConfirmations = 3)
    }

    private fun createObstacle(
        label: String = "car",
        direction: Direction = Direction.AHEAD,
        normalizedCenterX: Float = 0.5f
    ): DetectedObstacle {
        return DetectedObstacle(
            label = label,
            confidence = 0.85f,
            direction = direction,
            distance = Distance.NEAR,
            priority = AlertPriority.PRIORITY_1,
            normalizedCenterX = normalizedCenterX
        )
    }

    @Test
    fun testUnconfirmedUnderThreeFrames() {
        val car = createObstacle("car")

        // Frame 1
        val res1 = tracker.filterConfirmed(listOf(car))
        assertTrue("Frame 1 should not be confirmed yet", res1.isEmpty())

        // Frame 2
        val res2 = tracker.filterConfirmed(listOf(car))
        assertTrue("Frame 2 should not be confirmed yet", res2.isEmpty())
    }

    @Test
    fun testConfirmedOnThirdFrame() {
        val car = createObstacle("car")

        tracker.filterConfirmed(listOf(car)) // Frame 1
        tracker.filterConfirmed(listOf(car)) // Frame 2
        val res3 = tracker.filterConfirmed(listOf(car)) // Frame 3

        assertEquals(1, res3.size)
        assertEquals("car", res3[0].label)
    }

    @Test
    fun testTransientFalsePositiveIgnored() {
        val glitch = createObstacle("chair")

        // Glitch appears only in 1 frame
        val res1 = tracker.filterConfirmed(listOf(glitch))
        assertTrue(res1.isEmpty())

        // Next 4 frames are empty
        repeat(4) {
            val emptyRes = tracker.filterConfirmed(emptyList())
            assertTrue(emptyRes.isEmpty())
        }

        // Glitch appears again; total in window is only 1, so still not confirmed
        val res6 = tracker.filterConfirmed(listOf(glitch))
        assertTrue(res6.isEmpty())
    }

    @Test
    fun testDifferentPositionsNotGrouped() {
        val carLeft = createObstacle("car", direction = Direction.LEFT, normalizedCenterX = 0.1f)
        val carRight = createObstacle("car", direction = Direction.RIGHT, normalizedCenterX = 0.9f)

        // Alternate Left and Right
        tracker.filterConfirmed(listOf(carLeft))  // Frame 1: Left
        tracker.filterConfirmed(listOf(carRight)) // Frame 2: Right
        tracker.filterConfirmed(listOf(carLeft))  // Frame 3: Left
        tracker.filterConfirmed(listOf(carRight)) // Frame 4: Right

        // Neither has reached 3 confirmations in same position
        val res5 = tracker.filterConfirmed(listOf(carLeft)) // Frame 5: Left (3rd time for Left)
        assertEquals(1, res5.size)
        assertEquals(Direction.LEFT, res5[0].direction)
    }

    @Test
    fun testResetClearsHistory() {
        val person = createObstacle("person")
        tracker.filterConfirmed(listOf(person))
        tracker.filterConfirmed(listOf(person))
        tracker.reset()

        // After reset, 1st frame is not confirmed
        val res = tracker.filterConfirmed(listOf(person))
        assertTrue(res.isEmpty())
    }
}
