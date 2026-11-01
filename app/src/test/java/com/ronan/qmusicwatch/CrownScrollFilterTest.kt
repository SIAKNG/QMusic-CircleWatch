package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.playback.CrownScrollFilter
import com.ronan.qmusicwatch.playback.CrownHapticPulse
import org.junit.Assert.*
import org.junit.Test

class CrownScrollFilterTest {
    private fun drain(filter: CrownScrollFilter): List<Float> = buildList {
        while (filter.hasPending) add(filter.takeFrame())
    }
    @Test fun aFastBurstCannotBuildAnUnboundedScrollQueue() {
        val filter = CrownScrollFilter(2f)
        repeat(500) { filter.offer(1000f, it.toLong()) }
        val frames = drain(filter)
        assertTrue(frames.all { it > 0f && it <= 20f })
        assertEquals(96f, frames.sum(), .001f)
        assertFalse(filter.hasPending)
    }
    @Test fun crownMovementUsesLowerSensitivity() {
        val filter = CrownScrollFilter()
        assertTrue(filter.offer(20f, 1000))
        assertEquals(7f, drain(filter).sum(), .001f)
    }
    @Test fun aSmallOppositeSpikeDoesNotReverseTheList() {
        val filter = CrownScrollFilter()
        filter.offer(40f, 1000)
        assertFalse(filter.offer(-1f, 1010))
        assertTrue(drain(filter).all { it > 0 })
    }
    @Test fun aDeliberateReverseTurnDropsOldPendingMotion() {
        val filter = CrownScrollFilter()
        filter.offer(100f, 1000)
        assertTrue(filter.offer(-30f, 1010))
        assertTrue(drain(filter).all { it < 0 })
    }
    @Test fun newInputAfterAPauseDoesNotReplayStaleMotion() {
        val filter = CrownScrollFilter()
        filter.offer(100f, 1000)
        filter.offer(10f, 1400)
        assertEquals(3.5f, drain(filter).sum(), .001f)
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
        assertFalse(pulse.moved(0f, 1000))
        assertFalse(pulse.moved(6f, 1000))
        assertTrue(pulse.moved(6f, 1001))
        assertFalse(pulse.moved(24f, 1020))
        assertTrue(pulse.moved(1f, 1080))
    }
    @Test fun touchingTheListCanDiscardTheRotaryTail() {
        val filter = CrownScrollFilter()
        filter.offer(100f, 0)
        filter.discardPending()
        assertFalse(filter.hasPending)
        filter.offer(-10f, 500)
        assertTrue(filter.takeFrame() < 0)
    }
}
