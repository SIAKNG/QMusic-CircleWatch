package com.ronan.qmusicwatch

import android.os.Looper
import android.content.Intent
import android.content.ServiceConnection
import androidx.lifecycle.ViewModelStore
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ronan.qmusicwatch.data.DownloadEntity
import com.ronan.qmusicwatch.model.SessionTokens
import com.ronan.qmusicwatch.model.Track
import com.ronan.qmusicwatch.network.ControlPlaneClient
import com.ronan.qmusicwatch.update.UpdateManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowContextImpl

/** These tests inspect requests and settings, not the Media3 Binder protocol. */
@Implements(className = "android.app.ContextImpl", isInAndroidSdk = false)
class SettingsTestServiceBinding : ShadowContextImpl() {
    @Implementation
    override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean = false

    @Implementation(minSdk = 29)
    override fun bindService(intent: Intent, flags: Int, executor: Executor, connection: ServiceConnection): Boolean = false
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = QMusicApplication::class, shadows = [SettingsTestServiceBinding::class])
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackSettingsRobolectricTest {
    private val app get() = RuntimeEnvironment.getApplication() as QMusicApplication
    private val stores = mutableListOf<ViewModelStore>()
    private val streamRequests = CopyOnWriteArrayList<String>()
    // Deliberately minimal quality metadata: old lists can omit SQ support.
    private val track = Track("00485V8K4InqbZ", "Song", artists = listOf("Artist"), mediaMid = "media-mid")

    private fun setFixtureField(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    @Before fun setUp(): Unit = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        // In-memory synthetic session only; never access AndroidKeyStore or
        // contact QQ Music, the control plane, or any real user's account.
        setFixtureField(app.vault, "cached", SessionTokens(accountId = "test-owner", upstreamCookie = "qqmusic_uin=12345; qm_keyst=test-only"))
        setFixtureField(app.vault, "cacheValid", true)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = request.body?.let { Buffer().apply(it::writeTo).readUtf8() }.orEmpty()
            val isStream = body.contains("CgiGetVkey") || body.contains("UrlGetVkey")
            if (isStream) streamRequests += body
            val data = if (isStream) {
                """{"midurlinfo":[{"purl":"F000media-mid.flac","filename":"F000media-mid.flac"}],"sip":["https://dl.stream.qqmusic.qq.com/"],"expiration":3600}"""
            } else """{"songlist":[],"lyric":"","trans":""}"""
            val payload = if (request.url.host == "heyboxlite.xyz") {
                """{"ok":false,"error":{"code":"TEST_STUB","message":"test only"}}"""
            } else """{"code":0,"req_0":{"code":0,"data":$data}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (request.url.host == "heyboxlite.xyz") 503 else 200).message("Test fixture")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        setFixtureField(app.api, "http", http)
        app.controlPlane = ControlPlaneClient(http = http)
        app.updates = UpdateManager(app, app.controlPlane)
        app.settings.setQuality("sq")
        app.settings.setWifiOnlyDownload(false)
        app.settings.setHeadphoneWarning(false)
        app.settings.setPlaybackSnapshot("")
    }

    @After fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        app.playback.release()
        WorkManager.getInstance(app).cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
        app.db.close()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun awaitCondition(condition: suspend () -> Boolean) = withTimeout(5_000) {
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            delay(10)
        }
    }

    private suspend fun viewModel(expectedQuality: String = "sq"): AppViewModel {
        val vm = AppViewModel(app)
        stores += ViewModelStore().also { it.put("settings-test", vm) }
        awaitCondition { vm.state.value.sessionLoaded && vm.settings.value.quality == expectedQuality }
        return vm
    }

    private fun work(id: String = track.id) = WorkManager.getInstance(app)
        .getWorkInfosForUniqueWork("download-test-owner-$id").get().first { !it.state.isFinished }

    @Test fun loadingSurvivesADeferredControllerAndClearsWhenCancelled() {
        app.playback.replaceStream(track.id, "https://dl.stream.qqmusic.qq.com/M500test.mp3", track.title, "Artist", "", 0, true)
        assertTrue(app.playback.buffering.value)
        app.playback.stopForTrackChange()
        assertFalse(app.playback.buffering.value)
        app.playback.expectPlaybackStart("next-song")
        assertTrue(app.playback.buffering.value)
        app.playback.pause()
        assertFalse(app.playback.buffering.value)
    }

    @Test fun savedDefaultsRemainCurrentWithoutIndividualUiCollectors() = runBlocking {
        val vm = viewModel()
        awaitCondition { vm.quality.value == "sq" && !vm.wifiOnlyDownload.value }
        vm.setQuality("hq")
        val changed = vm.setWifiOnlyDownload(true)
        awaitCondition { changed.isCompleted }
        changed.join()
        awaitCondition { vm.settings.value.quality == "hq" && vm.quality.value == "hq" && vm.wifiOnlyDownload.value }
    }

    @Test fun playbackRequestsPersistedSqWithoutAQualityCollectorOrCompleteTrackMetadata() = runBlocking {
        val vm = viewModel()
        vm.requestPlay(track, allowSpeaker = true)
        awaitCondition { vm.state.value.currentTrack?.id == track.id }
        assertTrue(streamRequests.isNotEmpty())
        assertTrue("First stream request must use SQ", streamRequests.first().contains("F000media-mid.flac"))
        assertEquals("sq", vm.state.value.activeStreamQuality)
    }

    @Test fun pendingSelectionLoadsImmediatelyAndLatePreviousSongCannotReplaceIt() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = request.body?.let { Buffer().apply(it::writeTo).readUtf8() }.orEmpty()
            if (body.contains("F000media-mid.flac")) {
                entered.countDown()
                check(release.await(8, java.util.concurrent.TimeUnit.SECONDS))
            }
            val file = if (body.contains("next-mid")) "F000next-mid.flac" else "F000media-mid.flac"
            val data = """{"code":0,"req_0":{"code":0,"data":{"midurlinfo":[{"purl":"$file"}],"sip":["https://dl.stream.qqmusic.qq.com/"]}}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(data.toResponseBody("application/json".toMediaType())).build()
        }.build()
        setFixtureField(app.api, "http", http)
        val vm = viewModel()
        try {
            vm.requestPlay(track, allowSpeaker = true)
            awaitCondition { entered.count == 0L }
            assertEquals(track.id, vm.state.value.pendingPlaybackTrack?.id)
            assertTrue(vm.state.value.playbackLoading)
            val next = track.copy(id = "next-song", mediaMid = "next-mid")
            vm.requestPlay(next, allowSpeaker = true)
            awaitCondition { vm.state.value.currentTrack?.id == next.id }
            release.countDown()
            shadowOf(Looper.getMainLooper()).idle()
            delay(80)
            assertEquals(next.id, vm.state.value.currentTrack?.id)
            assertNull(vm.state.value.pendingPlaybackTrack)
            assertEquals("sq", vm.settings.value.quality)
        } finally {
            release.countDown()
        }
    }

    @Test fun manualNextAndPreviousRetryTheDefaultAfterThisSongFallsBackOnNetworkFailure() = runBlocking {
        var failPreferred = true
        val next = track.copy(id = "next-song", mediaMid = "next-mid")
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = request.body?.let { Buffer().apply(it::writeTo).readUtf8() }.orEmpty()
            val isStream = body.contains("CgiGetVkey") || body.contains("UrlGetVkey")
            if (isStream) {
                streamRequests += body
                if (failPreferred && body.contains("F000media-mid.flac")) throw java.io.IOException("temporary network failure")
            }
            val file = Regex("(?:F000|M800|M500)[A-Za-z0-9-]+\\.(?:flac|mp3)").find(body)?.value ?: "F000media-mid.flac"
            val data = if (isStream) """{"midurlinfo":[{"purl":"$file"}],"sip":["https://dl.stream.qqmusic.qq.com/"]}""" else """{"songlist":[],"lyric":"","trans":""}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("""{"code":0,"req_0":{"code":0,"data":$data}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        setFixtureField(app.api, "http", http)
        val vm = viewModel()
        vm.requestPlay(track, allowSpeaker = true, sourceQueue = listOf(track, next))
        awaitCondition { vm.state.value.currentTrack?.id == track.id && vm.state.value.activeStreamQuality == "hq" }
        assertEquals("sq", vm.settings.value.quality)
        assertTrue(vm.state.value.message.orEmpty().contains("临时切换"))
        failPreferred = false
        streamRequests.clear()
        vm.skipNext()
        awaitCondition { vm.state.value.currentTrack?.id == next.id }
        assertEquals("sq", vm.state.value.activeStreamQuality)
        assertTrue(streamRequests.first().contains("F000next-mid.flac"))
        streamRequests.clear()
        vm.skipPrevious()
        awaitCondition { vm.state.value.currentTrack?.id == track.id }
        assertEquals("sq", vm.state.value.activeStreamQuality)
        assertTrue("Previous must retry SQ, rather than reuse its earlier network fallback", streamRequests.any { it.contains("F000media-mid.flac") })
        assertEquals("sq", vm.settings.value.quality)
    }

    @Test fun automaticForwardAndReverseQueueTransitionsReadDefaultInsteadOfSnapshotQuality() = runBlocking {
        val controller = org.robolectric.Robolectric.buildService(com.ronan.qmusicwatch.playback.PlaybackService::class.java).create()
        val service = controller.get()
        val field = service.javaClass.getDeclaredField("player").apply { isAccessible = true }
        val original = field.get(service)
        var current = androidx.media3.common.MediaItem.Builder().setMediaId(track.id).setUri("https://dl.stream.qqmusic.qq.com/M800media-mid.mp3").build()
        val proxy = java.lang.reflect.Proxy.newProxyInstance(androidx.media3.exoplayer.ExoPlayer::class.java.classLoader,
            arrayOf(androidx.media3.exoplayer.ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> current
                "setMediaItem" -> { current = args!![0] as androidx.media3.common.MediaItem; null }
                "getPlaybackState" -> androidx.media3.common.Player.STATE_READY
                else -> when (method.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Long.TYPE -> 0L; java.lang.Float.TYPE -> 0f; else -> null }
            }
        }
        field.set(service, proxy)
        val skip = service.javaClass.getDeclaredMethod("skipFromSnapshot", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, kotlin.coroutines.Continuation::class.java).apply { isAccessible = true }
        val next = track.copy(id = "next-song", mediaMid = "next-mid")
        val previous = track.copy(id = "previous-song", mediaMid = "previous-mid")
        val codec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        try {
            for (reverse in listOf(false, true)) {
                val queue = if (reverse) listOf(next, track, previous) else listOf(previous, track, next)
                val expected = if (reverse) previous else next
                current = androidx.media3.common.MediaItem.Builder().setMediaId(track.id).setUri("https://dl.stream.qqmusic.qq.com/M800media-mid.mp3").build()
                app.api.invalidateStream(expected.id)
                app.settings.setPlaybackSnapshot(codec.encodeToString(com.ronan.qmusicwatch.model.PlaybackSnapshot.serializer(),
                    com.ronan.qmusicwatch.model.PlaybackSnapshot(track, queue, 25_000, reverse, current.localConfiguration!!.uri.toString(), Long.MAX_VALUE, "hq", "test-owner")))
                streamRequests.clear()
                val transition = async(Dispatchers.Default) {
                    kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Boolean> { continuation -> skip.invoke(service, 1, true, continuation) }
                }
                awaitCondition { transition.isCompleted }
                assertTrue(transition.await())
                assertEquals(expected.id, current.mediaId)
                assertTrue(streamRequests.first().contains("F000${expected.mediaMid}.flac"))
                val saved = codec.decodeFromString(com.ronan.qmusicwatch.model.PlaybackSnapshot.serializer(), app.settings.playbackSnapshot.first())
                assertEquals("sq", saved.quality)
                assertEquals(0L, saved.positionMs)
                assertEquals("sq", app.settings.quality.first())
            }
        } finally { field.set(service, original); controller.destroy() }
    }

    @Test fun changingTheDefaultAlsoRequestsSqForTheCurrentMinimalMetadataTrack() = runBlocking {
        app.settings.setQuality("standard")
        val vm = viewModel("standard")
        vm.requestPlay(track, allowSpeaker = true)
        awaitCondition { vm.state.value.currentTrack?.id == track.id }
        assertTrue(streamRequests.first().contains("M500media-mid.mp3"))

        val initialRequests = streamRequests.size
        vm.setQuality("sq")
        awaitCondition { streamRequests.size > initialRequests && vm.quality.value == "sq" }
        assertTrue(streamRequests.last().contains("F000media-mid.flac"))
    }

    @Test fun existingStandardCacheIsPlayedAndReportedAtItsActualQuality() = runBlocking {
        val audio = File(app.filesDir, "test-offline.audio").apply { writeBytes(byteArrayOf(0, 1)) }
        app.db.downloads().upsert(DownloadEntity(
            track.id, "test-owner", track.title, "Artist", "", audio.absolutePath,
            "complete", audio.length(), audio.length(), quality = "standard",
        ))
        val vm = viewModel()
        vm.requestPlay(track, allowSpeaker = true)
        awaitCondition { vm.state.value.currentTrack?.id == track.id }
        assertEquals("standard", vm.state.value.activeStreamQuality)
        assertTrue(streamRequests.isEmpty())
    }

    @Test fun cachingAndRetryUseMobilePolicyAndKeepTheRequestedQuality() = runBlocking {
        val vm = viewModel()
        vm.cache(track)
        awaitCondition { app.db.downloads().find(track.id, "test-owner") != null && vm.state.value.message == "已加入离线缓存" }
        assertEquals(NetworkType.CONNECTED, work().constraints.requiredNetworkType)
        assertEquals("sq", app.db.downloads().find(track.id, "test-owner")!!.quality)

        val row = app.db.downloads().find(track.id, "test-owner")!!
        app.db.downloads().progress(track.id, "test-owner", "paused", 0, -1)
        app.settings.setQuality("standard")
        vm.resumeDownload(row.copy(status = "paused"))
        awaitCondition { app.db.downloads().find(track.id, "test-owner")!!.status == "queued" }
        assertEquals(NetworkType.CONNECTED, work().constraints.requiredNetworkType)
        assertEquals("sq", app.db.downloads().find(track.id, "test-owner")!!.quality)
    }

    @Test fun disablingWifiInSettingsReleasesAnAlreadyWaitingTask() = runBlocking {
        app.settings.setWifiOnlyDownload(true)
        val vm = viewModel()
        app.downloads.enqueue(track, "test-owner", "sq", true, "Liked")
        val id = work().id
        val changed = vm.setWifiOnlyDownload(false)
        awaitCondition { changed.isCompleted }
        changed.join()
        assertEquals(id, work().id)
        assertEquals(NetworkType.CONNECTED, work().constraints.requiredNetworkType)
        assertEquals("queued", app.db.downloads().find(track.id, "test-owner")!!.status)
    }

    @Test fun startupRepairsOldWifiConstraintsWhenTheSavedSwitchIsAlreadyOff() = runBlocking {
        app.downloads.enqueue(track, "test-owner", "sq", true, "Liked")
        val id = work().id
        viewModel()
        awaitCondition { app.db.downloads().find(track.id, "test-owner")!!.status == "queued" }
        assertEquals(id, work().id)
        assertEquals(NetworkType.CONNECTED, work().constraints.requiredNetworkType)
    }
}
