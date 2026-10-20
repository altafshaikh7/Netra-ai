package com.example.netraai.feedback

import com.example.netraai.detection.AlertPriority
import com.example.netraai.detection.DetectedObstacle
import com.example.netraai.detection.Direction
import com.example.netraai.detection.Distance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class AlertEngineTest {

    private var spokenText: String? = null
    private var wasInterrupted: Boolean? = null
    private var mockIsSpeaking: Boolean = false
    private lateinit var alertEngine: AlertEngine

    @Before
    fun setUp() {
        spokenText = null
        wasInterrupted = null
        mockIsSpeaking = false

        alertEngine = AlertEngine(
            speakAction = { text, interrupt ->
                spokenText = text
                wasInterrupted = interrupt
            },
            isTtsSpeaking = { mockIsSpeaking }
        )
    }

    @Test
    fun testPriority1InterruptsCurrentSpeech() {
        val car = DetectedObstacle(
            label = "car",
            confidence = 0.9f,
            direction = Direction.AHEAD,
            distance = Distance.VERY_CLOSE,
            priority = AlertPriority.PRIORITY_1
        )

        val spoken = alertEngine.processObstacles(listOf(car), language = "en")

        assertNotNull(spoken)
        assertEquals("Car ahead, very close.", spokenText)
        assertEquals(true, wasInterrupted)
    }

    @Test
    fun testPriority2QueuesSpeech() {
        val person = DetectedObstacle(
            label = "person",
            confidence = 0.85f,
            direction = Direction.LEFT,
            distance = Distance.NEAR,
            priority = AlertPriority.PRIORITY_2
        )

        val spoken = alertEngine.processObstacles(listOf(person), language = "en")

        assertNotNull(spoken)
        assertEquals("Person left, near.", spokenText)
        assertEquals(false, wasInterrupted)
    }

    @Test
    fun testPriority3SpokenWhenTtsIsIdle() {
        mockIsSpeaking = false // TTS is idle
        val chair = DetectedObstacle(
            label = "chair",
            confidence = 0.7f,
            direction = Direction.RIGHT,
            distance = Distance.FAR,
            priority = AlertPriority.PRIORITY_3
        )

        val spoken = alertEngine.processObstacles(listOf(chair), language = "en")

        assertNotNull(spoken)
        assertEquals("Chair right, far.", spokenText)
        assertEquals(false, wasInterrupted)
    }

    @Test
    fun testPriority3SkippedWhenTtsIsBusy() {
        mockIsSpeaking = true // TTS is currently speaking
        val chair = DetectedObstacle(
            label = "chair",
            confidence = 0.7f,
            direction = Direction.RIGHT,
            distance = Distance.FAR,
            priority = AlertPriority.PRIORITY_3
        )

        val spoken = alertEngine.processObstacles(listOf(chair), language = "en")

        assertNull("Priority 3 must be skipped when TTS is busy", spoken)
        assertNull(spokenText)
    }

    @Test
    fun testHindiLocalization() {
        val car = DetectedObstacle(
            label = "car",
            confidence = 0.95f,
            direction = Direction.AHEAD,
            distance = Distance.VERY_CLOSE,
            priority = AlertPriority.PRIORITY_1
        )

        val spoken = alertEngine.processObstacles(listOf(car), language = "hi")

        assertNotNull(spoken)
        assertEquals("कार सामने, बहुत पास।", spokenText)
    }

    @Test
    fun testMultipleObstaclesHighestPrioritySelected() {
        val chair = DetectedObstacle("chair", 0.9f, Direction.RIGHT, Distance.FAR, AlertPriority.PRIORITY_3)
        val person = DetectedObstacle("person", 0.8f, Direction.LEFT, Distance.NEAR, AlertPriority.PRIORITY_2)
        val bus = DetectedObstacle("bus", 0.75f, Direction.AHEAD, Distance.VERY_CLOSE, AlertPriority.PRIORITY_1)

        // Pass in random order
        val spoken = alertEngine.processObstacles(listOf(chair, bus, person), language = "en")

        assertNotNull(spoken)
        assertEquals("bus", spoken?.label)
        assertEquals("Bus ahead, very close.", spokenText)
        assertEquals(true, wasInterrupted)
    }
}
