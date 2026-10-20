package com.example.netraai.detection

/**
 * Represents rough distance estimation derived from the area ratio of the detected bounding box
 * relative to the camera frame area.
 *
 * Distance urgency ordering: FAR < NEAR < VERY_CLOSE.
 */
enum class Distance(val urgencyLevel: Int) {
    FAR(1),
    NEAR(2),
    VERY_CLOSE(3);

    /**
     * Returns true if this distance is closer (more urgent) than the given [other] distance.
     */
    fun isCloserThan(other: Distance): Boolean {
        return this.urgencyLevel > other.urgencyLevel
    }

    companion object {
        const val DEFAULT_VERY_CLOSE_RATIO = 0.20f
        const val DEFAULT_NEAR_RATIO = 0.05f

        /**
         * Determines rough distance based on box area ratio:
         * - VERY_CLOSE: ratio >= 0.20 (object fills 20%+ of frame)
         * - NEAR: 0.05 <= ratio < 0.20 (object fills 5% to 20% of frame)
         * - FAR: ratio < 0.05 (object fills less than 5% of frame)
         *
         * @param boxArea Area of the bounding box (width * height).
         * @param frameArea Area of the total frame (frameWidth * frameHeight).
         */
        fun fromAreaRatio(
            boxArea: Float,
            frameArea: Float,
            veryCloseRatio: Float = DEFAULT_VERY_CLOSE_RATIO,
            nearRatio: Float = DEFAULT_NEAR_RATIO
        ): Distance {
            require(frameArea > 0f) { "frameArea must be positive, was $frameArea" }
            val ratio = (boxArea / frameArea).coerceIn(0f, 1f)
            return when {
                ratio >= veryCloseRatio -> VERY_CLOSE
                ratio >= nearRatio -> NEAR
                else -> FAR
            }
        }

        /**
         * Convenience method calculating distance directly from width and height dimensions.
         */
        fun fromDimensions(
            boxWidth: Float,
            boxHeight: Float,
            frameWidth: Float,
            frameHeight: Float,
            veryCloseRatio: Float = DEFAULT_VERY_CLOSE_RATIO,
            nearRatio: Float = DEFAULT_NEAR_RATIO
        ): Distance {
            val boxArea = (boxWidth * boxHeight).coerceAtLeast(0f)
            val frameArea = frameWidth * frameHeight
            return fromAreaRatio(boxArea, frameArea, veryCloseRatio, nearRatio)
        }
    }
}
