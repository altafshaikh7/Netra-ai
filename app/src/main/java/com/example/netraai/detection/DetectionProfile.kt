package com.example.netraai.detection

enum class DetectionProfile {
    STRICT,
    BALANCED,
    SENSITIVE;

    companion object {
        fun fromString(value: String): DetectionProfile {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BALANCED
        }
    }
}

data class DetectionProfileValues(
    val personThreshold: Float,
    val vehicleThreshold: Float,
    val otherThreshold: Float,
    val trackerMinConfirmations: Int,
    val trackerWindowSize: Int,
    val blurThreshold: Float,
    val isBlurGateEnabled: Boolean,
    val corridorLeft: Float,
    val corridorRight: Float,
    val motionThresholdRadSec: Float
) {
    companion object {
        val STRICT = DetectionProfileValues(
            personThreshold = 0.80f,
            vehicleThreshold = 0.75f,
            otherThreshold = 0.80f,
            trackerMinConfirmations = 4,
            trackerWindowSize = 6,
            blurThreshold = 80.0f,
            isBlurGateEnabled = true,
            corridorLeft = 0.30f,
            corridorRight = 0.70f,
            motionThresholdRadSec = 0.8f
        )

        val BALANCED = DetectionProfileValues(
            personThreshold = 0.55f,
            vehicleThreshold = 0.50f,
            otherThreshold = 0.55f,
            trackerMinConfirmations = 3,
            trackerWindowSize = 5,
            blurThreshold = 40.0f,
            isBlurGateEnabled = true,
            corridorLeft = 0.15f,
            corridorRight = 0.85f,
            motionThresholdRadSec = 1.2f
        )

        val SENSITIVE = DetectionProfileValues(
            personThreshold = 0.45f,
            vehicleThreshold = 0.40f,
            otherThreshold = 0.45f,
            trackerMinConfirmations = 2,
            trackerWindowSize = 4,
            blurThreshold = 0.0f,
            isBlurGateEnabled = false,
            corridorLeft = 0.10f,
            corridorRight = 0.90f,
            motionThresholdRadSec = 2.0f
        )

        fun forProfile(profile: DetectionProfile): DetectionProfileValues {
            return when (profile) {
                DetectionProfile.STRICT -> STRICT
                DetectionProfile.BALANCED -> BALANCED
                DetectionProfile.SENSITIVE -> SENSITIVE
            }
        }
    }
}
