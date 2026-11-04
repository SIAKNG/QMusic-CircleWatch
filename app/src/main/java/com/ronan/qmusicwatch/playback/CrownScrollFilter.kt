package com.ronan.qmusicwatch.playback

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.exp

/** Time-based, bounded crown motion. Touch cancellation and reversal also stop the coast. */
internal class CrownScrollFilter(private val density: Float = 1f) {
    private var pending = 0f
    private var direction = 0f
    private var opposing = 0f
    private var lastAcceptedAt = Long.MIN_VALUE
    private var lastFrameAt = Long.MIN_VALUE
    private var velocity = 0f
    private var frameSpeed = 1000f * density
    private var inputAgeMs = 0L
    val hasPending: Boolean get() = abs(pending) > .01f || abs(velocity) > 8f * density

    fun offer(pixels: Float, timeMs: Long): Boolean {
        if (!pixels.isFinite()) return false
        var delta = (pixels * .35f).coerceIn(-48f * density, 48f * density)
        if (abs(delta) < .25f * density) return false
        if (lastAcceptedAt != Long.MIN_VALUE && timeMs - lastAcceptedAt > 220L) clear()
        val firstInput = lastAcceptedAt == Long.MIN_VALUE
        val elapsed = if (firstInput) 32L else (timeMs - lastAcceptedAt).coerceIn(4L, 160L)
        val nextDirection = sign(delta)
        if (direction != 0f && nextDirection != direction) {
            opposing += delta
            if (timeMs - lastAcceptedAt < 80L && abs(opposing) < 2.5f * density) return false
            // A deliberate reversal replaces old pending movement immediately.
            delta = opposing
            pending = 0f
            velocity = 0f
        }
        opposing = 0f
        direction = nextDirection
        lastAcceptedAt = timeMs
        inputAgeMs = 0L
        val rate = abs(delta) / density * 1000f / elapsed
        val gain = 1f + ((rate - 80f) / 700f).coerceIn(0f, 1.8f)
        // A single detent has no measured cadence yet. Move it promptly, but
        // estimate its coast conservatively until another event establishes speed.
        val velocityInterval = if (firstInput) 64L else elapsed
        val instantaneous = (delta * gain * 1000f / velocityInterval).coerceIn(-1600f * density, 1600f * density)
        velocity = if (velocity == 0f) instantaneous else velocity * .2f + instantaneous * .8f
        frameSpeed = (abs(velocity) * 2.4f).coerceIn(1000f * density, 4800f * density)
        pending = (pending + delta * gain).coerceIn(-72f * density, 72f * density)
        return true
    }

    /** Apply the first detent before waiting for vsync, within the scroll mutation. */
    fun takeImmediate(): Float {
        lastFrameAt = Long.MIN_VALUE
        val step = pending.coerceIn(-32f * density, 32f * density)
        pending -= step
        return step
    }

    fun takeFrame(nowMs: Long = if (lastFrameAt == Long.MIN_VALUE) lastAcceptedAt else lastFrameAt + 16L): Float {
        val elapsed = if (lastFrameAt == Long.MIN_VALUE) 16L else (nowMs - lastFrameAt).coerceIn(1L, 40L)
        lastFrameAt = nowMs
        inputAgeMs += elapsed
        val dt = elapsed / 1000f
        // Consume the direct input within about two frames, rather than letting
        // rapid detents queue up behind the velocity animation.
        val limit = maxOf(frameSpeed, abs(pending) / .024f).coerceAtMost(4800f * density) * dt
        val step = pending.coerceIn(-limit, limit)
        pending -= step
        if (inputAgeMs < 40L) return step
        // Integrate exponential decay so the travelled distance is independent of refresh rate.
        val decay = exp(-dt / .065f)
        val coast = velocity * .065f * (1f - decay)
        velocity *= decay
        if (abs(velocity) < 8f * density || inputAgeMs > 240L) velocity = 0f
        return (step + coast).coerceIn(-limit, limit)
    }

    fun discardPending() = clear()
    fun clear() {
        pending = 0f; direction = 0f; opposing = 0f; velocity = 0f
        lastAcceptedAt = Long.MIN_VALUE; lastFrameAt = Long.MIN_VALUE
        inputAgeMs = 0L
    }
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
