package com.example.netraai.benchmark

import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkMetricsTest {

    @Test
    fun testDivisionByZeroSafety() {
        val cm = ConfusionMatrix("chair", "STRICT", truePositives = 0, falsePositives = 0, falseNegatives = 0)
        assertEquals(0.0f, cm.precision, 0.001f)
        assertEquals(0.0f, cm.recall, 0.001f)
    }

    @Test
    fun testKnownConfusionCounts() {
        val gt = listOf(
            BenchmarkMetrics.GroundTruthRow("photo1.jpg", "car", true),
            BenchmarkMetrics.GroundTruthRow("photo1.jpg", "person", false),
            BenchmarkMetrics.GroundTruthRow("photo2.jpg", "car", true),
            BenchmarkMetrics.GroundTruthRow("photo2.jpg", "person", true)
        )

        val detections = mapOf(
            "photo1.jpg" to setOf("car", "person"),
            "photo2.jpg" to setOf("car")
        )

        val metrics = BenchmarkMetrics.computeMetrics(gt, detections, "BALANCED")

        val carCm = metrics["car"]!!
        assertEquals(2, carCm.truePositives)
        assertEquals(0, carCm.falsePositives)
        assertEquals(0, carCm.falseNegatives)
        assertEquals(1.0f, carCm.precision, 0.001f)
        assertEquals(1.0f, carCm.recall, 0.001f)

        val personCm = metrics["person"]!!
        assertEquals(0, personCm.truePositives)
        assertEquals(1, personCm.falsePositives)
        assertEquals(1, personCm.falseNegatives)
        assertEquals(0.0f, personCm.precision, 0.001f)
        assertEquals(0.0f, personCm.recall, 0.001f)
    }

    @Test
    fun testEmptySet() {
        val metrics = BenchmarkMetrics.computeMetrics(emptyList(), emptyMap(), "STRICT")
        assertEquals(0, metrics.size)
    }
}
