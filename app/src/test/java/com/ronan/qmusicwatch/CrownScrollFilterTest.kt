package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.playback.CrownScrollFilter
import com.ronan.qmusicwatch.playback.CrownHapticPulse
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CrownScrollFilterTest {
    private fun drain(filter: CrownScrollFilter, start: Long, frameMs: Long = 16): List<Float> = buildList {
        var now = start
        while (filter.hasPending && now - start < 1500) { add(filter.takeFrame(now)); now += frameMs }
        assertFalse("Crown tail must settle", filter.hasPending)
    }
    @Test fun aFastBurstCannotBuildAnUnboundedScrollQueue() {
        val filter = CrownScrollFilter(2f)
        repeat(500) { filter.offer(1000f, it.toLong()) }
        val frames = drain(filter, 500)
        assertTrue(frames.all { it >= 0f && it <= 154f })
        assertTrue(frames.sum() < 1500f)
    }
    @Test fun aSingleSlowDetentRemainsPreciseAndHasAShortTail() {
        val filter = CrownScrollFilter()
        filter.offer(20f, 1000)
        val frames = drain(filter, 1000)
        assertTrue(frames.sum() in 7f..22f)
        assertTrue(frames.drop(3).any { it > 0f })
    }
    @Test fun theFirstDetentMovesImmediatelyAndReversalDoesNotKeepOldMotion() {
        val filter = CrownScrollFilter()
        filter.offer(20f, 1000)
        assertTrue(filter.takeImmediate() >= 7f)
        filter.offer(-30f, 1010)
        assertTrue(filter.takeImmediate() < 0f)
        assertTrue(drain(filter, 1010).all { it <= 0 })
    }
    @Test fun aRapidBurstFollowsWithinTwoFramesAndSettlesPromptly() {
        val filter = CrownScrollFilter()
        repeat(8) { filter.offer(80f, 1000L + it * 8) }
        val frames = drain(filter, 1056)
        assertTrue("Direct input must not lag behind a long animation", frames.take(2).sum() > 65f)
        assertTrue("Keep only a short inertial tail", frames.size <= 17)
    }
    @Test fun fasterTurningTravelsFurtherWithTheSameNumberOfDetents() {
        fun travel(interval: Long): Float {
            val filter = CrownScrollFilter()
            var result = 0f
            var event = 0
            var now = 0L
            while (event < 12 || filter.hasPending) {
                if (event < 12 && now >= event * interval) { filter.offer(28f, now); event++ }
                if (filter.hasPending) result += filter.takeFrame(now)
                now += 16
            }
            return result
        }
        assertTrue(travel(16) > travel(128) * 1.5f)
    }
    @Test fun aSmallOppositeSpikeDoesNotReverseTheList() {
        val filter = CrownScrollFilter()
        filter.offer(40f, 1000)
        assertFalse(filter.offer(-1f, 1010))
        assertTrue(drain(filter, 1010).all { it >= 0 })
    }
    @Test fun aDeliberateReverseTurnCancelsTheOldInertia() {
        val filter = CrownScrollFilter()
        filter.offer(100f, 1000)
        assertTrue(filter.offer(-30f, 1010))
        assertTrue(drain(filter, 1010).all { it <= 0 })
    }
    @Test fun newInputAfterAPauseDoesNotReplayStaleMotion() {
        val filter = CrownScrollFilter()
        filter.offer(100f, 1000)
        filter.offer(10f, 1400)
        assertTrue(drain(filter, 1400).sum() < 12f)
    }
    @Test fun coastingDistanceDoesNotDependOnFrameRate() {
        fun travel(frameMs: Long): Float = CrownScrollFilter().let {
            it.offer(40f, 1000); drain(it, 1000, frameMs).sum()
        }
        assertTrue(abs(travel(16) - travel(32)) < 3f)
    }
    @Test fun nonFiniteAndTinySignalsDoNotMoveTheList() {
        val filter = CrownScrollFilter()
        assertFalse(filter.offer(Float.NaN, 0))
        assertFalse(filter.offer(Float.POSITIVE_INFINITY, 1))
        assertFalse(filter.offer(.01f, 2))
        assertFalse(filter.hasPending)
    }
    @Test fun hapticsRequireRealTravelAndHaveAMinimumInterval() {
        val pulse = CrownHapticPulse(12f)
        assertFalse(pulse.moved(6f, 1000)); assertTrue(pulse.moved(6f, 1001))
        assertFalse(pulse.moved(24f, 1020)); assertTrue(pulse.moved(1f, 1080))
    }
    @Test fun touchingTheListCancelsAllRotaryMotion() {
        val filter = CrownScrollFilter()
        filter.offer(100f, 1000); filter.takeFrame(1016); filter.discardPending()
        assertFalse(filter.hasPending)
        filter.offer(-10f, 1100); assertTrue(filter.takeFrame(1100) < 0)
    }
}
