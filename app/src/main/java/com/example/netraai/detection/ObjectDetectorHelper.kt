package com.example.netraai.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * Helper class wrapping MediaPipe Tasks Vision ObjectDetector with float32 EfficientDet-Lite2 model
 * (with INT8 model switchable via constant), GPU acceleration with CPU fallback, Camera2 focal length estimation, and strict filtering.
 */
class ObjectDetectorHelper(
    context: Context,
    modelAssetName: String = DEFAULT_MODEL_NAME,
    var sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL,
    val distanceEstimator: DistanceEstimator = DistanceEstimator()
) {
    private val appContext = context.applicationContext
    private var detector: ObjectDetector? = null
    private val distanceMetersEstimator = DistanceMetersEstimator()
    private var cachedFocalLengthPixels: Float = 0f

    var lastInferenceTimeMs: Long = 0L
        private set

    init {
        // Attempt GPU acceleration first; fall back to CPU if unavailable
        var initialized = false
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAssetName)
                .setDelegate(Delegate.GPU)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setScoreThreshold(BASE_DETECTOR_SCORE_THRESHOLD)
                .setMaxResults(DetectionFilter.MAX_RESULTS)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            detector = ObjectDetector.createFromOptions(appContext, options)
            initialized = true
            Log.i(TAG, "MediaPipe ObjectDetector initialized successfully with GPU delegate using $modelAssetName")
        } catch (e: Exception) {
            Log.w(TAG, "GPU delegate unavailable or failed; falling back to CPU delegate", e)
        }

        if (!initialized) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelAssetName)
                    .setDelegate(Delegate.CPU)
                    .build()

                val options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setScoreThreshold(BASE_DETECTOR_SCORE_THRESHOLD)
                    .setMaxResults(DetectionFilter.MAX_RESULTS)
                    .setRunningMode(RunningMode.IMAGE)
                    .build()

                detector = ObjectDetector.createFromOptions(appContext, options)
                Log.i(TAG, "MediaPipe ObjectDetector initialized successfully with CPU delegate using $modelAssetName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MediaPipe ObjectDetector with CPU delegate", e)
            }
        }
    }

    private fun getFocalLengthPixels(uprightHeight: Float): Float {
        if (cachedFocalLengthPixels > 0f) return cachedFocalLengthPixels
        cachedFocalLengthPixels = try {
            val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val camId = manager?.cameraIdList?.firstOrNull() ?: return uprightHeight * 1.2f
            val chars = manager.getCameraCharacteristics(camId)
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val focalMm = focalLengths?.firstOrNull() ?: 4.0f
            val sensorH = sensorSize?.height ?: 5.0f
            if (sensorH > 0f && focalMm > 0f) {
                (focalMm / sensorH) * uprightHeight
            } else {
                uprightHeight * 1.2f
            }
        } catch (_: Exception) {
            uprightHeight * 1.2f
        }
        return cachedFocalLengthPixels
    }

    /**
     * Runs object detection on a [Bitmap] frame using rotation options so results match the upright screen.
     *
     * @param bitmap Camera image bitmap.
     * @param rotationDegrees Clockwise rotation degrees from camera sensor (0, 90, 180, 270).
     * @return Filtered, upright [DetectedObstacle] candidates with calculated distanceMeters.
     */
    fun detect(bitmap: Bitmap, rotationDegrees: Int): List<DetectedObstacle> {
        val activeDetector = detector ?: return emptyList()
        val rawWidth = bitmap.width.toFloat()
        val rawHeight = bitmap.height.toFloat()
        if (rawWidth <= 0f || rawHeight <= 0f) return emptyList()

        // Upright dimensions after rotation
        val isRotatedSideways = (rotationDegrees == 90 || rotationDegrees == 270)
        val uprightWidth = if (isRotatedSideways) rawHeight else rawWidth
        val uprightHeight = if (isRotatedSideways) rawWidth else rawHeight
        val frameArea = uprightWidth * uprightHeight

        val focalLengthPixels = getFocalLengthPixels(uprightHeight)

        val startTime = System.currentTimeMillis()

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val processingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()

            val result = activeDetector.detect(mpImage, processingOptions)
            lastInferenceTimeMs = System.currentTimeMillis() - startTime

            result.detections().mapNotNull { detection ->
                val category = detection.categories().firstOrNull() ?: return@mapNotNull null
                val label = category.categoryName() ?: category.displayName() ?: return@mapNotNull null
                val score = category.score()
                val box = detection.boundingBox() // Box in upright coordinate space

                Log.d(
                    "NetraRaw",
                    "Raw detection: label=$label, score=${String.format("%.2f", score)}, box=[L:${box.left.toInt()}, T:${box.top.toInt()}, R:${box.right.toInt()}, B:${box.bottom.toInt()}]"
                )

                // Whitelist, score threshold, size, and edge filtering
                if (!DetectionFilter.isAccepted(label, score, box, uprightWidth, uprightHeight, sensitivity)) {
                    return@mapNotNull null
                }

                val centerX = box.centerX()
                val normalizedCenterX = (centerX / uprightWidth).coerceIn(0f, 1f)
                val direction = Direction.fromBoundingBoxCenter(centerX, uprightWidth)

                val boxArea = (box.width() * box.height()).coerceAtLeast(0f)
                val areaRatio = if (frameArea > 0f) (boxArea / frameArea).coerceIn(0f, 1f) else 0f
                val distance = distanceEstimator.estimateDistance(label, direction, areaRatio)

                val boxHeightPixels = box.height().coerceAtLeast(1f)
                val distanceMeters = distanceMetersEstimator.estimateDistance(label, boxHeightPixels, focalLengthPixels)

                val priority = AlertPriority.fromLabel(label)

                Log.d(
                    "NetraDetection",
                    "Accepted: label=$label, score=${String.format("%.2f", score)}, direction=$direction, distance=$distance, distanceMeters=$distanceMeters"
                )

                DetectedObstacle(
                    label = label,
                    confidence = score,
                    direction = direction,
                    distance = distance,
                    priority = priority,
                    isBorderline = false,
                    boundingBox = RectF(box),
                    normalizedCenterX = normalizedCenterX,
                    distanceMeters = distanceMeters,
                    timestampMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing detection on image frame", e)
            emptyList()
        }
    }

    fun close() {
        try {
            detector?.close()
            detector = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ObjectDetector", e)
        }
    }

    companion object {
        private const val TAG = "ObjectDetectorHelper"
        const val DEFAULT_MODEL_NAME = "efficientdet_lite2_float32.tflite"
        const val INT8_MODEL_NAME = "efficientdet_lite2.tflite"
        // Base low threshold passed to MediaPipe to allow sensitivity tuning in Kotlin
        const val BASE_DETECTOR_SCORE_THRESHOLD = 0.35f
    }
}
