package com.example.netraai.detection

/**
 * Represents the horizontal direction of an obstacle relative to the user's field of view.
 * The frame width is divided into three equal horizontal zones: Left, Ahead (center), and Right.
 */
enum class Direction {
    LEFT,
    AHEAD,
    RIGHT;

    companion object {
        /**
         * Determines the horizontal direction based on the center X coordinate of an object's bounding box.
         *
         * @param centerX Horizontal center coordinate of the bounding box.
         * @param frameWidth Total width of the reference frame in the same coordinate unit.
         * @return [Direction.LEFT], [Direction.AHEAD], or [Direction.RIGHT].
         */
        fun fromBoundingBoxCenter(centerX: Float, frameWidth: Float): Direction {
            require(frameWidth > 0f) { "frameWidth must be positive, was $frameWidth" }
            val oneThird = frameWidth / 3f
            val twoThirds = 2f * oneThird
            return when {
                centerX < oneThird -> LEFT
                centerX <= twoThirds -> AHEAD
                else -> RIGHT
            }
        }

        /**
         * Determines horizontal direction from normalized center X coordinate (0.0 to 1.0).
         */
        fun fromNormalizedCenterX(normalizedCenterX: Float): Direction {
            return fromBoundingBoxCenter(normalizedCenterX, 1f)
        }
    }
}
