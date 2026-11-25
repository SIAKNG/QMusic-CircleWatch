package com.ronan.qmusicwatch

import com.ronan.qmusicwatch.model.UserProfile
import com.ronan.qmusicwatch.model.Track
import com.ronan.qmusicwatch.network.ApiClient
import com.ronan.qmusicwatch.network.ControlPlaneClient
import com.ronan.qmusicwatch.network.profileEpoch
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LoginAndPlaybackRevisionTest {
    @Test fun acceptsLargeQrResponsesAndRetriesAnInitialConnectionFailure() = runBlocking {
        var requests = 0
        val image = "AAAA".repeat(90_000)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            if (requests == 1) throw IOException("connection reset")
            val body = """{"ok":true,"data":{"id":"${"a".repeat(32)}","provider":"wechat","mimeType":"image/jpeg","imageBase64":"$image","expiresAt":4102444800000,"pollAfterMs":2000}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val result = ControlPlaneClient(http = http).createQrLogin("wechat")
        assertEquals(image, result.imageBase64)
        assertEquals(2, requests)
    }

    @Test fun cancellingQrCreationStopsItsRetry() = runBlocking {
        val requests = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor {
            requests.incrementAndGet()
            throw IOException("offline")
        }.build()
        val job = launch { ControlPlaneClient(http = http).createQrLogin("qq") }
        while (requests.get() == 0) delay(10)
        job.cancel()
        job.join()
        delay(1100)
        assertEquals(1, requests.get())
    }

    @Test fun membershipSummaryShowsTheFullDateWithoutExpiryLabels() {
        val profile = UserProfile(isVip = true, vipName = "豪华绿钻", vipExpireAt = profileEpoch("2099-07-22"))
        assertEquals("豪华绿钻VIP至2099.07.22", vipSummary(profile, true, null))
    }

    @Test fun legacyQualityUsesSqAndRejectsUnexpectedRemovedTierResponses() = runBlocking {
        val bodies = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("u.y.qq.com", request.url.host)
            bodies += Buffer().apply { request.body!!.writeTo(this) }.readUtf8()
            val file = if (bodies.size == 1) "RS01media-mid.flac" else "F000media-mid.flac"
            val json = """{"code":0,"req_0":{"code":0,"data":{"midurlinfo":[{"purl":"$file"}],"sip":["https://dl.stream.qqmusic.qq.com/"],"expiration":3600}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("fixture").code(200)
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val client = ApiClient(org.robolectric.RuntimeEnvironment.getApplication(),
            cookie = { "qqmusic_uin=12345; qm_keyst=test-only" }, transport = http)
        val track = Track("test-mid", "fixture", mediaMid = "media-mid", qualities = listOf("standard", "sq", "hires"))
        val stream = client.stream(track, "hires")
        assertEquals("sq", stream.quality)
        assertTrue(stream.url.contains("F000"))
        assertEquals(2, bodies.size)
        assertTrue(bodies.all { "F000media-mid.flac" in it && "RS01" !in it })
    }
}
