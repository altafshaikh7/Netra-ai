package com.example.netraai.benchmark

import java.util.Locale

data class ConfusionMatrix(
    val label: String,
    val profileName: String,
    var truePositives: Int = 0,
    var falsePositives: Int = 0,
    var falseNegatives: Int = 0
) {
    val precision: Float
        get() {
            val total = truePositives + falsePositives
            return if (total > 0) truePositives.toFloat() / total else 0.0f
        }

    val recall: Float
        get() {
            val total = truePositives + falseNegatives
            return if (total > 0) truePositives.toFloat() / total else 0.0f
        }
}

object BenchmarkMetrics {
    data class GroundTruthRow(
        val filename: String,
        val label: String,
        val isPresent: Boolean
    )

    fun parseLabelsCsv(csvContent: String): List<GroundTruthRow> {
        val lines = csvContent.lines()
        val rows = mutableListOf<GroundTruthRow>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("filename", ignoreCase = true)) continue
            val parts = trimmed.split(",")
            if (parts.size >= 3) {
                val filename = parts[0].trim()
                val label = parts[1].trim().lowercase(Locale.ROOT)
                val isPresent = parts[2].trim().lowercase(Locale.ROOT) in listOf("1", "true", "yes", "present")
                rows.add(GroundTruthRow(filename, label, isPresent))
            }
        }
        return rows
    }

    fun computeMetrics(
        groundTruth: List<GroundTruthRow>,
        detectedByFile: Map<String, Set<String>>,
        profileName: String
    ): Map<String, ConfusionMatrix> {
        val matrixMap = mutableMapOf<String, ConfusionMatrix>()

        for (gt in groundTruth) {
            val cm = matrixMap.getOrPut(gt.label) { ConfusionMatrix(gt.label, profileName) }
            val detectedLabels = detectedByFile[gt.filename] ?: emptySet()
            val wasDetected = detectedLabels.contains(gt.label)

            if (gt.isPresent) {
                if (wasDetected) {
                    cm.truePositives++
                } else {
                    cm.falseNegatives++
                }
            } else {
                if (wasDetected) {
                    cm.falsePositives++
                }
            }
        }

        return matrixMap
    }

    fun formatResultsCsv(metricsByProfile: Map<String, Map<String, ConfusionMatrix>>): String {
        val sb = StringBuilder()
        sb.append("label,profile,tp,fp,fn,precision,recall\n")
        for ((profile, labelMap) in metricsByProfile) {
            for ((_, cm) in labelMap) {
                sb.append("${cm.label},$profile,${cm.truePositives},${cm.falsePositives},${cm.falseNegatives},${String.format(Locale.US, "%.4f", cm.precision)},${String.format(Locale.US, "%.4f", cm.recall)}\n")
            }
        }
        return sb.toString()
    }
}
