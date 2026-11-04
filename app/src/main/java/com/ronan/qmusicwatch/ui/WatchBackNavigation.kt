package com.ronan.qmusicwatch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ronan.qmusicwatch.shouldTriggerSwipeBack
import kotlin.math.abs

/** System Back and edge swipes pop the same in-app stack before leaving home. */
@Composable
internal fun rememberWatchBackAction(nav: NavHostController, onRootBack: () -> Unit): () -> Unit {
    val entry by nav.currentBackStackEntryAsState()
    val rootBack by rememberUpdatedState(onRootBack)
    val action = remember(nav) {{
        if (nav.currentDestination?.route == "home") rootBack()
        else if (!nav.popBackStack()) nav.navigate("home") { launchSingleTop = true }
    }}
    BackHandler(enabled = entry?.destination?.route != null && entry?.destination?.route != "home", onBack = action)
    return action
}

/** Claim only a horizontal rightward gesture that began at the physical edge. */
internal fun Modifier.watchSwipeBack(
    enabled: Boolean, thresholdPx: Float, leftEdgePx: Float, onBack: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(thresholdPx, leftEdgePx, onBack) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x > leftEdgePx) return@awaitEachGesture
        var horizontal = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y
            if (!horizontal && maxOf(abs(dx), abs(dy)) > viewConfiguration.touchSlop) {
                if (dx <= 0 || abs(dy) >= abs(dx)) break
                horizontal = true
            }
            if (horizontal) change.consume()
            if (!change.pressed) {
                if (horizontal && shouldTriggerSwipeBack(dx, down.position.x, leftEdgePx, thresholdPx)) onBack()
                break
            }
        }
    }
}
