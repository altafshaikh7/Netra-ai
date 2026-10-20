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
     * Checks if a bounding box touches extreme frame edges.
     */
    fun isExtremeEdgeBox(box: RectF?, uprightWidth: Float, uprightHeight: Float): Boolean {
        if (box == null || uprightWidth <= 0f || uprightHeight <= 0f) return false
        val marginX = uprightWidth * EDGE_MARGIN_RATIO
        val marginY = uprightHeight * EDGE_MARGIN_RATIO

        val touchesLeft = box.left <= marginX
        val touchesTop = box.top <= marginY
        val touchesRight = box.right >= (uprightWidth - marginX)
        val touchesBottom = box.bottom >= (uprightHeight - marginY)

        return touchesLeft || touchesTop || touchesRight || touchesBottom
    }

    /**
     * Determines whether a detection passes whitelist, confidence threshold, size, and edge checks.
     */
    fun isAccepted(
        label: String,
        score: Float,
        box: RectF? = null,
        uprightWidth: Float = 0f,
        uprightHeight: Float = 0f,
        sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL
    ): Boolean {
        if (!isWhitelisted(label)) return false

        val threshold = getThreshold(label, sensitivity)
        if (score < threshold) return false

        if (box != null && uprightWidth > 0f && uprightHeight > 0f) {
            val frameArea = uprightWidth * uprightHeight
            val boxArea = (box.width() * box.height()).coerceAtLeast(0f)
            val areaRatio = boxArea / frameArea

            // Ignore tiny boxes under 2% area
            if (areaRatio < MIN_BOX_AREA_RATIO) return false

            // Ignore extreme edge boxes unless score is very high (>= 0.85)
            if (isExtremeEdgeBox(box, uprightWidth, uprightHeight) && score < HIGH_CONFIDENCE_EDGE_THRESHOLD) {
                return false
            }
        }

        return true
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
