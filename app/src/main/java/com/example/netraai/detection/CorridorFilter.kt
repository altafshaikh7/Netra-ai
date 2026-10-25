package com.example.netraai.detection

object CorridorFilter {
    const val MAX_DISTANCE_METERS = 4.0f

    fun isInCorridor(normalizedCenterX: Float, corridorLeft: Float = 0.15f, corridorRight: Float = 0.85f): Boolean {
        return normalizedCenterX in corridorLeft..corridorRight
    }

    fun isValidForAlert(obstacle: DetectedObstacle, corridorLeft: Float = 0.15f, corridorRight: Float = 0.85f): Boolean {
        if (!isInCorridor(obstacle.normalizedCenterX, corridorLeft, corridorRight)) return false

        val meters = obstacle.distanceMeters
        if (meters != null) {
            return meters <= MAX_DISTANCE_METERS
        }

        return obstacle.distance == Distance.VERY_CLOSE || obstacle.distance == Distance.NEAR
    }
}
