package com.example.netraai.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.File
import java.io.FileOutputStream

/**
 * CameraX [ImageAnalysis.Analyzer] for Live Walking Mode.
 *
 * Responsibilities:
 * - Throttles analysis to target FPS (default 3 FPS).
 * - Estimates ambient brightness from the Y plane using stride sampling.
 * - Evaluates motion (gyroscope) and blur (Laplacian variance) gates.
 * - Runs MediaPipe object detection with sensor rotation.
 * - Confirms detections temporally via [TemporalTracker] (4 of 6 frames).
 * - Supports capturing the exact upright model-input frame for debugging.
 * - Supports [readTextRequested] for offline OCR on demand.
 */
class WalkingModeAnalyzer(
    private val detectorHelper: ObjectDetectorHelper,
    val temporalTracker: TemporalTracker = TemporalTracker(),
    val motionGate: MotionGate = MotionGate(),
    val targetFps: Int = DEFAULT_TARGET_FPS,
    private val onLumaSampled: ((Float) -> Unit)? = null,
    private val onAnalysisComplete: (
        confirmed: List<DetectedObstacle>,
        rawAccepted: List<DetectedObstacle>,
        fps: Int,
        inferenceTimeMs: Long,
        uprightWidth: Int,
        uprightHeight: Int,
        gateStatus: GateStatus,
        angularSpeed: Float,
        sharpnessScore: Float
    ) -> Unit
) : ImageAnalysis.Analyzer {

    val throttleIntervalMs: Long = if (targetFps > 0) 1000L / targetFps else 333L

    @Volatile
    var isWalkingModeEnabled: Boolean = false

    @Volatile
    var debugCaptureRequested: Boolean = false

    @Volatile
    var readTextRequested: Boolean = false
    var onTextScanned: ((Bitmap, Int) -> Unit)? = null

    private var appContext: Context? = null
    var onDebugFrameSaved: ((String) -> Unit)? = null

    private var lastAnalysisTimestampMs: Long = 0L

    // FPS calculation tracking
    private var frameCount = 0
    private var fpsCalculationStartMs = System.currentTimeMillis()
    var currentFps: Int = 0
        private set

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            // Report analyzer resolution (Instruction 4)
            Log.i(TAG, "ImageAnalysis analyzer resolution: ${imageProxy.width}x${imageProxy.height}")

            // 1. Sample luma regardless of walking mode (for auto flashlight)
            val luma = estimateAverageLuma(imageProxy, sampleStep = LUMA_SAMPLE_STEP)
            onLumaSampled?.invoke(luma)

            // OCR request handling (works even when walking mode is off)
            if (readTextRequested) {
                readTextRequested = false
                val bitmap = imageProxy.toBitmap()
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                onTextScanned?.invoke(bitmap, rotationDegrees)
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

            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val isRotatedSideways = (rotationDegrees == 90 || rotationDegrees == 270)
            val uprightWidth = if (isRotatedSideways) bitmap.height else bitmap.width
            val uprightHeight = if (isRotatedSideways) bitmap.width else bitmap.height

            // STEP 1 Item 1: Debug frame capture (exact upright bitmap passed to model)
            if (debugCaptureRequested && appContext != null) {
                debugCaptureRequested = false
                val path = saveUprightBitmapToCache(appContext!!, bitmap, rotationDegrees)
                if (path != null) {
                    Log.i(TAG, "Saved exact upright model-input frame to: $path")
                    onDebugFrameSaved?.invoke(path)
                }
            }

            // Camera covered / dark check (luma < 10f) -> clear tracker
            if (luma < 10f) {
                temporalTracker.reset()
            }

            // STEP 3 Item 2: Motion Gate evaluation
            val motionStatus = motionGate.evaluateStatus(currentTime)
            if (motionStatus != GateStatus.ACTIVE) {
                Log.d(
                    "NetraGate",
                    "Frame dropped: $motionStatus (angularSpeed=${String.format("%.2f", motionGate.currentAngularSpeed)} rad/s)"
                )
                temporalTracker.reset()
                onAnalysisComplete(
                    emptyList(),
                    emptyList(),
                    currentFps,
                    0L,
                    uprightWidth,
                    uprightHeight,
                    motionStatus,
                    motionGate.currentAngularSpeed,
                    0f
                )
                return
            }

            // STEP 3 Item 4: Blur Gate evaluation
            val sharpnessScore = BlurGate.calculateSharpness(imageProxy)
            if (BlurGate.isBlurry(sharpnessScore)) {
                Log.d(
                    "NetraGate",
                    "Frame dropped: PAUSED_BLURRY (sharpnessScore=${String.format("%.1f", sharpnessScore)} < ${BlurGate.DEFAULT_SHARPNESS_THRESHOLD})"
                )
                temporalTracker.reset()
                onAnalysisComplete(
                    emptyList(),
                    emptyList(),
                    currentFps,
                    0L,
                    uprightWidth,
                    uprightHeight,
                    GateStatus.PAUSED_BLURRY,
                    motionGate.currentAngularSpeed,
                    sharpnessScore
                )
                return
            }

            // Run object detection on steady, sharp frame
            val rawAccepted = detectorHelper.detect(bitmap, rotationDegrees)

            // Temporal confirmation filter (4 of last 6 frames)
            val confirmed = temporalTracker.filterConfirmed(rawAccepted, detectorHelper.sensitivity)

            onAnalysisComplete(
                confirmed,
                rawAccepted,
                currentFps,
                detectorHelper.lastInferenceTimeMs,
                uprightWidth,
                uprightHeight,
                GateStatus.ACTIVE,
                motionGate.currentAngularSpeed,
                sharpnessScore
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in walking mode analyzer", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun saveUprightBitmapToCache(context: Context, rawBitmap: Bitmap, rotationDegrees: Int): String? {
        return try {
            val matrix = Matrix()
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees.toFloat())
            }
            val uprightBitmap = Bitmap.createBitmap(
                rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
            )
            val file = File(context.cacheDir, "netra_debug_frame.jpg")
            FileOutputStream(file).use { out ->
                uprightBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (uprightBitmap != rawBitmap) {
                uprightBitmap.recycle()
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save debug frame", e)
            null
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
        motionGate.reset()
        frameCount = 0
        currentFps = 0
    }

    companion object {
        private const val TAG = "WalkingModeAnalyzer"
        const val DEFAULT_TARGET_FPS = 3
        const val LUMA_SAMPLE_STEP = 16
    }
}
