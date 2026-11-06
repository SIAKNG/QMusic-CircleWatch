package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.network.awaitBody
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HttpAwaitTest {
    @Test fun completedResponsesReleaseConnectionsForReuse() = runBlocking {
        val server = MockWebServer(); server.start()
        val client = OkHttpClient()
        try {
            repeat(2) { server.enqueue(MockResponse().setBody("complete")) }
            repeat(2) {
                val result = client.newCall(Request.Builder().url(server.url("/data")).build()).awaitBody { it.body!!.string() }
                assertEquals("complete", result)
            }
            assertEquals(0, server.takeRequest().sequenceNumber)
            assertEquals(1, server.takeRequest().sequenceNumber)
        } finally { server.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }

    @Test fun cancellingWhileWaitingForHeadersCancelsTheUnderlyingCall() = runBlocking {
        val server = MockWebServer(); server.start()
        val client = OkHttpClient()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val call = client.newCall(Request.Builder().url(server.url("/data")).build())
            val pending = async(Dispatchers.Default) { call.awaitBody { it.body!!.string() } }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(2_000) { pending.cancelAndJoin() }
            assertTrue(call.isCanceled())
        } finally { server.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }

    @Test fun cancellingDuringABodyReadDoesNotWaitForTheWholeBody() = runBlocking {
        val server = MockWebServer(); server.start()
        val client = OkHttpClient()
        try {
            server.enqueue(MockResponse().setBody("slowbody").throttleBody(1, 1, TimeUnit.SECONDS))
            val call = client.newCall(Request.Builder().url(server.url("/data")).build())
            val reading = CompletableDeferred<Unit>()
            val pending = async(Dispatchers.Default) { call.awaitBody { reading.complete(Unit); it.body!!.string() } }
            withTimeout(2_000) { reading.await() }
            withTimeout(2_000) { pending.cancelAndJoin() }
            assertTrue(call.isCanceled())
        } finally { server.shutdown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
}
