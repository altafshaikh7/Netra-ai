package com.example.netraai.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertPriorityTest {

    @Test
    fun testVehiclePriority1() {
        val vehicles = listOf("car", "bus", "truck", "motorcycle", "bicycle")
        for (v in vehicles) {
            assertEquals("Expected $v to be Priority 1", AlertPriority.PRIORITY_1, AlertPriority.fromLabel(v))
            assertEquals("Expected uppercase $v to be Priority 1", AlertPriority.PRIORITY_1, AlertPriority.fromLabel(v.uppercase()))
        }
    }

    @Test
    fun testLivingBeingPriority2() {
        val livingBeings = listOf("person", "dog", "cow", "horse")
        for (lb in livingBeings) {
            assertEquals("Expected $lb to be Priority 2", AlertPriority.PRIORITY_2, AlertPriority.fromLabel(lb))
            assertEquals("Expected capitalized $lb to be Priority 2", AlertPriority.PRIORITY_2, AlertPriority.fromLabel(lb.replaceFirstChar { it.uppercase() }))
        }
    }

    @Test
    fun testOtherObstaclesPriority3() {
        val obstacles = listOf("chair", "bench", "couch", "table", "traffic light", "fire hydrant", "backpack", "umbrella")
        for (obs in obstacles) {
            assertEquals("Expected $obs to be Priority 3", AlertPriority.PRIORITY_3, AlertPriority.fromLabel(obs))
        }
    }
}
