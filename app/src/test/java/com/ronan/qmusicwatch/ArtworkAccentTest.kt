package com.ronan.qmusicwatch

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.ronan.qmusicwatch.ui.extractArtworkAccent
import com.ronan.qmusicwatch.ui.readableArtworkAccent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ArtworkAccentTest {
    @Test fun artworkChangesProduceDifferentDominantColors() {
        val red = extractArtworkAccent(IntArray(300) { if (it < 240) Color.RED else Color.BLUE })!!
        val blue = extractArtworkAccent(IntArray(300) { if (it < 240) Color.BLUE else Color.RED })!!
        assertTrue(Color.red(red) > Color.blue(red))
        assertTrue(Color.blue(blue) > Color.red(blue))
        assertNotEquals(red, blue)
    }
    @Test fun darkOrBlueCoversRemainReadableAgainstBlack() {
        for (color in listOf(Color.BLUE, Color.rgb(28, 8, 20), Color.rgb(0, 24, 70))) {
            assertTrue(ColorUtils.calculateContrast(readableArtworkAccent(color), Color.BLACK) >= 4.5)
        }
    }
    @Test fun neutralCoversAndTransparentImagesHaveExplicitFallbacks() {
        assertNotNull(extractArtworkAccent(IntArray(30) { Color.BLACK }))
        assertNotNull(extractArtworkAccent(IntArray(30) { Color.WHITE }))
        assertNull(extractArtworkAccent(IntArray(30) { Color.TRANSPARENT }))
        assertNull(extractArtworkAccent(intArrayOf()))
    }
}
