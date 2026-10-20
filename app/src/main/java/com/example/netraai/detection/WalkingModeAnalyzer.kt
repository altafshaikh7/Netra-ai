package com.example.netraai.detection

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * CameraX [ImageAnalysis.Analyzer] that processes frames using [ObjectDetectorHelper].
 *
 * Implements frame rate throttling (default 3 FPS) so inference does not overheat the device
 * or overload the system, and safely handles ImageProxy lifecycle.
 *
 * @param detectorHelper The MediaPipe detector helper instance.
 * @param targetFps Target frames per second to analyze (default 3 FPS).
 * @param onObstaclesDetected Callback invoked with detected obstacles when a frame is analyzed.
 */
class WalkingModeAnalyzer(
    private val detectorHelper: ObjectDetectorHelper,
    val targetFps: Int = DEFAULT_TARGET_FPS,
    private val onObstaclesDetected: (List<DetectedObstacle>) -> Unit
) : ImageAnalysis.Analyzer {

    val throttleIntervalMs: Long = if (targetFps > 0) 1000L / targetFps else 333L

    @Volatile
    var isWalkingModeEnabled: Boolean = false

    private var lastAnalysisTimestampMs: Long = 0L

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (!isWalkingModeEnabled) {
                return
            }

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnalysisTimestampMs < throttleIntervalMs) {
                return
            }
            lastAnalysisTimestampMs = currentTime

            // Convert CameraX ImageProxy to RGB Bitmap
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val obstacles = detectorHelper.detect(rotatedBitmap)
            onObstaclesDetected(obstacles)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image frame", e)
        } finally {
            imageProxy.close()
        }
    }

    companion object {
        private const val TAG = "WalkingModeAnalyzer"
        const val DEFAULT_TARGET_FPS = 3
    }
}
