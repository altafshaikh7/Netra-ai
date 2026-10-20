package com.example.netraai.detection

/**
 * Priority assigned to detected obstacles based on safety hazard level.
 *
 * Priority 1: High speed / heavy moving hazards (vehicles) -> interrupts current speech (QUEUE_FLUSH)
 * Priority 2: Living beings (people, animals) -> queued speech (QUEUE_ADD)
 * Priority 3: Static or other obstacles (chairs, benches, etc.) -> spoken only when TTS is idle
 */
enum class AlertPriority(val level: Int) {
    PRIORITY_1(1),
    PRIORITY_2(2),
    PRIORITY_3(3);

    companion object {
        // Priority 1: Vehicles
        val VEHICLE_LABELS = setOf(
            "car",
            "bus",
            "truck",
            "motorcycle",
            "bicycle"
        )

        // Priority 2: Living beings
        val LIVING_BEING_LABELS = setOf(
            "person",
            "dog",
            "cow",
            "horse"
        )

        /**
         * Resolves the alert priority for a detected COCO class label.
         *
         * @param label COCO class label (case-insensitive).
         * @return [AlertPriority.PRIORITY_1] for vehicles,
         *         [AlertPriority.PRIORITY_2] for person/dog/cow/horse,
         *         [AlertPriority.PRIORITY_3] for all other obstacles.
         */
        fun fromLabel(label: String): AlertPriority {
            val normalized = label.trim().lowercase()
            return when {
                normalized in VEHICLE_LABELS -> PRIORITY_1
                normalized in LIVING_BEING_LABELS -> PRIORITY_2
                else -> PRIORITY_3
            }
        }
    }
}
