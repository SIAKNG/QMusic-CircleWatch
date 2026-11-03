package com.ronan.qmusicwatch

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], qualifiers = "mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconSafetyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun render(drawable: Drawable, size: Int, circular: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (circular) canvas.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun artwork(color: Int): Boolean = Color.alpha(color) > 128 && Color.green(color) > 100
    private fun save(name: String, bitmap: Bitmap) {
        val folder = File(requireNotNull(System.getProperty("qmusic.preview.dir")), "launcher-icon")
        folder.mkdirs()
        File(folder, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun completeAdaptiveMarkIncludingCrownFitsInsideTheGuaranteedSafeArea() {
        val drawable = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue(drawable is AdaptiveIconDrawable)
        val foreground = render((drawable as AdaptiveIconDrawable).foreground, 108)
        var count = 0
        var radius = 0.0
        var crownPixels = 0
        for (y in 0 until 108) for (x in 0 until 108) {
            if (artwork(foreground.getPixel(x, y))) {
                count++
                radius = maxOf(radius, hypot(x + .5 - 54, y + .5 - 54))
                if (x >= 79 && y in 47..57) crownPixels++
            }
        }
        save("adaptive-foreground-108.png", foreground)
        save("adaptive-round-432.png", render(requireNotNull(context.getDrawable(R.mipmap.ic_launcher_round)), 432, true))
        save("adaptive-round-48.png", render(requireNotNull(context.getDrawable(R.mipmap.ic_launcher_round)), 48, true))
        assertTrue("Missing foreground artwork", count > 500)
        assertTrue("Crown detail disappeared", crownPixels > 4)
        assertTrue("Artwork exceeds the 66dp safe circle: radius=$radius", radius < 33)
    }

    @Test fun legacyCircularClippingKeepsEveryBrightArtworkPixelIncludingTheCrown() {
        val full = render(requireNotNull(context.getDrawable(R.drawable.ic_launcher)), 432)
        val round = render(requireNotNull(context.getDrawable(R.drawable.ic_launcher)), 432, true)
        var count = 0
        var lost = 0
        for (y in 0 until 432) for (x in 0 until 432) {
            if (artwork(full.getPixel(x, y))) {
                count++
                if (!artwork(round.getPixel(x, y))) lost++
            }
        }
        assertTrue(count > 5_000)
        assertEquals("Circular clipping removed ring/crown artwork", 0, lost)
        save("legacy-round-432.png", round)
        save("qmusic-circlewatch-icon.png", render(requireNotNull(context.getDrawable(R.drawable.ic_launcher)), 1080))
    }

    @Test fun launcherScalingAtDifferentSizesKeepsEveryForegroundPixelInsideTheCircularMask() {
        for (size in listOf(48, 96, 432)) {
            val icon = context.getDrawable(R.mipmap.ic_launcher_round) as AdaptiveIconDrawable
            val masked = render(icon, size, true)
            // AdaptiveIconDrawable expands its foreground beyond its own bounds.
            // Keep those actual bounds; testing a standalone 108dp layer misses
            // fixed-dp inset bugs when the launcher draws a different icon size.
            val expandedForeground = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.foreground.draw(Canvas(expandedForeground))
            var visible = 0
            var lost = 0
            for (y in 0 until size) for (x in 0 until size) {
                if (artwork(expandedForeground.getPixel(x, y))) {
                    visible++
                    if (!artwork(masked.getPixel(x, y))) lost++
                }
            }
            assertTrue("Missing icon at size $size", visible > size)
            assertEquals("Launcher clipped ring/crown at size $size", 0, lost)
            save("verified-adaptive-round-$size.png", masked)
        }
    }
}
