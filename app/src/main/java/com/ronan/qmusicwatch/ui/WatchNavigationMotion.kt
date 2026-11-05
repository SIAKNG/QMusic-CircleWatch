package com.ronan.qmusicwatch.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Short page translations; no content resizing, blur, bitmap snapshots or input lock. */
internal object WatchNavigationMotion {
    fun enter(): EnterTransition = slideInHorizontally(tween(160, easing = FastOutSlowInEasing)) { it }
    fun exit(scope: AnimatedContentTransitionScope<*>): ExitTransition = with(scope) {
        ExitTransition.KeepUntilTransitionsFinished
    }
    fun popEnter(): EnterTransition = EnterTransition.None
    fun popExit(): ExitTransition = slideOutHorizontally(tween(150, easing = FastOutSlowInEasing)) { it }
}

/** Opaque content lets the stationary parent be revealed without text ghosting. */
internal fun NavGraphBuilder.watchDestination(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route) { entry ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content(entry) }
    }
}

/** Read animation frames only in the layer, keeping list item measurement stable. */
@Composable
internal fun Modifier.watchPressFeedback(source: InteractionSource): Modifier {
    val pressed = source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed.value) .98f else 1f,
        tween(if (pressed.value) 55 else 95, easing = FastOutSlowInEasing), label = "navigationPress")
    val drawingLayerActive by remember(source) {
        derivedStateOf { pressed.value || scale.value != 1f }
    }
    return if (drawingLayerActive) graphicsLayer { scaleX = scale.value; scaleY = scale.value } else this
}
