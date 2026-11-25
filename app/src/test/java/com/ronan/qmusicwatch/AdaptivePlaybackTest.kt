package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.model.StreamData
import com.ronan.qmusicwatch.network.QqCredentialExpiredException
import com.ronan.qmusicwatch.network.lowerNetworkQuality
import com.ronan.qmusicwatch.network.resolvePlaybackStream
import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AdaptivePlaybackTest {
    @Test fun healthyNetworkKeepsTheRequestedQuality() = runBlocking {
        val result = resolvePlaybackStream("sq", 40) { quality -> StreamData("https://example/$quality", quality, 1000) }
        assertEquals("sq", result.quality)
        assertFalse(result.networkFallback)
    }

    @Test fun slowRequestIsCancelledAndNextSongStartsWithTheDefaultAgain() = runBlocking {
        val requests = mutableListOf<String>()
        var cancelled = false
        val result = resolvePlaybackStream("sq", 40) { quality ->
            requests += quality
            if (quality == "sq") {
                try { awaitCancellation() } finally { cancelled = true }
            }
            StreamData("https://example/$quality", quality, 1000)
        }
        assertEquals(listOf("sq", "hq"), requests)
        assertTrue(cancelled)
        assertEquals("hq", result.quality)
        assertTrue(result.networkFallback)
        val nextRequests = mutableListOf<String>()
        val next = resolvePlaybackStream("sq", 40) { quality ->
            nextRequests += quality
            StreamData("https://example/next", quality, 1000)
        }
        assertEquals(listOf("sq"), nextRequests)
        assertEquals("sq", next.quality)
        assertFalse(next.networkFallback)
    }

    @Test fun connectionFailureFallsBackToStandardForThisRequest() = runBlocking {
        val result = resolvePlaybackStream("hq", 40) { quality ->
            if (quality == "hq") throw IOException("connection reset")
            StreamData("https://example/standard", quality, 1000)
        }
        assertEquals("standard", result.quality)
        assertTrue(result.networkFallback)
        assertEquals("hq", lowerNetworkQuality("sq"))
        assertNull(lowerNetworkQuality("standard"))
    }

    @Test fun membershipFailureDoesNotTriggerNetworkDowngrade() = runBlocking {
        val requests = mutableListOf<String>()
        try {
            resolvePlaybackStream("sq", 40) { quality ->
                requests += quality
                throw QqCredentialExpiredException("login expired")
            }
            fail("Credential error must propagate")
        } catch (_: QqCredentialExpiredException) {
            assertEquals(listOf("sq"), requests)
        }
    }

    @Test fun cancelledSongCannotStartALowerQualityRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val requests = mutableListOf<String>()
        val job = launch {
            resolvePlaybackStream("sq", 1000) { quality ->
                requests += quality
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(listOf("sq"), requests)
    }

    @Test fun standardFailureTerminatesWithoutRepeatingTheRequest() = runBlocking {
        var requests = 0
        try {
            resolvePlaybackStream("standard", 40) {
                requests++
                throw IOException("offline")
            }
            fail("Offline error must propagate")
        } catch (_: IOException) {
            assertEquals(1, requests)
        }
    }
}
