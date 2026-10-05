package com.wandervox.features.translator.presentation.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.sqrt

/**
 * Pure shake-recognition logic (no Android types), so it can be unit tested.
 *
 * A *deliberate* shake is [requiredPeaks] acceleration peaks above [thresholdG] within
 * [peakWindowMillis]. One stray bump or walking never triggers. After a shake is reported,
 * further shakes are ignored for [cooldownMillis] (debounce).
 */
class ShakeDetector(
    private val thresholdG: Float = DEFAULT_THRESHOLD_G,
    private val requiredPeaks: Int = DEFAULT_REQUIRED_PEAKS,
    private val peakWindowMillis: Long = DEFAULT_PEAK_WINDOW_MILLIS,
    private val minPeakSpacingMillis: Long = DEFAULT_MIN_PEAK_SPACING_MILLIS,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {
    private var peakCount = 0
    private var firstPeakAtMillis = 0L
    private var lastPeakAtMillis = 0L
    private var lastShakeAtMillis = Long.MIN_VALUE / 2

    /** Feeds one accelerometer sample (m/s²). Returns `true` exactly when a shake is recognized. */
    fun onSample(x: Float, y: Float, z: Float, timestampMillis: Long): Boolean {
        if (timestampMillis - lastShakeAtMillis < cooldownMillis) return false

        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gForce < thresholdG) return false
        // Consecutive samples from the same jolt count as one peak.
        if (peakCount > 0 && timestampMillis - lastPeakAtMillis < minPeakSpacingMillis) return false

        if (peakCount == 0 || timestampMillis - firstPeakAtMillis > peakWindowMillis) {
            peakCount = 0
            firstPeakAtMillis = timestampMillis
        }
        peakCount++
        lastPeakAtMillis = timestampMillis

        if (peakCount < requiredPeaks) return false
        peakCount = 0
        lastShakeAtMillis = timestampMillis
        return true
    }

    companion object {
        const val DEFAULT_THRESHOLD_G = 2.5f
        const val DEFAULT_REQUIRED_PEAKS = 2
        const val DEFAULT_PEAK_WINDOW_MILLIS = 600L
        const val DEFAULT_MIN_PEAK_SPACING_MILLIS = 100L
        const val DEFAULT_COOLDOWN_MILLIS = 1_500L
    }
}

/**
 * Calls [onShake] when the user shakes the device. The accelerometer listener is registered only
 * while the host is RESUMED, so the sensor is powered down whenever the screen is not visible.
 * Does nothing on devices without an accelerometer.
 */
@Composable
fun ShakeEffect(
    enabled: Boolean = true,
    onShake: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)
    val sensorManager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val accelerometer = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    LifecycleResumeEffect(sensorManager, accelerometer, enabled) {
        if (!enabled || sensorManager == null || accelerometer == null) {
            return@LifecycleResumeEffect onPauseOrDispose { }
        }
        val detector = ShakeDetector()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                if (detector.onSample(x, y, z, event.timestamp / NANOS_PER_MILLI)) currentOnShake()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        // SENSOR_DELAY_UI (~60 ms) is enough to catch shakes and costs far less power than GAME/FASTEST.
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sensorManager.unregisterListener(listener) }
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
