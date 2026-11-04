package com.ronan.qmusicwatch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import coil.compose.AsyncImage
import com.ronan.qmusicwatch.R

@Stable
class MiniPlayerController {
    var collapsed by mutableStateOf(false)
        private set

    fun onListScrollStarted() { collapsed = true }
    fun expand() { collapsed = false }
}

val LocalMiniPlayerController = staticCompositionLocalOf<MiniPlayerController?> { null }

internal fun playbackProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0) 0f else (positionMs.coerceAtLeast(0).toDouble() / durationMs).toFloat().coerceIn(0f, 1f)

@Composable
fun WatchMiniPlayer(
    title: String,
    subtitle: String,
    artwork: Any?,
    playing: Boolean,
    progress: Float,
    controller: MiniPlayerController,
    modifier: Modifier = Modifier,
    accent: Color = WatchAccent,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val dimensions = LocalWatchDimensions.current
    val round = dimensions.isRound
    val collapsed = round && controller.collapsed
    val fraction = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    val morph by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = .001f),
        label = "miniPlayerMorphProgress",
    )
    val amount = morph.coerceIn(0f, 1f)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val expandedWidth = if (round) maxWidth * .74f else maxWidth - 12.dp
        val expandedHeight = if (round) 46.dp else dimensions.miniPlayerHeight
        val expandedArtwork = if (round) 34.dp else dimensions.artworkSize
        val width = lerp(expandedWidth, 44.dp, amount)
        val height = lerp(expandedHeight, 44.dp, amount)
        val artworkSize = lerp(expandedArtwork, 44.dp, amount)
        val details = ((.6f - amount) / .6f).coerceIn(0f, 1f)
        val detailsAlpha = details * details * (3f - 2f * details)
        Surface(
            Modifier.padding(bottom = if (round) 20.dp else 0.dp).width(width).height(height)
                .clip(CircleShape)
                .testTag(if (collapsed) "mini-player-collapsed" else "mini-player-expanded")
                .clickable(role = Role.Button) {
                    if (collapsed) controller.expand() else onOpenPlayer()
                }
                .semantics { contentDescription = if (collapsed) "展开正在播放" else "打开播放器" },
            shape = RoundedCornerShape(50), color = WatchSurfaceRaised, contentColor = WatchTextPrimary, tonalElevation = 0.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Keep text measured at its expanded width. Fading a stable row
                // avoids reflowing the title at every intermediate capsule size.
                if (amount < .6f || !collapsed) {
                    Row(
                        Modifier.offset(x = 7.dp + expandedArtwork + 7.dp)
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .width((expandedWidth - 7.dp - expandedArtwork - 7.dp - 3.dp).coerceAtLeast(1.dp))
                            .height(expandedHeight)
                            .graphicsLayer { alpha = detailsAlpha }
                            .then(if (collapsed) Modifier.clearAndSetSemantics {} else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = if (round) 13.sp else dimensions.bodySp.sp, fontWeight = FontWeight.SemiBold)
                            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = WatchTextSecondary, fontSize = if (round) 10.sp else dimensions.secondarySp.sp)
                        }
                        WatchIconButton(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playing) "暂停" else "播放", buttonSize = if (round) 36.dp else dimensions.touchTarget,
                            containerColor = Color.Transparent, onClick = { if (!collapsed) onPlayPause() },
                            tint = accent,
                        )
                    }
                }
                // One artwork and one ring survive both states and interruptions.
                // As the centered capsule shrinks, its left inset tends to zero,
                // carrying the original album smoothly to the screen center.
                AlbumProgressRing(artwork, fraction,
                    Modifier.offset(x = lerp(7.dp, 0.dp, amount), y = (height - artworkSize) / 2)
                        .size(artworkSize).testTag("mini-player-artwork"), accent)
            }
        }
    }
}

@Composable
private fun AlbumProgressRing(artwork: Any?, progress: Float, modifier: Modifier, accent: Color) {
    val fallback = painterResource(R.drawable.ic_launcher)
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            artwork, null,
            modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape).background(WatchSurface),
            contentScale = ContentScale.Crop, placeholder = fallback, fallback = fallback, error = fallback,
        )
        Canvas(Modifier.fillMaxSize().testTag("mini-player-progress")
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(WatchDivider, -90f, 360f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress > 0) drawArc(accent, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
