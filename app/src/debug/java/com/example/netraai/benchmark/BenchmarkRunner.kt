package com.example.netraai.benchmark

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.example.netraai.detection.DetectionProfile
import com.example.netraai.detection.ObjectDetectorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class BenchmarkRunner(private val context: Context) {

    suspend fun runBenchmark(onProgress: (String) -> Unit): Map<String, Map<String, ConfusionMatrix>> = withContext(Dispatchers.IO) {
        val benchDir = context.getExternalFilesDir("benchmark")
            ?: File(context.filesDir, "benchmark")

        if (!benchDir.exists() || !benchDir.isDirectory) {
            benchDir.mkdirs()
            val msg = "Benchmark folder empty: ${benchDir.absolutePath}"
            onProgress(msg)
            Log.d("NetraBench", msg)
            return@withContext emptyMap()
        }

        val labelsFile = File(benchDir, "labels.csv")
        if (!labelsFile.exists()) {
            val msg = "labels.csv missing in ${benchDir.absolutePath}"
            onProgress(msg)
            Log.d("NetraBench", msg)
            return@withContext emptyMap()
        }

        val groundTruth = BenchmarkMetrics.parseLabelsCsv(labelsFile.readText())
        val detectorHelper = ObjectDetectorHelper(context)

        val resultsByProfile = mutableMapOf<String, Map<String, ConfusionMatrix>>()

        for (profile in listOf(DetectionProfile.STRICT, DetectionProfile.BALANCED, DetectionProfile.SENSITIVE)) {
            detectorHelper.detectionProfile = profile
            onProgress("Running profile: ${profile.name}...")

            val detectedByFile = mutableMapOf<String, MutableSet<String>>()

            val imageFiles = benchDir.listFiles { file ->
                file.extension.lowercase(Locale.ROOT) in listOf("jpg", "jpeg", "png")
            } ?: emptyArray()

            for (file in imageFiles) {
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                    val detections = detectorHelper.detect(bitmap, 0)
                    val labels = detections.map { it.label.lowercase(Locale.ROOT) }.toSet()
                    detectedByFile[file.name] = labels.toMutableSet()
                } catch (e: Exception) {
                    Log.e("NetraBench", "Error processing file: ${file.name}", e)
                }
            }

            val metrics = BenchmarkMetrics.computeMetrics(groundTruth, detectedByFile, profile.name)
            resultsByProfile[profile.name] = metrics

            Log.d("NetraBench", "--- PROFILE: ${profile.name} ---")
            for ((label, cm) in metrics) {
                Log.d("NetraBench", "Label: $label | TP: ${cm.truePositives} | FP: ${cm.falsePositives} | FN: ${cm.falseNegatives} | Prec: ${String.format(Locale.US, "%.2f", cm.precision)} | Rec: ${String.format(Locale.US, "%.2f", cm.recall)}")
            }
        }

        detectorHelper.close()

        val resultsCsv = BenchmarkMetrics.formatResultsCsv(resultsByProfile)
        val resultsFile = File(benchDir, "results.csv")
        resultsFile.writeText(resultsCsv)
        Log.d("NetraBench", "Wrote results to: ${resultsFile.absolutePath}")
        onProgress("Complete! Results saved to results.csv")

        resultsByProfile
    }
}
