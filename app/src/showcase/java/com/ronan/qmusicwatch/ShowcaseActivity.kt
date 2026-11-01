package com.ronan.qmusicwatch

import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import com.ronan.qmusicwatch.data.DownloadEntity
import com.ronan.qmusicwatch.lyrics.LyricLine
import com.ronan.qmusicwatch.lyrics.LyricWord
import com.ronan.qmusicwatch.model.*
import com.ronan.qmusicwatch.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Offline documentation host, present ONLY in the .showcase application.
 * Actual production screens are rendered with explicit sample data; the
 * showcase manifest removes INTERNET and its storage/Keystore UID is isolated.
 */
class ShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.APPLICATION_ID.endsWith(".showcase"))
        // Coil's asynchronous OkHttp dispatcher treats missing INTERNET as a
        // fatal SecurityException. Reject remote demo artwork before DNS with
        // an ordinary IO failure; keep the manifest's hard offline boundary.
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(this)
            .okHttpClient(okhttp3.OkHttpClient.Builder().addInterceptor {
                throw java.io.IOException("Offline showcase: remote artwork disabled")
            }.build()).build())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        val scene = intent.getStringExtra("scene") ?: "home"
        val graph = application as QMusicApplication
        val artwork = "android.resource://$packageName/${R.mipmap.ic_launcher}"
        val tracks = listOf(
            "星海慢行" to "演示音乐人",
            "窗边的节拍" to "城市音符",
            "口袋里的晚风" to "圆表乐队",
            "沿着光走向更远的地方" to "晨光计划",
            "雨停以后" to "北岸录音室",
            "晚安，旅行者" to "夜间电台",
        ).mapIndexed { i, (title, artist) -> Track("showcase-$i", title, listOf(artist), "演示专辑", artwork) }
        val playlists = listOf(
            MusicCollection("demo-created", "日落随身听", artwork, 6, owned = true),
            MusicCollection("demo-created-2", "通勤节拍", artwork, 4, owned = true),
            MusicCollection("demo-collected", "安静的午后", artwork, 8, owned = false),
        )
        val lyrics = listOf(
            "把轻快的旋律装进手腕" to "Carry a little rhythm along",
            "沿着清晰的节拍慢慢向前" to "Follow the beat at your own pace",
            "让这一段温柔的旋律陪你走过每一个平凡又闪亮的日常" to "Let this quiet melody brighten an ordinary day",
            "抬起手就看见正在播放的那束光" to "A small light is playing on your wrist",
            "下一站仍然有音乐陪伴" to "Music goes on to the next stop",
        ).mapIndexed { i, (original, translation) ->
            val start = i * 30_000L
            LyricLine(start, original, translation, original.mapIndexed { j, char ->
                LyricWord(char.toString(), start + j * 850L, 850L)
            })
        }
        val profile = UserProfile(displayName = "演示用户", avatarUrl = artwork, isVip = false)
        val qr = Base64.encodeToString(assets.open("demo-qr.png").use { it.readBytes() }, Base64.NO_WRAP)
        val demoState = AppUiState(
            sessionLoaded = true, home = HomeData(tracks, playlists),
            library = LibraryData(tracks.take(4), playlists), recent = tracks.reversed(), recentLoaded = true,
            searchTracks = tracks, searchQuery = "星海", currentTrack = tracks.first(), lyrics = lyrics,
            profile = profile, profileLoaded = true, qrImageBase64 = qr,
            qrStatus = "演示码 · 不可登录", qrExpiresAt = System.currentTimeMillis() + 180_000,
        )
        lifecycleScope.launch {
            val audio = withContext(Dispatchers.IO) {
                if (scene == "login") graph.vault.clear()
                else graph.vault.save(SessionTokens(accountId = "10000000", provider = "qq"))
                val file = File(filesDir, "showcase-silence.wav")
                createSilence(file)
                graph.settings.setPureBlack(true)
                graph.settings.setHeadphoneWarning(false)
                graph.settings.setPlaybackSnapshot(Json.encodeToString(PlaybackSnapshot.serializer(), PlaybackSnapshot(
                    track = tracks.first(), queue = tracks, positionMs = 62_000,
                    streamUrl = file.toURI().toString(), streamExpiresAt = Long.MAX_VALUE,
                    ownerAccountId = "10000000",
                )))
                file
            }
            val vm = ViewModelProvider(this@ShowcaseActivity)[AppViewModel::class.java]
            delay(650)
            graph.playback.replaceStream(tracks.first().id, audio.toURI().toString(), tracks.first().title,
                tracks.first().artists.first(), artwork, 62_000, false)
            val downloads = listOf(
                DownloadEntity(tracks[0].id, "10000000", tracks[0].title, "演示音乐人", artwork,
                    audio.absolutePath, "complete", audio.length(), audio.length(), groupName = "日落随身听"),
                DownloadEntity(tracks[1].id, "10000000", tracks[1].title, "城市音符", artwork,
                    File(filesDir, "showcase-queued.wav").absolutePath, "queued_wifi", groupName = "日落随身听"),
            )
            setContent {
                QMusicWatchTheme("compact", true) {
                    val nav = rememberNavController()
                    val mini = remember { MiniPlayerController().apply { if (scene == "home-mini") onListScrollStarted() } }
                    CompositionLocalProvider(LocalMiniPlayerController provides mini) {
                        Box(Modifier.fillMaxSize()) {
                            NavHost(nav, startDestination = "showcase") {
                                composable("showcase") { Scene(scene, nav, demoState, vm, downloads, tracks, playlists) }
                                listOf("home", "search", "recent", "downloads", "player", "queue", "detail", "settings",
                                    "settings/display", "settings/playback", "settings/network", "settings/about", "login").forEach { route ->
                                    composable(route) { Scene(route, nav, demoState, vm, downloads, tracks, playlists) }
                                }
                                composable("library/{section}") { entry ->
                                    Scene("library/${entry.arguments?.getString("section")}", nav, demoState, vm, downloads, tracks, playlists)
                                }
                            }
                            if (scene in setOf("home", "home-mini", "my", "search", "search-results", "recent", "liked", "created", "collected", "detail", "downloads", "queue")) {
                                WatchMiniPlayer(tracks.first().title, "演示音乐人", artwork, false, 62f / 180f, mini,
                                    Modifier.align(Alignment.BottomCenter), onPlayPause = {}, onOpenPlayer = { nav.navigate("player") })
                            }
                            OppoRoundClock(Modifier.align(Alignment.TopCenter))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Scene(
        scene: String, nav: androidx.navigation.NavHostController, state: AppUiState, vm: AppViewModel,
        downloads: List<DownloadEntity>, tracks: List<Track>, playlists: List<MusicCollection>,
    ) {
        val back: () -> Unit = { if (!nav.popBackStack()) finish() }
        when (scene) {
            "home", "home-mini", "my" -> HomeScreen(nav, state, vm, 5, initialPage = if (scene == "my") 1 else 0)
            "login" -> LoginScreen(state, vm, back)
            "search" -> SearchScreen(nav, state.copy(searchTracks = emptyList()), vm, listOf("星海", "晚风"))
            "search-results" -> SearchScreen(nav, state, vm, emptyList(), initialQuery = "星海")
            "liked", "library/liked" -> LibraryScreen(nav, state, vm, LibrarySection.Liked)
            "created", "library/created" -> LibraryScreen(nav, state, vm, LibrarySection.Created)
            "collected", "library/collected" -> LibraryScreen(nav, state, vm, LibrarySection.Collected)
            "recent" -> TrackListScreen("最近播放", state.recent, playlists, vm, back)
            "detail" -> DetailScreen(CollectionDetail("日落随身听", tracks), null, false, null, playlists, vm, back)
            "downloads" -> DownloadScreen(downloads, vm, back)
            "player", "lyrics", "player-menu" -> PlayerScreen(tracks.first(), state.lyrics, vm,
                "sequential", "normal", true, true, 0, "soft", "left", false, true, "standard", "standard", false,
                state.profile, true, playlists, false, openQueue = { nav.navigate("queue") }, onBack = back,
                initialPage = if (scene == "lyrics") 1 else 0)
            "queue" -> QueueScreen(tracks, 0, false, state, vm, back)
            "settings" -> SettingsCenter(nav, back)
            "settings/display" -> DisplaySettingsScreen(vm, "compact", "normal", true, true, 0, "soft", "left", true, false, back)
            "settings/playback" -> PlaybackSettingsScreen(vm, "standard", state.profile, true, false, true, "sequential", 0, true, true, 30, back)
            "settings/network" -> NetworkSettingsScreen(vm, 5, state, { }, { }, back)
            "settings/about" -> AboutScreen(vm, com.ronan.qmusicwatch.network.UpdateUiState.Idle, {}, {}, back)
            "font-selector" -> SettingsSelectionSheet("界面字号", "compact", listOf("compact" to "紧凑", "standard" to "标准", "large" to "大字号"), {}, back)
            "confirm" -> WatchDialog(back, title = { Text("清理演示缓存？") },
                text = { Text("此画面用于展示确认弹窗的实际布局。") },
                confirmButton = { WatchConfirmButton({}) { Text("确认") } },
                dismissButton = { WatchCancelButton({}) { Text("取消") } })
            else -> SettingsCenter(nav, back)
        }
    }

    private fun createSilence(file: File) {
        if (file.exists()) return
        val sampleRate = 8000
        val size = sampleRate * 180 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(size)
        }.array()
        FileOutputStream(file).use { output ->
            output.write(header)
            val buffer = ByteArray(8192)
            var remaining = size
            while (remaining > 0) { val count = minOf(remaining, buffer.size); output.write(buffer, 0, count); remaining -= count }
        }
    }
}
