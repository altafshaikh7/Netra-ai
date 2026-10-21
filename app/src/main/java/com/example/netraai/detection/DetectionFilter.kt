package com.example.netraai.detection

import android.graphics.RectF

/**
 * User-configurable detection sensitivity levels.
 */
enum class DetectionSensitivity {
    LOW,
    NORMAL,
    HIGH;

    companion object {
        fun fromString(value: String): DetectionSensitivity {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NORMAL
        }
    }
}

/**
 * Pure Kotlin filtering logic for obstacle detection.
 * Enforces label whitelisting, strict score thresholds, box area gating, and extreme edge filtering.
 * Policy: PRECISION OVER RECALL. When unsure, stay silent.
 */
object DetectionFilter {

    const val PERSON_SCORE_THRESHOLD_NORMAL = 0.80f
    const val VEHICLE_SCORE_THRESHOLD_NORMAL = 0.75f
    const val OTHER_SCORE_THRESHOLD_NORMAL = 0.80f

    const val MIN_BOX_AREA_RATIO = 0.02f // Ignore boxes < 2% of frame area
    const val EDGE_MARGIN_RATIO = 0.02f // 2% margin from upright frame edges
    const val HIGH_CONFIDENCE_EDGE_THRESHOLD = 0.85f

    const val MAX_RESULTS = 5

    // Strict whitelist of obstacles relevant for walking safety
    val WHITELISTED_LABELS = setOf(
        "person",
        "bicycle",
        "car",
        "motorcycle",
        "bus",
        "truck",
        "dog",
        "cow",
        "horse",
        "chair",
        "bench",
        "couch",
        "dining table",
        "potted plant",
        "traffic light",
        "stop sign"
    )

    fun isWhitelisted(label: String): Boolean {
        return label.trim().lowercase() in WHITELISTED_LABELS
    }

    /**
     * Resolves the minimum acceptance confidence threshold for a given object label and sensitivity.
     */
    fun getThreshold(label: String, sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL): Float {
        val cleanLabel = label.trim().lowercase()
        val baseThreshold = when {
            cleanLabel == "person" -> PERSON_SCORE_THRESHOLD_NORMAL
            AlertPriority.fromLabel(cleanLabel) == AlertPriority.PRIORITY_1 -> VEHICLE_SCORE_THRESHOLD_NORMAL
            else -> OTHER_SCORE_THRESHOLD_NORMAL
        }

        return when (sensitivity) {
            DetectionSensitivity.LOW -> (baseThreshold + 0.05f).coerceAtMost(0.95f)
            DetectionSensitivity.NORMAL -> baseThreshold
            DetectionSensitivity.HIGH -> (baseThreshold - 0.05f).coerceAtLeast(0.65f)
        }
    }

    /**
     * Checks if bounding box coordinates touch extreme frame edges.
     */
    fun isExtremeEdgeBox(left: Float, top: Float, right: Float, bottom: Float, uprightWidth: Float, uprightHeight: Float): Boolean {
        if (uprightWidth <= 0f || uprightHeight <= 0f) return false
        val marginX = uprightWidth * EDGE_MARGIN_RATIO
        val marginY = uprightHeight * EDGE_MARGIN_RATIO

        val touchesLeft = left <= marginX
        val touchesTop = top <= marginY
        val touchesRight = right >= (uprightWidth - marginX)
        val touchesBottom = bottom >= (uprightHeight - marginY)

        return touchesLeft || touchesTop || touchesRight || touchesBottom
    }

    fun isExtremeEdgeBox(box: RectF?, uprightWidth: Float, uprightHeight: Float): Boolean {
        if (box == null) return false
        return isExtremeEdgeBox(box.left, box.top, box.right, box.bottom, uprightWidth, uprightHeight)
    }

    /**
     * Determines whether a detection passes whitelist, confidence threshold, size, and edge checks.
     */
    fun isAccepted(
        label: String,
        score: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        uprightWidth: Float,
        uprightHeight: Float,
        sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL
    ): Boolean {
        if (!isWhitelisted(label)) return false

        val threshold = getThreshold(label, sensitivity)
        if (score < threshold) return false

        if (uprightWidth > 0f && uprightHeight > 0f) {
            val frameArea = uprightWidth * uprightHeight
            val boxWidth = (right - left).coerceAtLeast(0f)
            val boxHeight = (bottom - top).coerceAtLeast(0f)
            val boxArea = boxWidth * boxHeight
            val areaRatio = boxArea / frameArea

            if (areaRatio < MIN_BOX_AREA_RATIO) return false

            val marginX = uprightWidth * EDGE_MARGIN_RATIO
            val marginY = uprightHeight * EDGE_MARGIN_RATIO
            val touchesLeft = left <= marginX
            val touchesTop = top <= marginY
            val touchesRight = right >= (uprightWidth - marginX)
            val touchesBottom = bottom >= (uprightHeight - marginY)

            val touchesLeftOrRight = touchesLeft || touchesRight
            val touchesTopOrBottom = touchesTop || touchesBottom

            // Left/Right edge boxes require score >= 0.85
            if (touchesLeftOrRight && score < HIGH_CONFIDENCE_EDGE_THRESHOLD) {
                return false
            }

            val cleanLabel = label.trim().lowercase()
            val isPersonOrVehicle = cleanLabel == "person" || AlertPriority.fromLabel(cleanLabel) == AlertPriority.PRIORITY_1

            // Top/Bottom edge boxes: person or vehicle accepted if score >= 0.80, others require >= 0.85
            if (touchesTopOrBottom) {
                if (isPersonOrVehicle) {
                    if (score < 0.80f) return false
                } else {
                    if (score < HIGH_CONFIDENCE_EDGE_THRESHOLD) return false
                }
            }
        }

        return true
    }

    fun isAccepted(
        label: String,
        score: Float,
        box: RectF? = null,
        uprightWidth: Float = 0f,
        uprightHeight: Float = 0f,
        sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL
    ): Boolean {
        if (box == null) {
            return isAccepted(label, score, 0f, 0f, 0f, 0f, uprightWidth, uprightHeight, sensitivity)
        }
        return isAccepted(label, score, box.left, box.top, box.right, box.bottom, uprightWidth, uprightHeight, sensitivity)
    }

    /**
     * Borderline guessing is removed per PRECISION OVER RECALL policy.
     */
    fun isBorderline(
        @Suppress("UNUSED_PARAMETER") label: String,
        @Suppress("UNUSED_PARAMETER") score: Float,
        @Suppress("UNUSED_PARAMETER") sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL
    ): Boolean {
        return false
    }
}
