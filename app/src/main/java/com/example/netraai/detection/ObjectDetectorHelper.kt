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
import kotlin.math.abs

/**
 * Helper class wrapping MediaPipe Tasks Vision ObjectDetector with float32 EfficientDet-Lite2 model
 * (with INT8 model switchable via constant), GPU acceleration with CPU fallback, precise Camera2 focal length selection, and strict filtering.
 */
class ObjectDetectorHelper(
    context: Context,
    modelAssetName: String = DEFAULT_MODEL_NAME,
    var sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL,
    var calibrationMultiplier: Float = 1.0f,
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

    private fun getFocalLengthPixels(uprightWidth: Float, uprightHeight: Float): Float {
        if (cachedFocalLengthPixels > 0f) return cachedFocalLengthPixels
        cachedFocalLengthPixels = try {
            val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (manager != null) {
                var bestCameraId: String? = null
                var bestFocalDiff = Float.MAX_VALUE
                val standardMainCameraFocalMm = 4.2f // Typical main camera focal length

                for (id in manager.cameraIdList) {
                    val chars = manager.getCameraCharacteristics(id)
                    val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                        val focalMm = focalLengths?.firstOrNull() ?: continue
                        val diff = abs(focalMm - standardMainCameraFocalMm)
                        if (diff < bestFocalDiff) {
                            bestFocalDiff = diff
                            bestCameraId = id
                        }
                    }
                }

                val targetId = bestCameraId ?: manager.cameraIdList.firstOrNull()
                if (targetId != null) {
                    val chars = manager.getCameraCharacteristics(targetId)
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val focalMm = focalLengths?.firstOrNull() ?: 4.0f
                    val sensorWidth = sensorSize?.width ?: 6.4f
                    val sensorHeight = sensorSize?.height ?: 4.8f

                    DistanceMetersEstimator.computeFocalPixels(focalMm, sensorWidth, sensorHeight, uprightWidth, uprightHeight)
                } else {
                    uprightHeight * 1.2f
                }
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

        val focalLengthPixels = getFocalLengthPixels(uprightWidth, uprightHeight)
        val TRUNCATION_MARGIN = 0.02f

        // Call beginFrame ONCE per detect() before the detection loop
        distanceMetersEstimator.beginFrame()

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

                // Truncated boxes check (within 2% margin of top or bottom edge)
                val touchesTopOrBottom = box.top <= uprightHeight * TRUNCATION_MARGIN || box.bottom >= uprightHeight * (1f - TRUNCATION_MARGIN)

                val (distanceMeters, _) = if (touchesTopOrBottom) {
                    Pair(null, "")
                } else {
                    distanceMetersEstimator.estimateDistance(
                        label = label,
                        boxHeightPixels = boxHeightPixels,
                        focalLengthPixels = focalLengthPixels,
                        calibrationMultiplier = calibrationMultiplier,
                        centerX = box.centerX(),
                        centerY = box.centerY()
                    )
                }

                val finalDistance = if (touchesTopOrBottom && distance == Distance.FAR) Distance.NEAR else distance

                val realHeight = DistanceMetersEstimator.getRealHeight(label)
                val rawMeters = if (realHeight != null && boxHeightPixels > 0f) (realHeight * focalLengthPixels) / boxHeightPixels * calibrationMultiplier else 0f

                // Requirement 4: Logging at Log.d tag "NetraDist"
                Log.d(
                    "NetraDist",
                    "label=$label, boxHeight=$boxHeightPixels, focalPx=$focalLengthPixels, rawMeters=$rawMeters, smoothedMeters=$distanceMeters, truncated=$touchesTopOrBottom"
                )

                val priority = AlertPriority.fromLabel(label)

                Log.d(
                    "NetraDetection",
                    "Accepted: label=$label, score=${String.format("%.2f", score)}, direction=$direction, distance=$finalDistance, distanceMeters=$distanceMeters"
                )

                DetectedObstacle(
                    label = label,
                    confidence = score,
                    direction = direction,
                    distance = finalDistance,
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
