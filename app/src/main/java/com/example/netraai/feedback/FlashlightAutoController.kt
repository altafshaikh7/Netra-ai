package com.example.netraai.feedback

/**
 * Flashlight operation modes supported by Netra AI.
 */
enum class FlashlightMode {
    AUTO,
    ON,
    OFF;

    companion object {
        fun fromString(value: String): FlashlightMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
        }
    }
}

/**
 * Controller for smart automatic flashlight activation based on camera frame brightness.
 * Implements hysteresis and a mandatory minimum hold duration to prevent rapid strobing/flickering.
 *
 * @param lowThreshold Luma below which torch should turn ON (default 45.0f).
 * @param highThreshold Luma above which torch should turn OFF (default 75.0f).
 * @param minHoldDurationMs Minimum duration in milliseconds the torch state must be held (default 5000ms).
 */
class FlashlightAutoController(
    val lowThreshold: Float = DEFAULT_LOW_THRESHOLD,
    val highThreshold: Float = DEFAULT_HIGH_THRESHOLD,
    val minHoldDurationMs: Long = DEFAULT_MIN_HOLD_DURATION_MS
) {
    var isTorchActive: Boolean = false
        private set

    var lastStateChangeTimestampMs: Long = 0L
        private set

    /**
     * Evaluates the current sampled luma (brightness 0..255).
     *
     * @param averageLuma Average luma of the scene.
     * @param currentTimeMs Current timestamp in ms.
     * @return `true` if torch turned ON, `false` if torch turned OFF, or `null` if state did not change.
     */
    @Synchronized
    fun evaluate(averageLuma: Float, currentTimeMs: Long = System.currentTimeMillis()): Boolean? {
        val elapsed = currentTimeMs - lastStateChangeTimestampMs

        if (!isTorchActive) {
            // Torch is currently OFF. Check if light dropped below low threshold and hold period satisfied
            if (averageLuma < lowThreshold && (lastStateChangeTimestampMs == 0L || elapsed >= minHoldDurationMs)) {
                isTorchActive = true
                lastStateChangeTimestampMs = currentTimeMs
                return true
            }
        } else {
            // Torch is currently ON. Check if light exceeded high threshold and hold period satisfied
            if (averageLuma > highThreshold && elapsed >= minHoldDurationMs) {
                isTorchActive = false
                lastStateChangeTimestampMs = currentTimeMs
                return false
            }
        }
        return null
    }

    @Synchronized
    fun reset(torchState: Boolean = false) {
        isTorchActive = torchState
        lastStateChangeTimestampMs = 0L
    }

    companion object {
        const val DEFAULT_LOW_THRESHOLD = 45f
        const val DEFAULT_HIGH_THRESHOLD = 75f
        const val DEFAULT_MIN_HOLD_DURATION_MS = 5000L
    }
}
