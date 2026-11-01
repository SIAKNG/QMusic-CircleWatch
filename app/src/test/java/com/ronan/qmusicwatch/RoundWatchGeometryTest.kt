package com.ronan.qmusicwatch

import androidx.compose.ui.unit.dp
import com.ronan.qmusicwatch.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RoundWatchGeometryTest {
    @Test fun scaledLayoutKeepsAllFourCornersInsideTheScreenWithoutHeightFeedback() {
        for (diameter in listOf(192f, 200f, 233f, 240f, 320f)) {
            for (position in listOf(.1f, .2f, .35f, .5f, .65f, .75f)) {
                val width = diameter * .9f
                val height = 64f
                val top = diameter * position
                val scale = roundItemScaleFromTop(width, height, top, diameter)
                val x = width * scale / 2
                val radius = diameter / 2 - 2f
                for (y in listOf(top, top + height * scale)) {
                    val dy = y - diameter / 2
                    assertTrue("Clipped corner at $diameter/$position", x*x + dy*dy <= radius*radius)
                }
            }
        }
    }
    @Test fun playbackRingUsesTheActualElapsedFractionAndHandlesUnknownDuration() {
        assertEquals(.5f, playbackProgress(60_000, 120_000))
        assertEquals(0f, playbackProgress(60_000, 0))
        assertEquals(0f, playbackProgress(-1, 120_000))
        assertEquals(1f, playbackProgress(130_000, 120_000))
    }
    @Test fun completeRowCornersStayInsideTheCircleAtEveryVisiblePosition() {
        for (diameter in listOf(192f, 200f, 233f, 240f, 320f)) {
            for (center in listOf(.18f, .25f, .35f, .5f, .65f, .75f, .82f)) {
                val width = diameter * .9f
                val height = 64f
                val scale = roundItemScale(width, height, diameter * center, diameter)
                val x = width * scale / 2
                val y = abs(diameter * center - diameter / 2) + height * scale / 2
                assertTrue("clipped row at $diameter / $center", x*x + y*y <= (diameter/2-2f)*(diameter/2-2f))
            }
        }
    }

    @Test fun marginsFollowScreenSizeAndDoNotGrowWithFontSize() {
        val small = resolveWatchDimensions(200.dp, WatchUiSize.Compact, true)
        val large = resolveWatchDimensions(320.dp, WatchUiSize.Large, true, 1.3f)
        assertEquals(10.dp, small.screenPadding)
        assertEquals(16.dp, large.screenPadding)
        assertEquals(small.itemSpacing, large.itemSpacing)
        assertTrue(large.trackRowHeight > small.trackRowHeight)
        assertTrue(large.bodySp > small.bodySp)
    }

    @Test fun inheritedUpdaterCannotOfferAnUpstreamApkToThisDerivative() {
        val update = kotlinx.coroutines.runBlocking { com.ronan.qmusicwatch.network.ControlPlaneClient().latestRelease() }
        assertFalse(update.hasUpdate)
        assertEquals("1.0", update.versionName)
        assertEquals("com.qmusicwatch.round", BuildConfig.APPLICATION_ID)
    }
}
