package com.ronan.qmusicwatch.ui

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.ColorUtils
import com.ronan.qmusicwatch.network.safeLocalOrArtworkUri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

private val accentCache = object : LinkedHashMap<String, Int>(24, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 24
}

internal fun readableArtworkAccent(argb: Int): Int {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    hsv[2] = hsv[2].coerceAtLeast(.88f)
    if (hsv[1] < .14f) return AndroidColor.rgb(181, 189, 200)
    hsv[1] = hsv[1].coerceIn(.35f, .82f)
    var color = AndroidColor.HSVToColor(hsv)
    while (ColorUtils.calculateContrast(color, AndroidColor.BLACK) < 4.5 && hsv[1] > .3f) {
        hsv[1] -= .04f
        color = AndroidColor.HSVToColor(hsv)
    }
    return color
}

private fun cachedAccent(value: String, artworkUrl: String): Int? {
    val separator = value.lastIndexOf('|')
    if (separator <= 0 || value.substring(0, separator) != artworkUrl) return null
    return value.substring(separator + 1).toLongOrNull()?.toInt()
}

internal fun extractArtworkAccent(pixels: IntArray): Int? {
    val weights = FloatArray(12)
    val red = LongArray(12)
    val green = LongArray(12)
    val blue = LongArray(12)
    val counts = IntArray(12)
    var opaque = false
    val hsv = FloatArray(3)
    for (index in pixels.indices step 3) {
        val pixel = pixels[index]
        if (AndroidColor.alpha(pixel) < 128) continue
        opaque = true
        AndroidColor.colorToHSV(pixel, hsv)
        if (hsv[1] < .14f || hsv[2] < .12f) continue
        val bucket = (hsv[0] / 30f).toInt().coerceIn(0, 11)
        weights[bucket] += hsv[1] * (.5f + .5f * hsv[2])
        red[bucket] += AndroidColor.red(pixel)
        green[bucket] += AndroidColor.green(pixel)
        blue[bucket] += AndroidColor.blue(pixel)
        counts[bucket]++
    }
    val bucket = weights.indices.maxByOrNull { weights[it] } ?: return null
    val count = counts[bucket]
    if (count == 0) return if (opaque) AndroidColor.rgb(181, 189, 200) else null
    return readableArtworkAccent(AndroidColor.rgb((red[bucket] / count).toInt(),
        (green[bucket] / count).toInt(), (blue[bucket] / count).toInt()))
}

@Composable
fun rememberArtworkAccent(
    artworkUrl: String,
    cachedValue: String,
    onResolved: (String, Long) -> Unit,
): Color {
    val context = androidx.compose.ui.platform.LocalContext.current
    val safeUrl = remember(artworkUrl) { safeLocalOrArtworkUri(artworkUrl) }
    val cached = remember(safeUrl, cachedValue) {
        synchronized(accentCache) { accentCache[safeUrl] }
            ?: cachedAccent(cachedValue, safeUrl)?.let(::readableArtworkAccent)
    }
    var accent by remember(safeUrl) { mutableStateOf(cached?.let(::Color) ?: WatchAccent) }
    LaunchedEffect(safeUrl, cached) {
        if (cached != null) { accent = Color(cached); return@LaunchedEffect }
        if (safeUrl.isBlank()) return@LaunchedEffect
        val resolved = try { withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(safeUrl)
                .size(64)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
            val bitmap = result.drawable.toBitmap(64, 64)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            extractArtworkAccent(pixels)
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } ?: return@LaunchedEffect
        synchronized(accentCache) { accentCache[safeUrl] = resolved }
        accent = Color(resolved)
        onResolved(safeUrl, resolved.toLong() and 0xffffffffL)
    }
    return accent
}
