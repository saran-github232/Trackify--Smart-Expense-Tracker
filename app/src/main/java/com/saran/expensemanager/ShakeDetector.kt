package com.saran.expensemanager

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects a deliberate phone shake from the accelerometer. Requires [REQUIRED_JOLTS] separate
 * over-threshold jolts inside a short rolling window (not just one spike) so a single bump —
 * picking the phone up, setting it on a table — doesn't fire a false positive.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Whether this device even has the sensor the feature depends on. */
    val isAvailable: Boolean get() = accelerometer != null

    /** g-force a jolt must exceed to count; lower = more sensitive. Set from [ShakePrefs.thresholdG]. */
    var thresholdG: Float = SENSITIVITY_MEDIUM

    private var jolts = 0
    private var windowStart = 0L
    private var lastShakeAt = 0L

    fun start() {
        accelerometer?.let {
            // Unregister first so a repeat start() (e.g. sensitivity changed while already running)
            // updates in place instead of stacking a second registration for the same listener.
            sensorManager.unregisterListener(this, it)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        jolts = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce < thresholdG) return

        val now = System.currentTimeMillis()
        if (now - lastShakeAt < COOLDOWN_MS) return

        if (now - windowStart > SHAKE_WINDOW_MS) {
            windowStart = now
            jolts = 0
        }
        jolts++
        if (jolts >= REQUIRED_JOLTS) {
            jolts = 0
            lastShakeAt = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val SENSITIVITY_HIGH: Float = 1.8f
        const val SENSITIVITY_MEDIUM: Float = 2.5f
        const val SENSITIVITY_LOW: Float = 3.2f

        private const val REQUIRED_JOLTS = 3
        private const val SHAKE_WINDOW_MS = 1000L
        private const val COOLDOWN_MS = 2000L
    }
}
