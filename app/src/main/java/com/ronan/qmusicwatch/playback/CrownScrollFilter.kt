package com.ronan.qmusicwatch.playback

import kotlin.math.abs
import kotlin.math.sign

/** Bounded motion, not a queue of per-event scroll animations. Values are screen pixels. */
internal class CrownScrollFilter(private val density: Float = 1f) {
    private var pending = 0f
    private var direction = 0f
    private var opposing = 0f
    private var lastAcceptedAt = Long.MIN_VALUE
    val hasPending: Boolean get() = abs(pending) > .01f

    fun offer(pixels: Float, timeMs: Long): Boolean {
        if (!pixels.isFinite()) return false
        var delta = (pixels * .35f).coerceIn(-48f * density, 48f * density)
        if (abs(delta) < .25f * density) return false
        if (lastAcceptedAt != Long.MIN_VALUE && timeMs - lastAcceptedAt > 180L) clear()
        val nextDirection = sign(delta)
        if (direction != 0f && nextDirection != direction) {
            opposing += delta
            if (timeMs - lastAcceptedAt < 80L && abs(opposing) < 2.5f * density) return false
            // A deliberate reversal replaces old pending movement immediately.
            delta = opposing
            pending = 0f
        }
        opposing = 0f
        direction = nextDirection
        lastAcceptedAt = timeMs
        pending = (pending + delta).coerceIn(-48f * density, 48f * density)
        return true
    }

    fun takeFrame(): Float {
        val step = pending.coerceIn(-10f * density, 10f * density)
        pending -= step
        return step
    }

    fun discardPending() { pending = 0f }
    fun clear() { pending = 0f; direction = 0f; opposing = 0f; lastAcceptedAt = Long.MIN_VALUE }
}

internal class CrownHapticPulse(private val distancePx: Float) {
    private var distance = 0f
    private var lastTick = Long.MIN_VALUE
    fun moved(pixels: Float, nowMs: Long): Boolean {
        if (!pixels.isFinite()) return false
        distance += abs(pixels)
        if (distance < distancePx || (lastTick != Long.MIN_VALUE && nowMs - lastTick < 70L)) return false
        distance %= distancePx
        lastTick = nowMs
        return true
    }
}
