package com.ronan.qmusicwatch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ronan.qmusicwatch.playback.DeviceVolumeState
import kotlin.math.roundToInt

@Composable
internal fun WatchVolumePanel(
    volume: DeviceVolumeState?,
    onChange: (Int) -> Unit,
) {
    val level = volume?.current ?: 0
    val maxLevel = volume?.max ?: 0
    val currentLevel by rememberUpdatedState(level)
    val onVolumeChange by rememberUpdatedState(onChange)
    val sliderGesture = Modifier.pointerInput(maxLevel) {
        if (maxLevel <= 0) return@pointerInput
        var dragLevel = 0f
        var lastStep = 0
        detectVerticalDragGestures(
            onDragStart = {
                lastStep = currentLevel
                dragLevel = lastStep.toFloat()
            },
        ) { change, delta ->
            change.consume()
            // A slow, short drag should move only one system volume step.
            val stepsPerPixel = maxLevel / (size.height * 1.25f)
            dragLevel = (dragLevel - delta * stepsPerPixel).coerceIn(0f, maxLevel.toFloat())
            // Use separate thresholds for increasing and decreasing a step,
            // so small finger movements around the boundary cannot toggle it.
            val target = when {
                dragLevel > lastStep -> (dragLevel - .2f).roundToInt().coerceAtLeast(lastStep)
                dragLevel < lastStep -> (dragLevel + .2f).roundToInt().coerceAtMost(lastStep)
                else -> lastStep
            }
            if (target != lastStep) {
                lastStep = target
                onVolumeChange(target)
            }
        }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(indication = null, interactionSource = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        val diameter = minOf(maxWidth, maxHeight)
        val fraction = if (maxLevel > 0) (level.toFloat() / maxLevel).coerceIn(0f, 1f) else 0f
        val animatedFraction = animateFloatAsState(fraction, tween(110), label = "volumeFill")
        Box(
            Modifier.size(diameter * .292f, diameter * .69f)
                .clip(CircleShape).background(Color.White.copy(alpha = .12f))
                .drawBehind {
                    val fillHeight = size.height * animatedFraction.value
                    drawRect(Color.White.copy(alpha = .53f),
                        topLeft = Offset(0f, size.height - fillHeight), size = Size(size.width, fillHeight))
                }
                .then(sliderGesture).semantics {
                    contentDescription = "媒体音量"
                    progressBarRangeInfo = ProgressBarRangeInfo(level.toFloat(), 0f..maxLevel.toFloat())
                    if (maxLevel > 0) setProgress {
                        onVolumeChange(it.roundToInt().coerceIn(0, maxLevel))
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val icon = when {
                volume?.muted == true || level == 0 -> com.heytap.wearable.R.drawable.hey_volume_mute
                level * 3 <= maxLevel -> com.heytap.wearable.R.drawable.hey_volume_light
                level * 3 <= maxLevel * 2 -> com.heytap.wearable.R.drawable.hey_volume_middle
                else -> com.heytap.wearable.R.drawable.hey_volume_high
            }
            Icon(painterResource(icon), null, Modifier.size(diameter * .10f), tint = Color.White)
        }
    }
}
