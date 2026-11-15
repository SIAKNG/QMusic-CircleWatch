package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.playback.DeviceVolumeState
import com.ronan.qmusicwatch.playback.CrownVolumeSteps
import com.ronan.qmusicwatch.playback.hardwareVolumeDirection
import com.ronan.qmusicwatch.playback.rotaryScrollDelta
import com.ronan.qmusicwatch.playback.rotaryVolumeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotaryInputTest {
    @Test fun prefersTheAxisThatContainsTheCrownMovement() {
        assertEquals(-4f, rotaryScrollDelta(-4f, 1f))
        assertEquals(3f, rotaryScrollDelta(0f, 3f))
        assertEquals(0f, rotaryScrollDelta(Float.NaN, Float.NaN))
    }

    @Test fun mapsRotaryDirectionToVolumeSteps() {
        assertEquals(-1, rotaryVolumeDirection(-0.1f))
        assertEquals(1, rotaryVolumeDirection(0.1f))
        assertNull(rotaryVolumeDirection(0f))
        assertNull(rotaryVolumeDirection(Float.POSITIVE_INFINITY))
    }

    @Test fun mapsHardwareVolumeKeysToVolumeSteps() {
        assertEquals(1, hardwareVolumeDirection(android.view.KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(-1, hardwareVolumeDirection(android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(hardwareVolumeDirection(android.view.KeyEvent.KEYCODE_VOLUME_MUTE))
    }

    @Test fun singleOppositePulseDuringContinuousTurningCannotReverseVolume() {
        for (direction in listOf(1, -1)) {
            val steps = CrownVolumeSteps()
            assertTrue(steps.accept(direction, 0))
            assertFalse(steps.accept(-direction, 120))
            assertTrue(steps.accept(direction, 140))
            assertFalse(steps.accept(-direction, 255))
            assertTrue(steps.accept(direction, 270))
        }
    }

    @Test fun permitsDeliberateReversalAndDoesNotCountDuplicateInputAsAnotherStep() {
        val steps = CrownVolumeSteps()
        assertTrue(steps.accept(1, 0))
        assertFalse(steps.accept(1, 20))
        assertFalse(steps.accept(-1, 80))
        assertFalse(steps.accept(-1, 85))
        assertTrue(steps.accept(-1, 120))
        assertTrue(steps.accept(1, 350))
    }

    @Test fun volumeStateReportsAClampedPercentage() {
        assertEquals(50, DeviceVolumeState(5, 10).percent)
        assertEquals(0, DeviceVolumeState(-3, 10).percent)
        assertEquals(100, DeviceVolumeState(20, 10).percent)
        assertNull(DeviceVolumeState(1, 0).percent)
    }
}
