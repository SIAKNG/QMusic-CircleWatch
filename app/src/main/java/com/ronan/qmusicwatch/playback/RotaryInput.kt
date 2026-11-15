package com.ronan.qmusicwatch.playback

import kotlin.math.abs

/**
 * Chooses the useful component of a rotary event.
 *
 * Most crowns report their movement as [vertical], but a few watch launchers
 * rotate the input device and expose it as [horizontal].  Prefer the axis with
 * the larger absolute movement so a zero vertical component does not silently
 * discard a real crown turn.
 */
internal fun rotaryScrollDelta(vertical: Float, horizontal: Float): Float = when {
    !vertical.isFinite() && !horizontal.isFinite() -> 0f
    abs(vertical) >= abs(horizontal) && vertical.isFinite() -> vertical
    horizontal.isFinite() -> horizontal
    else -> 0f
}

/**
 * Maps crown movement to the volume step for the watch's reversed direction.
 */
internal fun rotaryVolumeDirection(delta: Float): Int? = when {
    !delta.isFinite() || delta == 0f -> null
    delta > 0f -> 1
    else -> -1
}

/** Volume changes in detents, so a brief opposite pulse must not reverse a burst. */
internal class CrownVolumeSteps {
    private var direction = 0
    private var lastInputAt = 0L
    private var lastStepAt: Long? = null
    private var reverseStartedAt: Long? = null

    fun accept(next: Int, now: Long): Boolean {
        if (next != -1 && next != 1) return false
        if (next != direction && direction != 0 && now - lastInputAt < 180L) {
            val started = reverseStartedAt
            if (started == null) {
                reverseStartedAt = now
                return false
            }
            if (now - started < 35L) return false
        }
        direction = next
        lastInputAt = now
        reverseStartedAt = null
        if (lastStepAt?.let { now - it < 110L } == true) return false
        lastStepAt = now
        return true
    }
}

/**
 * Some Android watch firmwares expose the crown as regular media volume
 * keys instead of a Compose rotary event. Keep this translation in one place
 * so Activity and Compose input use the same direction semantics.
 */
internal fun hardwareVolumeDirection(keyCode: Int): Int? = when (keyCode) {
    android.view.KeyEvent.KEYCODE_VOLUME_UP -> 1
    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> -1
    else -> null
}
