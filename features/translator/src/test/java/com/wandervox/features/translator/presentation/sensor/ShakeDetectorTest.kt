package com.wandervox.features.translator.presentation.sensor

import android.hardware.SensorManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    private val detector = ShakeDetector()

    // 3 g along one axis: comfortably above the 2.5 g threshold.
    private fun ShakeDetector.jolt(atMillis: Long) = onSample(3 * SensorManager.GRAVITY_EARTH, 0f, 0f, atMillis)

    private fun ShakeDetector.rest(atMillis: Long) = onSample(0f, 0f, SensorManager.GRAVITY_EARTH, atMillis)

    @Test
    fun `device at rest never triggers`() {
        val triggered = (0L..5_000L step 60).any { detector.rest(it) }
        assertFalse(triggered)
    }

    @Test
    fun `a single bump is not a shake`() {
        assertFalse(detector.jolt(1_000))
        assertFalse(detector.rest(1_100))
    }

    @Test
    fun `two peaks within the window trigger a shake`() {
        assertFalse(detector.jolt(1_000))
        assertTrue(detector.jolt(1_250))
    }

    @Test
    fun `samples from the same jolt count as one peak`() {
        assertFalse(detector.jolt(1_000))
        assertFalse(detector.jolt(1_040))
        assertFalse(detector.jolt(1_080))
    }

    @Test
    fun `peaks too far apart do not trigger`() {
        assertFalse(detector.jolt(1_000))
        assertFalse(detector.jolt(1_000 + ShakeDetector.DEFAULT_PEAK_WINDOW_MILLIS + 1))
    }

    @Test
    fun `shakes during the cooldown are debounced`() {
        detector.jolt(1_000)
        assertTrue(detector.jolt(1_200))

        assertFalse(detector.jolt(1_500))
        assertFalse(detector.jolt(1_700))

        val afterCooldown = 1_200 + ShakeDetector.DEFAULT_COOLDOWN_MILLIS
        assertFalse(detector.jolt(afterCooldown + 100))
        assertTrue(detector.jolt(afterCooldown + 300))
    }
}
