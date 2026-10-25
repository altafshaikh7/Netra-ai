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
 * - Confirms detections temporally via [TemporalTracker].
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
            Log.i(TAG, "ImageAnalysis analyzer resolution: ${imageProxy.width}x${imageProxy.height}")

            val luma = estimateAverageLuma(imageProxy, sampleStep = LUMA_SAMPLE_STEP)
            onLumaSampled?.invoke(luma)

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

            frameCount++
            val elapsedFpsTime = currentTime - fpsCalculationStartMs
            if (elapsedFpsTime >= 1000L) {
                currentFps = ((frameCount * 1000L) / elapsedFpsTime).toInt()
                frameCount = 0
                fpsCalculationStartMs = currentTime
            }

            val profileValues = DetectionProfileValues.forProfile(detectorHelper.detectionProfile)

            val angularSpeed = motionGate.currentAngularSpeed
            val motionStatus = motionGate.evaluateStatus(currentTime)

            val sharpnessScore = if (profileValues.isBlurGateEnabled) {
                BlurGate.calculateSharpness(imageProxy, step = BLUR_SAMPLE_STEP)
            } else {
                1000.0f
            }

            val gateStatus = when {
                motionStatus == GateStatus.PAUSED_MOVING -> GateStatus.PAUSED_MOVING
                motionStatus == GateStatus.PAUSED_SETTLING -> GateStatus.PAUSED_SETTLING
                profileValues.isBlurGateEnabled && BlurGate.isBlurry(sharpnessScore, profileValues.blurThreshold) -> GateStatus.PAUSED_BLURRY
                else -> GateStatus.ACTIVE
            }

            val rawBitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            if (debugCaptureRequested) {
                debugCaptureRequested = false
                saveDebugFrame(rawBitmap, rotationDegrees)
            }

            val rawAccepted = detectorHelper.detect(rawBitmap, rotationDegrees)

            val isRotatedSideways = (rotationDegrees == 90 || rotationDegrees == 270)
            val uprightWidth = if (isRotatedSideways) rawBitmap.height else rawBitmap.width
            val uprightHeight = if (isRotatedSideways) rawBitmap.width else rawBitmap.height

            val confirmed = if (gateStatus == GateStatus.ACTIVE) {
                temporalTracker.filterConfirmed(rawAccepted, detectorHelper.sensitivity)
            } else {
                emptyList()
            }

            onAnalysisComplete(
                confirmed,
                rawAccepted,
                currentFps,
                detectorHelper.lastInferenceTimeMs,
                uprightWidth,
                uprightHeight,
                gateStatus,
                angularSpeed,
                sharpnessScore
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in WalkingModeAnalyzer", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun saveDebugFrame(bitmap: Bitmap, rotationDegrees: Int) {
        val context = appContext ?: return
        try {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val uprightBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            val debugFile = File(context.cacheDir, "netra_debug_frame_${System.currentTimeMillis()}.jpg")
            FileOutputStream(debugFile).use { out ->
                uprightBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.i(TAG, "Debug upright frame saved: ${debugFile.absolutePath}")
            onDebugFrameSaved?.invoke(debugFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving debug frame", e)
        }
    }

    private fun estimateAverageLuma(imageProxy: ImageProxy, sampleStep: Int = 16): Float {
        val planes = imageProxy.planes
        if (planes.isEmpty()) return 128f
        val buffer = planes[0].buffer ?: return 128f
        val length = buffer.remaining()
        if (length == 0) return 128f

        var totalLuma = 0L
        var count = 0

        var i = 0
        while (i < length) {
            totalLuma += (buffer.get(i).toInt() and 0xFF)
            count++
            i += sampleStep
        }

        return if (count > 0) totalLuma.toFloat() / count else 128f
    }

    fun reset() {
        temporalTracker.reset()
        motionGate.reset()
        frameCount = 0
        fpsCalculationStartMs = System.currentTimeMillis()
        currentFps = 0
    }

    companion object {
        private const val TAG = "WalkingModeAnalyzer"
        const val DEFAULT_TARGET_FPS = 3
        const val LUMA_SAMPLE_STEP = 16
        const val BLUR_SAMPLE_STEP = 4
    }
}
