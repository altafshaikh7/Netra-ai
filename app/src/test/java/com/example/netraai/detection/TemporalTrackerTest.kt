package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemporalTrackerTest {

    private lateinit var tracker: TemporalTracker

    @Before
    fun setUp() {
        tracker = TemporalTracker(windowSize = 6, minConfirmations = 4)
    }

    private fun createObstacle(
        label: String = "car",
        confidence: Float = 0.85f,
        direction: Direction = Direction.AHEAD,
        normalizedCenterX: Float = 0.5f
    ): DetectedObstacle {
        return DetectedObstacle(
            label = label,
            confidence = confidence,
            direction = direction,
            distance = Distance.NEAR,
            priority = AlertPriority.PRIORITY_1,
            normalizedCenterX = normalizedCenterX
        )
    }

    @Test
    fun testUnconfirmedUnderFourFrames() {
        val car = createObstacle("car")

        val res1 = tracker.filterConfirmed(listOf(car))
        assertTrue("Frame 1 must not be confirmed", res1.isEmpty())

        val res2 = tracker.filterConfirmed(listOf(car))
        assertTrue("Frame 2 must not be confirmed", res2.isEmpty())

        val res3 = tracker.filterConfirmed(listOf(car))
        assertTrue("Frame 3 must not be confirmed", res3.isEmpty())
    }

    @Test
    fun testConfirmedOnFourthFrameWithHighAverageScore() {
        val car = createObstacle("car", confidence = 0.85f)

        tracker.filterConfirmed(listOf(car)) // Frame 1
        tracker.filterConfirmed(listOf(car)) // Frame 2
        tracker.filterConfirmed(listOf(car)) // Frame 3
        val res4 = tracker.filterConfirmed(listOf(car)) // Frame 4

        assertEquals(1, res4.size)
        assertEquals("car", res4[0].label)
    }

    @Test
    fun testScatteredLowScoreHitsRejectedByAverageScoreRule() {
        // Threshold for person is >= 0.55
        val lowPerson = createObstacle("person", confidence = 0.40f)

        repeat(4) {
            tracker.filterConfirmed(listOf(lowPerson))
        }

        // Average score is 0.40 < threshold -> Must NOT be confirmed
        val res = tracker.filterConfirmed(listOf(lowPerson))
        assertTrue("Scattered or low average score hits must not confirm", res.isEmpty())
    }

    @Test
    fun testResetClearsHistory() {
        val person = createObstacle("person", confidence = 0.85f)
        tracker.filterConfirmed(listOf(person))
        tracker.filterConfirmed(listOf(person))
        tracker.reset()

        // After reset, 1st frame is not confirmed
        val res = tracker.filterConfirmed(listOf(person))
        assertTrue(res.isEmpty())
    }
}
