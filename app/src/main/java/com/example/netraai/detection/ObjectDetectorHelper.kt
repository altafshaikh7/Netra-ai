package com.example.netraai.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * Helper class that wraps MediaPipe Tasks Vision ObjectDetector with the bundled
 * EfficientDet-Lite0 model.
 *
 * @param context Android context to access assets.
 * @param modelAssetName Path in assets to the model file (default: "efficientdet_lite0.tflite").
 * @param scoreThreshold Minimum confidence score for detected objects (default: 0.35f).
 * @param maxResults Maximum number of detection results returned per frame.
 */
class ObjectDetectorHelper(
    context: Context,
    modelAssetName: String = DEFAULT_MODEL_NAME,
    scoreThreshold: Float = DEFAULT_SCORE_THRESHOLD,
    maxResults: Int = DEFAULT_MAX_RESULTS
) {
    private var detector: ObjectDetector? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelAssetName)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setScoreThreshold(scoreThreshold)
                .setMaxResults(maxResults)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            detector = ObjectDetector.createFromOptions(context.applicationContext, options)
            Log.d(TAG, "MediaPipe ObjectDetector initialized successfully with $modelAssetName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe ObjectDetector", e)
        }
    }

    /**
     * Runs object detection on a [Bitmap] image.
     *
     * @param bitmap The frame image to analyze.
     * @return List of [DetectedObstacle] objects detected in the frame.
     */
    fun detect(bitmap: Bitmap): List<DetectedObstacle> {
        val activeDetector = detector ?: return emptyList()
        val frameWidth = bitmap.width.toFloat()
        val frameHeight = bitmap.height.toFloat()
        if (frameWidth <= 0f || frameHeight <= 0f) return emptyList()

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = activeDetector.detect(mpImage)

            result.detections().mapNotNull { detection ->
                val category = detection.categories().firstOrNull() ?: return@mapNotNull null
                val label = category.categoryName() ?: category.displayName() ?: return@mapNotNull null
                val box = detection.boundingBox()

                val direction = Direction.fromBoundingBoxCenter(
                    centerX = box.centerX(),
                    frameWidth = frameWidth
                )

                val distance = Distance.fromDimensions(
                    boxWidth = box.width(),
                    boxHeight = box.height(),
                    frameWidth = frameWidth,
                    frameHeight = frameHeight
                )

                val priority = AlertPriority.fromLabel(label)

                DetectedObstacle(
                    label = label,
                    confidence = category.score(),
                    direction = direction,
                    distance = distance,
                    priority = priority,
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
        const val DEFAULT_MODEL_NAME = "efficientdet_lite0.tflite"
        const val DEFAULT_SCORE_THRESHOLD = 0.35f
        const val DEFAULT_MAX_RESULTS = 6
    }
}
