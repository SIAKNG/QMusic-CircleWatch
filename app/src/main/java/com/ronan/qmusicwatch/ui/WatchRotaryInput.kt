package com.ronan.qmusicwatch.ui

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.heytap.wearable.support.util.HeyHapticFeedbackConstants
import com.ronan.qmusicwatch.data.AppLog
import com.ronan.qmusicwatch.playback.CrownHapticPulse
import com.ronan.qmusicwatch.playback.CrownScrollFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Uses the same effect 302 as OPPO's supplied RecyclerView, with a safe public-API fallback. */
internal class CrownHaptics(private val view: View) {
    internal val requests = Channel<Unit>(Channel.CONFLATED)
    private var lastTick = Long.MIN_VALUE
    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (!view.isHapticFeedbackEnabled || (lastTick != Long.MIN_VALUE && now - lastTick < 70L)) return
        lastTick = now
        requests.trySend(Unit)
    }

    internal suspend fun run() {
        val context = view.context.applicationContext
        var native = nativeTick(context)
        AppLog.write("CROWN", "haptics=${if (native != null) "OPPO-302" else "system"}")
        for (request in requests) {
            if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) continue
            val sent = native?.let { tick -> runCatching { tick(); true }.getOrElse {
                AppLog.write("CROWN", "native-unavailable ${it.javaClass.simpleName}")
                native = null
                false
            } } ?: false
            if (!sent) view.post {
                if (view.isAttachedToWindow && !view.performHapticFeedback(HeyHapticFeedbackConstants.GRANULAR_SHORT_VIBRATE)) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }

    private fun nativeTick(context: Context): (() -> Unit)? = runCatching {
        val motor = context.getSystemService("linearmotor") ?: return null
        val vibrate = motor.javaClass.methods.first { it.name == "vibrate" && it.parameterCount == 1 }
        val effectClass = vibrate.parameterTypes.single()
        val builderClass = Class.forName(effectClass.name + "\$Builder")
        val builder = builderClass.getConstructor().newInstance()
        builderClass.getMethod("setEffectType", Int::class.javaPrimitiveType).invoke(builder, 302)
        builderClass.getMethod("setEffectStrength", Int::class.javaPrimitiveType).invoke(builder, 2)
        builderClass.getMethod("setEffectLoop", Boolean::class.javaPrimitiveType).invoke(builder, false)
        val effect = builderClass.getMethod("build").invoke(builder)
        val tick: () -> Unit = { vibrate.invoke(motor, effect); Unit }
        tick
    }.getOrNull()
}

@Composable
internal fun rememberCrownHaptics(): CrownHaptics {
    val view = LocalView.current
    val haptics = remember(view) { CrownHaptics(view) }
    LaunchedEffect(haptics) { launch(Dispatchers.IO) { haptics.run() } }
    DisposableEffect(haptics) { onDispose { haptics.requests.close() } }
    return haptics
}

@Stable
internal class CrownScrollController(private val filter: CrownScrollFilter) {
    internal val requests = Channel<Unit>(Channel.CONFLATED)
    internal var enabled = true
    internal var onInteraction: () -> Unit = {}
    fun offer(pixels: Float, timeMs: Long): Boolean {
        if (!enabled) return false
        if (filter.offer(pixels, timeMs)) { onInteraction(); requests.trySend(Unit) }
        return true
    }
    internal fun hasPending() = filter.hasPending
    internal fun takeImmediate() = filter.takeImmediate()
    internal fun takeFrame(nowMs: Long) = filter.takeFrame(nowMs)
    internal fun clear() = filter.clear()
    internal fun discardPending() = filter.discardPending()
}

/** One user-input mutation per burst; new events are merged and touch input can cancel it. */
@Composable
internal fun rememberCrownScroll(
    state: ScrollableState,
    enabled: Boolean = true,
    onInteraction: () -> Unit = {},
): CrownScrollController {
    val density = LocalDensity.current.density
    val haptics = rememberCrownHaptics()
    val latestInteraction by rememberUpdatedState(onInteraction)
    val controller = remember(state, density) { CrownScrollController(CrownScrollFilter(density)) }
    SideEffect { controller.enabled = enabled; controller.onInteraction = { latestInteraction() } }
    DisposableEffect(controller) { onDispose { controller.requests.close(); controller.clear() } }
    LaunchedEffect(controller, enabled) {
        controller.clear()
        if (!enabled) return@LaunchedEffect
        val pulse = CrownHapticPulse(12f * density)
        for (request in controller.requests) coroutineScope {
            // A competing touch scroll cancels this child, not the event pump.
            launch {
                try {
                    state.scroll(MutatePriority.UserInput) {
                        val initial = controller.takeImmediate()
                        val firstConsumed = scrollBy(initial)
                        if (kotlin.math.abs(initial) > .1f && kotlin.math.abs(firstConsumed) < .05f) controller.clear()
                        if (pulse.moved(firstConsumed, SystemClock.uptimeMillis())) haptics.tick()
                        while (controller.hasPending()) withFrameNanos { frameTime ->
                            val requested = controller.takeFrame(frameTime / 1_000_000)
                            val consumed = scrollBy(requested)
                            if (kotlin.math.abs(requested) > .1f && kotlin.math.abs(consumed) < .05f) controller.clear()
                            if (pulse.moved(consumed, SystemClock.uptimeMillis())) haptics.tick()
                        }
                    }
                } finally { controller.discardPending() }
            }.join()
        }
    }
    return controller
}
