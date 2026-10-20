package com.example.netraai.detection

/**
 * Pure data model representing a detected obstacle in the camera frame.
 *
 * @property label The detected object label (e.g. "car", "person", "chair").
 * @property confidence Confidence score in range [0.0, 1.0].
 * @property direction Horizontal direction in user frame (LEFT, AHEAD, RIGHT).
 * @property distance Rough distance estimate (VERY_CLOSE, NEAR, FAR).
 * @property priority Hazard alert priority level (PRIORITY_1, PRIORITY_2, PRIORITY_3).
 * @property timestampMs Timestamp in milliseconds when the object was detected.
 */
data class DetectedObstacle(
    val label: String,
    val confidence: Float,
    val direction: Direction,
    val distance: Distance,
    val priority: AlertPriority,
    val timestampMs: Long = System.currentTimeMillis()
)
