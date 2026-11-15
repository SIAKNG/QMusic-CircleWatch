package com.ronan.qmusicwatch

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ronan.qmusicwatch.network.ApiClient
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ApiReadProgressTest {
    private fun response(request: Request, data: JsonObject) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body(data.toString().toResponseBody("application/json".toMediaType())).build()
    private fun wrap(data: JsonObject) = buildJsonObject {
        putJsonObject("req_0") { put("code", 0); put("data", data) }
    }
    private fun payload(request: Request) = request.body!!.let { body ->
        val buffer = Buffer(); body.writeTo(buffer); Json.parseToJsonElement(buffer.readUtf8()).jsonObject["req_0"]!!.jsonObject
    }
    private fun client(http: OkHttpClient) = ApiClient(ApplicationProvider.getApplicationContext(),
        cookie = { "qqmusic_uin=12345; qm_keyst=fake-key; euin=fake-euin" }, transport = http)

    @Test fun playlistDirectoryIsPublishedWhileLikedSongsAreStillPending() = runBlocking {
        val release = CountDownLatch(1)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); val part = payload(request)
            val method = part["method"]!!.jsonPrimitive.content
            val data = when (method) {
                "GetPlaylistByUin" -> Json.parseToJsonElement("""{"v_playlist":[{"dirId":202,"dirName":"测试歌单","tid":9988}]}""").jsonObject
                "CgiGetPlaylistFavInfo" -> buildJsonObject { put("v_list", JsonArray(emptyList())) }
                "CgiGetDiss" -> {
                    check(release.await(5, TimeUnit.SECONDS))
                    buildJsonObject { put("songlist", JsonArray(emptyList())); put("total_song_num", 0) }
                }
                else -> error("Unexpected method")
            }
            response(request, wrap(data))
        }.build()
        try {
            val directory = CompletableDeferred<Int>()
            val read = async { client(http).library(onPlaylists = { owned, _ -> if (owned != null) directory.complete(owned.size) }) }
            assertEquals(1, withTimeout(3_000) { directory.await() })
            assertFalse(read.isCompleted)
            release.countDown()
            val result = withTimeout(3_000) { read.await() }
            assertEquals("测试歌单", result.playlists.single().title)
            assertTrue(result.liked.isEmpty())
        } finally { release.countDown(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }

    @Test fun identityArrivesBeforeMembershipWithoutLosingTheFinalRights() = runBlocking {
        val release = CountDownLatch(1)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.method == "GET") response(request, buildJsonObject {})
            else {
                val method = payload(request)["method"]!!.jsonPrimitive.content
                val data = when (method) {
                    "GetLoginUserInfo", "GetUserInfo" -> Json.parseToJsonElement("""{"info":{"nick":"测试用户"}}""").jsonObject
                    "vip_login_base" -> {
                        check(release.await(5, TimeUnit.SECONDS))
                        Json.parseToJsonElement("""{"superVip":{"isSVip":1,"vipEndTime":2000000000}}""").jsonObject
                    }
                    else -> error("Unexpected method")
                }
                response(request, wrap(data))
            }
        }.build()
        try {
            val identity = CompletableDeferred<String>()
            val read = async { client(http).profile { identity.complete(it.displayName) } }
            assertEquals("测试用户", withTimeout(3_000) { identity.await() })
            assertFalse(read.isCompleted)
            release.countDown()
            val result = withTimeout(3_000) { read.await() }
            assertEquals("测试用户", result.displayName); assertEquals(true, result.isVip)
        } finally { release.countDown(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }

    @Test fun favoritePagesUseRawOffsetsAndKeepDistinctCollectionsWithSharedDirectoryIds() = runBlocking {
        val offsets = mutableListOf<Int>()
        val result = com.ronan.qmusicwatch.network.collectFavoritePlaylistPages { offset, _ ->
            offsets += offset
            val payload = when (offset) {
                0 -> """{"total":5,"v_list":[{"tid":"1001","dirId":202,"dissname":"第一张"},{"tid":"1001","dirId":202,"dissname":"重复"},{"tid":"201","dissname":"我喜欢"}]}"""
                3 -> """{"total":5,"v_list":[{"tid":"1002","dirId":202,"dissname":"第二张"},{"tid":"1003","dirId":202,"dissname":"第三张"}]}"""
                else -> error("Unexpected offset $offset")
            }
            Json.parseToJsonElement(payload).jsonObject
        }
        assertEquals(listOf(0, 3), offsets)
        assertEquals(listOf("1001", "1002", "1003"), result.map { it.id })
    }

    @Test fun wechatLibraryUsesAccountPlaylistAndTotalFromTheOfficialEnvelope() = runBlocking {
        val offsets = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.method == "GET") {
                assertEquals("0", request.url.queryParameter("userid"))
                return@addInterceptor response(request, Json.parseToJsonElement(
                    """{"code":0,"data":{"mymusic":[{"type":1,"id":123456,"num0":3}]}}""",
                ).jsonObject)
            }
            val part = payload(request)
            when (part["method"]!!.jsonPrimitive.content) {
                "GetPlaylistByUin" -> response(request, wrap(buildJsonObject { put("v_playlist", JsonArray(emptyList())) }))
                "CgiGetPlaylistFavInfo" -> response(request, wrap(buildJsonObject { put("v_list", JsonArray(emptyList())) }))
                "uniform_get_Dissinfo" -> {
                    val param = part["param"]!!.jsonObject
                    assertEquals(123456L, param["disstid"]!!.jsonPrimitive.long)
                    assertEquals(1, param["ctx"]!!.jsonPrimitive.int)
                    val offset = param["song_begin"]!!.jsonPrimitive.int
                    offsets += offset
                    val data = Json.parseToJsonElement(
                        """{"req_0":{"code":0,"total_song_num":3,"data":{"songlist":[{"mid":"testSong${offset}","name":"Song ${offset}","singer":[],"album":{}}]}}}""",
                    ).jsonObject
                    response(request, data)
                }
                else -> error("Unexpected library method")
            }
        }.build()
        try {
            val client = ApiClient(ApplicationProvider.getApplicationContext(),
                cookie = { "wxuin=old; qqmusic_uin=12345; qqmusic_key=key; euin=fake; tmeLoginType=1" },
                transport = http)
            val result = client.library()
            assertEquals(listOf(0, 1, 2), offsets.toList())
            assertEquals(listOf("Song 0", "Song 1", "Song 2"), result.liked.map { it.title })
        } finally { http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }

    @Test fun repeatedFavoritePageCannotBeSavedAsACompleteLibrary() = runBlocking {
        val repeated = Json.parseToJsonElement(
            """{"total":5,"v_list":[{"tid":"1001","dissname":"收藏歌单"}]}""",
        ).jsonObject
        val result = runCatching { com.ronan.qmusicwatch.network.collectFavoritePlaylistPages { _, _ -> repeated } }
        assertTrue(result.exceptionOrNull() is com.ronan.qmusicwatch.network.QqResponseException)
    }
}
