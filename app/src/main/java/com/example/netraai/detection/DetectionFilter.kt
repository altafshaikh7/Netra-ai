package com.example.netraai.detection

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
 * Enforces label whitelisting, vehicle vs general object score thresholds,
 * sensitivity adjustments, and borderline confidence flagging.
 */
object DetectionFilter {

    const val VEHICLE_SCORE_THRESHOLD_NORMAL = 0.55f
    const val OTHER_SCORE_THRESHOLD_NORMAL = 0.60f

    const val BORDERLINE_MARGIN = 0.12f
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
        val isVehicle = AlertPriority.fromLabel(label) == AlertPriority.PRIORITY_1
        val baseThreshold = if (isVehicle) VEHICLE_SCORE_THRESHOLD_NORMAL else OTHER_SCORE_THRESHOLD_NORMAL

        return when (sensitivity) {
            DetectionSensitivity.LOW -> baseThreshold + 0.10f
            DetectionSensitivity.NORMAL -> baseThreshold
            DetectionSensitivity.HIGH -> (baseThreshold - 0.10f).coerceAtLeast(0.35f)
        }
    }

    /**
     * Determines whether a detection passes whitelist and confidence threshold checks.
     */
    fun isAccepted(
        label: String,
        score: Float,
        sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL
    ): Boolean {
        if (!isWhitelisted(label)) return false
        val threshold = getThreshold(label, sensitivity)
        return score >= threshold
    }

    /**
     * Flags whether an accepted detection is borderline in confidence (deserves "maybe" phrasing).
     */
    fun isBorderline(
        label: String,
        score: Float,
        sensitivity: DetectionSensitivity = DetectionSensitivity.NORMAL
    ): Boolean {
        val threshold = getThreshold(label, sensitivity)
        return score >= threshold && score < (threshold + BORDERLINE_MARGIN)
    }
}
