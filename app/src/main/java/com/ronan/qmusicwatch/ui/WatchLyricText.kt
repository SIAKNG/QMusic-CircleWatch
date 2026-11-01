package com.ronan.qmusicwatch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import com.ronan.qmusicwatch.lyrics.LyricLine
import com.ronan.qmusicwatch.lyrics.hasWordTiming
import com.ronan.qmusicwatch.lyrics.timedLyricProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private class LyricPlaybackSample(val positionMs: Long)

/** Reserve the largest rendering bounds so emphasis never changes line breaks or scroll geometry. */
@Composable
internal fun WatchLyricText(
    text: String,
    requestedFontSp: Float,
    emphasized: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    renderProgress: Float? = null,
    wordTimedLine: LyricLine? = null,
    playbackPositionMs: Long = 0L,
    playing: Boolean = false,
    lowPower: Boolean = false,
    accent: Color = WatchAccent,
    animationMillis: Int = 200,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val restingScale = .84f
    val maximumScale = restingScale + (1.12f - restingScale) * .7f
    val scale by animateFloatAsState(
        if (emphasized) maximumScale else restingScale,
        tween(animationMillis), label = "lyricEmphasis",
    )
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val validTiming = remember(wordTimedLine) { wordTimedLine?.takeIf(::hasWordTiming) }
    val sample = remember(playbackPositionMs, playing) { LyricPlaybackSample(playbackPositionMs) }
    val latestSample by rememberUpdatedState(sample)
    val frameProgress = remember(text) { mutableFloatStateOf(renderProgress ?: 0f) }
    LaunchedEffect(validTiming, playing, lowPower) {
        val line = validTiming ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        var anchorSample: LyricPlaybackSample? = null
        var anchorFrame = 0L
        while (isActive) {
            withFrameNanos { frame ->
                val current = latestSample
                if (current !== anchorSample) { anchorSample = current; anchorFrame = frame }
                val elapsedMs = ((frame - anchorFrame) / 1_000_000L).coerceIn(0L, 1_500L)
                // Only draw reads this state; advancing a frame never measures
                // text or recomposes the player/list. Word gaps do not redraw.
                frameProgress.floatValue = timedLyricProgress(line, current.positionMs + elapsedMs)
            }
            if (lowPower) delay(30)
        }
    }
    Text(
        text = text,
        modifier = modifier.layout { measurable, constraints ->
            val textWidth = (constraints.maxWidth / maximumScale).toInt().coerceAtLeast(1)
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = textWidth, minHeight = 0))
            val height = ceil(placeable.height * maximumScale).toInt()
            layout(constraints.maxWidth, height) {
                val x = if (centered) (constraints.maxWidth - placeable.width) / 2 else 0
                placeable.placeWithLayer(x, (height - placeable.height) / 2) {
                    transformOrigin = TransformOrigin(if (centered) .5f else 0f, .5f)
                    scaleX = scale
                    scaleY = scale
                }
            }
        }.fillMaxWidth().drawWithContent {
            drawContent()
            val layout = textLayout
            val progress = if (validTiming != null && playing) frameProgress.floatValue else renderProgress
            if (progress != null && layout != null && progress > 0f && text.isNotEmpty()) {
                val characters = progress.coerceIn(0f, 1f) * text.length
                val completed = floor(characters).toInt().coerceIn(0, text.length)
                if (completed == text.length) {
                    drawText(layout, color = accent)
                } else {
                    val visualLine = layout.getLineForOffset(completed)
                    val top = layout.getLineTop(visualLine)
                    if (visualLine > 0) clipRect(bottom = top) { drawText(layout, color = accent) }
                    val box = layout.getBoundingBox(completed)
                    val fraction = characters - completed
                    val rtl = layout.getBidiRunDirection(completed) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl
                    val front = if (rtl) box.right - box.width * fraction else box.left + box.width * fraction
                    val feather = requestedFontSp.sp.toPx() * .28f
                    val brush = if (rtl) Brush.horizontalGradient(listOf(accent.copy(alpha = 0f), accent), front, front + feather)
                        else Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0f)), front - feather, front)
                    // A soft trailing edge stays on the current visual line;
                    // later wrapped lines remain untouched until their turn.
                    clipRect(top = top, bottom = layout.getLineBottom(visualLine)) {
                        // Match the completed-text pass. The default brush
                        // alpha inherits the grey base style and would make
                        // the whole line brighten again when it completes.
                        drawText(layout, brush = brush, alpha = 1f)
                    }
                }
            }
        },
        color = color,
        fontSize = requestedFontSp.sp,
        lineHeight = (requestedFontSp * 1.3f).sp,
        // The weight also stays fixed: switching it can change wrapping mid-scroll.
        fontWeight = FontWeight.Bold,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        maxLines = 3,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { textLayout = it; onTextLayout(it) },
    )
}
