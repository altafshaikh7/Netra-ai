package com.example.netraai.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Gate status representing whether camera motion is steady enough for object detection.
 */
enum class GateStatus {
    ACTIVE,
    PAUSED_MOVING,
    PAUSED_BLURRY,
    PAUSED_SETTLING;

    val isPaused: Boolean get() = this != ACTIVE
}

/**
 * Tracks device angular speed via Gyroscope and enforces a settle time after motion stops.
 */
class MotionGate(
    val angularSpeedThreshold: Float = DEFAULT_MOTION_THRESHOLD,
    val settleTimeMs: Long = DEFAULT_SETTLE_TIME_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : SensorEventListener {

    var currentAngularSpeed: Float = 0f
        private set

    var isMovingFast: Boolean = false
        private set

    private var lastMotionEndTimeMs: Long = 0L

    fun onSensorChangedValues(wx: Float, wy: Float, wz: Float) {
        currentAngularSpeed = sqrt(wx * wx + wy * wy + wz * wz)
        val now = timeProvider()

        if (currentAngularSpeed > angularSpeedThreshold) {
            isMovingFast = true
            lastMotionEndTimeMs = now
        } else {
            if (isMovingFast) {
                // Motion just dropped below threshold
                isMovingFast = false
                lastMotionEndTimeMs = now
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val values = event.values ?: return
            if (values.size >= 3) {
                onSensorChangedValues(values[0], values[1], values[2])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun evaluateStatus(currentTimeMs: Long = timeProvider()): GateStatus {
        if (isMovingFast) {
            return GateStatus.PAUSED_MOVING
        }
        if (lastMotionEndTimeMs > 0L && currentTimeMs - lastMotionEndTimeMs < settleTimeMs) {
            return GateStatus.PAUSED_SETTLING
        }
        return GateStatus.ACTIVE
    }

    fun register(context: Context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro != null) {
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun unregister(context: Context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager?.unregisterListener(this)
    }

    fun reset() {
        currentAngularSpeed = 0f
        isMovingFast = false
        lastMotionEndTimeMs = 0L
    }

    companion object {
        const val DEFAULT_MOTION_THRESHOLD = 0.8f // rad/s
        const val DEFAULT_SETTLE_TIME_MS = 500L
    }
}
