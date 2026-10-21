package com.example.netraai.detection

object CorridorFilter {
    const val CORRIDOR_LEFT = 0.30f
    const val CORRIDOR_RIGHT = 0.70f
    const val MAX_DISTANCE_METERS = 4.0f

    fun isInCorridor(normalizedCenterX: Float): Boolean {
        return normalizedCenterX in CORRIDOR_LEFT..CORRIDOR_RIGHT
    }

    fun isValidForAlert(obstacle: DetectedObstacle): Boolean {
        if (!isInCorridor(obstacle.normalizedCenterX)) return false

        val meters = obstacle.distanceMeters
        if (meters != null) {
            return meters <= MAX_DISTANCE_METERS
        }

        return obstacle.distance == Distance.VERY_CLOSE || obstacle.distance == Distance.NEAR
    }
}
