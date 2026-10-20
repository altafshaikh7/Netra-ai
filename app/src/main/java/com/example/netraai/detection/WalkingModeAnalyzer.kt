package com.example.netraai.detection

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * CameraX [ImageAnalysis.Analyzer] for Live Walking Mode.
 *
 * Responsibilities:
 * - Throttles analysis to target FPS (default 3 FPS).
 * - Estimates ambient brightness from the Y plane using stride sampling.
 * - Runs MediaPipe object detection with sensor rotation.
 * - Confirms detections temporally via [TemporalTracker] (3 of 5 frames).
 * - Computes real-time FPS and inference latency for debug overlay.
 */
class WalkingModeAnalyzer(
    private val detectorHelper: ObjectDetectorHelper,
    val temporalTracker: TemporalTracker = TemporalTracker(),
    val targetFps: Int = DEFAULT_TARGET_FPS,
    private val onLumaSampled: ((Float) -> Unit)? = null,
    private val onBitmapCaptured: ((android.graphics.Bitmap) -> Unit)? = null,
    private val onAnalysisComplete: (
        confirmed: List<DetectedObstacle>,
        rawAccepted: List<DetectedObstacle>,
        fps: Int,
        inferenceTimeMs: Long,
        uprightWidth: Int,
        uprightHeight: Int
    ) -> Unit
) : ImageAnalysis.Analyzer {

    val throttleIntervalMs: Long = if (targetFps > 0) 1000L / targetFps else 333L

    @Volatile
    var isWalkingModeEnabled: Boolean = false

    private var lastAnalysisTimestampMs: Long = 0L

    // FPS calculation tracking
    private var frameCount = 0
    private var fpsCalculationStartMs = System.currentTimeMillis()
    var currentFps: Int = 0
        private set

    override fun analyze(imageProxy: ImageProxy) {
        try {
            // 1. Sample luma regardless of walking mode if onLumaSampled is requested (for auto flashlight)
            onLumaSampled?.let { callback ->
                val luma = estimateAverageLuma(imageProxy, sampleStep = LUMA_SAMPLE_STEP)
                callback(luma)
            }

            if (!isWalkingModeEnabled) {
                return
            }

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnalysisTimestampMs < throttleIntervalMs) {
                return
            }
            lastAnalysisTimestampMs = currentTime

            // Calculate current FPS
            frameCount++
            val elapsedFpsTime = currentTime - fpsCalculationStartMs
            if (elapsedFpsTime >= 1000L) {
                currentFps = ((frameCount * 1000L) / elapsedFpsTime).toInt()
                frameCount = 0
                fpsCalculationStartMs = currentTime
            }

            // Convert to Bitmap
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val isRotatedSideways = (rotationDegrees == 90 || rotationDegrees == 270)
            val uprightWidth = if (isRotatedSideways) bitmap.height else bitmap.width
            val uprightHeight = if (isRotatedSideways) bitmap.width else bitmap.height

            // Run detection
            val rawAccepted = detectorHelper.detect(bitmap, rotationDegrees)

            // Temporal confirmation filter (3 of last 5 frames)
            val confirmed = temporalTracker.filterConfirmed(rawAccepted)

            onAnalysisComplete(
                confirmed,
                rawAccepted,
                currentFps,
                detectorHelper.lastInferenceTimeMs,
                uprightWidth,
                uprightHeight
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in walking mode analyzer", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Estimates average scene luma (0..255) by sampling pixels in the Y (luminance) plane.
     */
    fun estimateAverageLuma(imageProxy: ImageProxy, sampleStep: Int = 16): Float {
        val planes = imageProxy.planes
        if (planes.isEmpty()) return 128f

        val yPlane = planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height

        var sum = 0L
        var count = 0

        for (row in 0 until height step sampleStep) {
            val rowOffset = row * rowStride
            for (col in 0 until width step sampleStep) {
                val index = rowOffset + (col * pixelStride)
                if (index < buffer.limit()) {
                    val luma = buffer.get(index).toInt() and 0xFF
                    sum += luma
                    count++
                }
            }
        }

        return if (count > 0) sum.toFloat() / count else 128f
    }

    fun reset() {
        temporalTracker.reset()
        frameCount = 0
        currentFps = 0
    }

    companion object {
        private const val TAG = "WalkingModeAnalyzer"
        const val DEFAULT_TARGET_FPS = 3
        const val LUMA_SAMPLE_STEP = 16
    }
}
