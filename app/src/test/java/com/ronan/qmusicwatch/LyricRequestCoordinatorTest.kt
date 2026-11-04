package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.lyrics.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LyricRequestCoordinatorTest {
    private val a = LyricRequestKey("owner-a", "song-a", 1)
    private val b = LyricRequestKey("owner-b", "song-b", 2)
    private fun lines(text: String) = listOf(LyricLine(0, text))

    @Test fun restoredTrackLoadsWithoutStartingPlayback() = runBlocking {
        val states = mutableListOf<LyricLoadResult>()
        val loader = LyricRequestCoordinator(this, { _, _ -> lines("restored") }, { _, s -> states += s })
        loader.request(a); yield()
        assertTrue(states.first().loading)
        assertEquals("restored", states.last().lines.single().text)
    }
    @Test fun openingLyricsDuringAnExistingRequestDoesNotDuplicateIt() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val states = mutableListOf<LyricLoadResult>()
        val loader = LyricRequestCoordinator(this, { _, _ -> calls++; gate.await(); lines("ready") }, { _, s -> states += s })
        loader.request(a); yield(); repeat(10) { loader.request(a) }
        assertEquals(1, calls); assertEquals(1, states.size)
        gate.complete(Unit); yield()
        assertEquals("ready", states.last().lines.single().text)
    }
    @Test fun transientFailureRetriesAutomaticallyAndKeepsLoadingState() = runBlocking {
        var calls = 0
        val states = mutableListOf<LyricLoadResult>()
        val loader = LyricRequestCoordinator(this, { _, _ ->
            if (++calls < 3) throw IOException("temporary")
            lines("recovered")
        }, { _, s -> states += s }, retryPause = { yield() })
        loader.request(a)
        withTimeout(2000) { while (states.last().loading) yield() }
        assertEquals(3, calls); assertEquals(2, states.size)
        assertFalse(states.last().failed)
        assertEquals("recovered", states.last().lines.single().text)
    }
    @Test fun genuineEmptyResultIsDistinctFromNetworkFailure() = runBlocking {
        var calls = 0
        var result = LyricLoadResult(loading = true)
        val loader = LyricRequestCoordinator(this, { _, _ -> calls++; emptyList() }, { _, s -> result = s }, retryPause = { yield() })
        loader.request(a)
        withTimeout(2000) { while (result.loading) yield() }
        assertEquals(2, calls); assertTrue(result.lines.isEmpty()); assertFalse(result.failed)
    }
    @Test fun repeatedErrorsAreShownAsErrorsNotNoLyrics() = runBlocking {
        var calls = 0
        var result = LyricLoadResult(loading = true)
        val loader = LyricRequestCoordinator(this, { _, _ -> calls++; throw IOException() }, { _, s -> result = s }, retryPause = { yield() })
        loader.request(a)
        withTimeout(2000) { while (result.loading) yield() }
        assertEquals(3, calls); assertTrue(result.failed)
    }
    @Test fun staleRequestCannotReplaceTheNewSongEvenIfItIgnoresCancellation() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val results = mutableListOf<Pair<LyricRequestKey, LyricLoadResult>>()
        val loader = LyricRequestCoordinator(this, { key, _ ->
            if (key == a) withContext(NonCancellable) { gate.await() }
            lines(key.trackId)
        }, { key, result -> results += key to result })
        loader.request(a); yield(); loader.request(b); yield(); gate.complete(Unit); yield()
        assertEquals(b, results.last().first)
        assertEquals("song-b", results.last().second.lines.single().text)
    }
    @Test fun cacheIsAccountScopedAndManualReloadBypassesIt() = runBlocking {
        var calls = 0
        val loader = LyricRequestCoordinator(this, { _, _ -> calls++; lines("loaded") }, { _, _ -> })
        loader.request(a); yield(); loader.request(b); yield(); loader.request(a); yield()
        assertEquals(2, calls)
        loader.request(a, force = true); yield(); assertEquals(3, calls)
    }
    @Test fun logoutInvalidatesAnInFlightResult() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val states = mutableListOf<LyricLoadResult>()
        val loader = LyricRequestCoordinator(this, { _, _ ->
            withContext(NonCancellable) { gate.await() }; lines("old-account")
        }, { _, s -> states += s })
        loader.request(a); yield(); loader.clear(); gate.complete(Unit); yield()
        assertEquals(1, states.size); assertTrue(states.single().loading)
    }
}
