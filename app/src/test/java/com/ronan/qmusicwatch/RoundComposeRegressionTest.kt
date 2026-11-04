package com.ronan.qmusicwatch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.Alignment
import com.ronan.qmusicwatch.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextClock
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w233dp-h233dp-round-xhdpi", application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoundComposeRegressionTest {
    @get:Rule val compose = createComposeRule()
    private var previewView: View? = null

    @Composable private fun PreviewView() {
        val view = LocalView.current
        SideEffect { previewView = view }
    }

    private fun preview(name: String) {
        val directory = File(requireNotNull(System.getProperty("qmusic.preview.dir")))
        directory.mkdirs()
        compose.runOnIdle {
            val view = requireNotNull(previewView).rootView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    @Test fun scrollingKeepsThePlaylistNameAndBackButtonBelowTheClock() {
        var wentBack = false
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize()) {
                    WatchLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        pinnedTitle = "夜间音乐歌单", onPinnedBack = { wentBack = true }) {
                        item { WatchPageHeader("夜间音乐歌单", { wentBack = true }) }
                        items((1..80).toList()) { n -> WatchListRow("歌曲 $n", "歌手") }
                    }
                    OppoRoundClock(Modifier.align(Alignment.TopCenter))
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        compose.onNodeWithTag("pinned-list-header").assertIsDisplayed()
        assertEquals(20.dp, compose.onNodeWithTag("pinned-list-header").getUnclippedBoundsInRoot().top)
        compose.onNodeWithText("夜间音乐歌单").assertIsDisplayed()
        preview("pinned-playlist-header-round")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    @Test fun queueSearchScrollsAwayWhileTheCompactTitleRemainsAndReturnsAtTop() {
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                WatchLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    pinnedTitle = "播放列表", onPinnedBack = {}) {
                    item { WatchPageHeader("播放列表", {}) }
                    item { WatchSearchField("", {}, "筛选播放列表", Modifier.fillMaxWidth(), heightOverride = 34.dp) }
                    items((1..80).toList()) { n -> WatchListRow("歌曲 $n", "歌手") }
                }
            }
        }
        compose.onNodeWithText("筛选播放列表").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        compose.onNodeWithText("筛选播放列表").assertDoesNotExist()
        compose.onNodeWithText("播放列表").assertIsDisplayed()
        preview("queue-search-collapsed-title-pinned")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("筛选播放列表").assertIsDisplayed()
        compose.onNodeWithTag("pinned-list-header").assertDoesNotExist()
    }

    @Test fun lastSongCanScrollIntoViewAndStillReceiveItsPlaybackClick() {
        var selected = ""
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                WatchLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items((1..20).toList()) { n ->
                        WatchListRow("歌曲 $n", "很长的中文歌手名字", onClick = { selected = "$n" })
                    }
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(19)
        compose.onNodeWithText("歌曲 20").assertIsDisplayed()
        preview("round-list-last-song")
        compose.onNodeWithText("歌曲 20").performClick()
        compose.runOnIdle { assertEquals("20", selected) }
    }

    @Test fun roundConfirmationButtonsKeepBothActionsReachable() {
        var action = ""
        compose.setContent {
            QMusicWatchTheme("large", true) {
                WatchDialog(onDismissRequest = {}, title = { Text("确认操作") },
                    text = { PreviewView(); Text("这是较长的说明文字。".repeat(20)) },
                    confirmButton = { WatchConfirmButton({ action = "confirm" }) { Text("保存") } },
                    dismissButton = { WatchCancelButton({ action = "cancel" }) { Text("取消") } })
            }
        }
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        preview("round-dialog-large-font")
        compose.runOnIdle { assertEquals("cancel", action) }
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("confirm", action) }
    }

    @Test fun searchActionAndTextEditingStillWorkInARoundDialog() {
        var submitted = ""
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                var query by remember { mutableStateOf("") }
                WatchSearchField(query, { query = it }, "搜索音乐", Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    onSearch = { submitted = query })
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("周杰伦")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals("周杰伦", submitted) }
    }

    @Test fun nonOppoFirmwareCanToggleTheFallbackSwitchWithoutVendorClasses() {
        var checked = false
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                var value by remember { mutableStateOf(false) }
                OppoRoundSwitch(value) { value = it; checked = it }
            }
        }
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(checked) }
    }

    @Test fun roundClockUsesIntrinsicWidthAndStaysCentered() {
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                    OppoRoundClock()
                }
            }
        }
        val clock = compose.onNodeWithTag("round-clock").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(clock.right - clock.left < 80.dp)
        assertTrue(kotlin.math.abs((clock.left.value + clock.right.value) / 2f - 116.5f) < 1f)
        assertEquals(0.dp, clock.top)
        preview("round-clock-intrinsic-width")
    }

    @Test fun scrollingCollapsesTheMiniPlayerAndClickExpandsBeforeOpening() {
        val controller = MiniPlayerController()
        var opened = 0
        var playPause = 0
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                CompositionLocalProvider(LocalMiniPlayerController provides controller) {
                    Box(Modifier.fillMaxSize()) {
                        WatchLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                            items((1..30).toList()) { n -> WatchListRow("测试歌曲 $n", "歌手", onClick = {}) }
                        }
                        WatchMiniPlayer("测试歌曲", "已暂停", null, false, .5f, controller,
                            Modifier.align(Alignment.BottomCenter),
                            onPlayPause = { playPause++ }, onOpenPlayer = { opened++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("mini-player-expanded").assertIsDisplayed()
        preview("mini-player-expanded")
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            swipeUp(startY = height * .58f, endY = height * .18f, durationMillis = 400)
        }
        compose.onNodeWithTag("mini-player-collapsed").assertIsDisplayed()
        compose.onNodeWithTag("mini-player-progress", useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(.5f, 0f..1f)))
        preview("mini-player-collapsed")
        compose.onNodeWithTag("mini-player-collapsed").performClick()
        compose.onNodeWithTag("mini-player-expanded").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithContentDescription("播放").performClick()
        compose.runOnIdle { assertEquals(1, playPause); assertEquals(0, opened) }
        compose.onNodeWithTag("mini-player-expanded").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun scalingDoesNotLeaveLargeBlankGapsBetweenRows() {
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                WatchLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items((1..12).toList()) { n -> WatchListRow("间距测试 $n", "歌曲说明", onClick = {}) }
                }
            }
        }
        preview("round-list-tight-spacing")
        val first = compose.onNode(hasTestTag("round-list-item") and hasAnyDescendant(hasText("间距测试 1")), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val second = compose.onNode(hasTestTag("round-list-item") and hasAnyDescendant(hasText("间距测试 2")), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Rows overlap: $first / $second", second.top >= first.bottom)
        assertTrue("Scaled row keeps an oversized blank gap: $first / $second", second.top - first.bottom <= 4.dp)
    }

    @Test fun miniPlayerKeepsOneArtworkMovingContinuouslyToTheCenter() {
        val controller = MiniPlayerController()
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize()) {
                    WatchMiniPlayer("连续移动", "演示", null, false, .5f, controller,
                        Modifier.align(Alignment.BottomCenter), onPlayPause = {}, onOpenPlayer = {})
                }
            }
        }
        fun center() = compose.onNodeWithTag("mini-player-artwork", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().let { (it.left.value + it.right.value) / 2 }
        val start = center()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { controller.onListScrollStarted(); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        repeat(5) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val middle = center()
        assertTrue("Artwork center should move between endpoints: start=$start middle=$middle", middle > start + 2f && middle < 116.5f)
        compose.onAllNodesWithTag("mini-player-artwork", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("mini-player-progress", useUnmergedTree = true).assertCountEquals(1)
        preview("mini-player-morph-intermediate")
        compose.mainClock.advanceTimeBy(1500)
        assertEquals(116.5f, center(), .6f)
        compose.onNodeWithTag("mini-player-collapsed").assertWidthIsEqualTo(44.dp)
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun interruptingMiniPlayerCollapseReversesWithoutReplacingTheArtwork() {
        val controller = MiniPlayerController()
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                WatchMiniPlayer("连续反向", "演示", null, false, .4f, controller,
                    onPlayPause = {}, onOpenPlayer = {})
            }
        }
        fun center() = compose.onNodeWithTag("mini-player-artwork", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().let { (it.left.value + it.right.value) / 2 }
        val start = center()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { controller.onListScrollStarted(); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        repeat(5) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        val before = center()
        compose.runOnIdle { controller.expand(); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(16)
        assertTrue(kotlin.math.abs(center() - before) < 15f)
        compose.mainClock.advanceTimeBy(1500)
        assertEquals(start, center(), .6f)
        compose.onAllNodesWithTag("mini-player-artwork", useUnmergedTree = true).assertCountEquals(1)
        compose.mainClock.autoAdvance = true
    }

    @Test fun clockScrimSeparatesScrollingContentFromTheTime() {
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.TopCenter) { OppoRoundClock() }
            }
        }
        val mask = compose.onNodeWithTag("round-clock-scrim").getUnclippedBoundsInRoot()
        val clock = compose.onNodeWithTag("round-clock").getUnclippedBoundsInRoot()
        assertEquals(233.dp, mask.right - mask.left)
        assertEquals(clock.bottom + 3.dp, mask.bottom)
        preview("round-clock-content-isolation")
        compose.runOnIdle {
            val view = requireNotNull(previewView).rootView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val besideTime = android.graphics.Color.red(bitmap.getPixel((clock.left.value * 2 - 8).toInt(), 24))
            assertTrue("Thin band should dim content with transparency, not opaque black", besideTime in 30..220)
            assertTrue("Content below the compact scrim must remain visible", android.graphics.Color.red(bitmap.getPixel(233, (mask.bottom.value * 2 + 8).toInt())) > 200)
        }
        preview("round-clock-content-isolation")
    }

    @Test fun optionCardsKeepMaximumCapsuleCornersWhenTheirHeightGrows() {
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatchListRow("胶囊选项", "较高的选项也保持胶囊外观",
                        Modifier.width(200.dp).height(96.dp).testTag("capsule-card"), onClick = {})
                }
            }
        }
        val bounds = compose.onNodeWithTag("capsule-card").getUnclippedBoundsInRoot()
        compose.runOnIdle {
            val view = requireNotNull(previewView).rootView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val left = (bounds.left.value * 2).toInt()
            val top = (bounds.top.value * 2).toInt()
            assertTrue("Tall option corners must follow a half-height capsule", android.graphics.Color.red(bitmap.getPixel(left + 16, top + 28)) < 10)
            assertTrue("Capsule side at its center must remain filled", android.graphics.Color.red(bitmap.getPixel(left + 2, top + 96)) > 20)
        }
        preview("option-card-full-capsule")
    }

    @Test fun crownBurstHasBoundedTravelAndTouchScrollingRemainsUsable() {
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        lateinit var crown: CrownScrollController
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                state = androidx.compose.foundation.lazy.rememberLazyListState()
                crown = rememberCrownScroll(state)
                WatchLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = state) {
                    items((1..40).toList()) { n -> WatchListRow("表冠测试 $n", "歌曲", onClick = {}) }
                }
            }
        }
        compose.runOnIdle { repeat(500) { crown.offer(1000f, 1000L + it) } }
        compose.waitForIdle()
        var afterCrown = 0
        compose.runOnIdle {
            afterCrown = state.firstVisibleItemIndex
            assertTrue("Fast crown motion must remain bounded", afterCrown <= 8)
        }
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(durationMillis = 400) }
        compose.runOnIdle { assertTrue("Touch must still scroll after crown input", state.firstVisibleItemIndex > afterCrown) }
        preview("round-crown-bounded-scroll")
    }

    @Test fun firstHeaderScalingAccountsForTheListsTopPadding() {
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                WatchLazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    item { Text("顶部标题", fontSize = 18.sp, modifier = Modifier.testTag("padded-header")) }
                    items((1..8).toList()) { n -> WatchListRow("歌曲 $n", onClick = {}) }
                }
            }
        }
        val header = compose.onNodeWithTag("padded-header").getUnclippedBoundsInRoot()
        assertTrue("Header should not shrink as though it were above the top inset", header.right - header.left > 40.dp)
        preview("round-list-padded-header")
    }

    @Test fun longLyricsWrapAndPlaybackEmphasisSurvivesRepeatedLineChanges() {
        val emphasis = mutableStateOf(false)
        var result: TextLayoutResult? = null
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatchLyricText("这句测试歌词需要分成两行显示", 17f, emphasis.value, Color.White,
                        Modifier.width(190.dp).testTag("lyric"), centered = true,
                        onTextLayout = { result = it })
                }
            }
        }
        fun brightPixels(): Int {
            var count = 0
            compose.runOnIdle {
                val view = requireNotNull(previewView).rootView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    if (android.graphics.Color.red(bitmap.getPixel(x, y)) > 150) count++
                }
            }
            return count
        }
        compose.runOnIdle {
            assertEquals(2, requireNotNull(result).lineCount)
            assertFalse(requireNotNull(result).isLineEllipsized(1))
        }
        val initialSize = requireNotNull(result).size
        val small = brightPixels()
        preview("lyric-two-lines-inactive")
        repeat(3) {
            compose.runOnIdle { emphasis.value = true }
            compose.waitForIdle()
            assertTrue("Active lyric should visibly enlarge on every change", brightPixels() > small * 1.4)
            compose.runOnIdle { assertEquals(initialSize, requireNotNull(result).size) }
            preview("lyric-two-lines-active")
            compose.runOnIdle { emphasis.value = false }
            compose.waitForIdle()
        }
    }

    @Test fun leftAlignedLyricsKeepTheirLeftEdgeWhenPlaybackEmphasisChanges() {
        val emphasis = mutableStateOf(false)
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatchLyricText("测试歌词向右放大", 17f, emphasis.value, Color.White,
                        Modifier.width(190.dp), centered = false)
                }
            }
        }
        fun leftPixel(): Int {
            var left = Int.MAX_VALUE
            compose.runOnIdle {
                val view = requireNotNull(previewView).rootView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    if (android.graphics.Color.red(bitmap.getPixel(x, y)) > 150) left = minOf(left, x)
                }
            }
            return left
        }
        val inactiveLeft = leftPixel()
        preview("lyric-left-aligned-inactive")
        repeat(3) {
            compose.runOnIdle { emphasis.value = true }
            compose.waitForIdle()
            assertTrue("Playback emphasis must keep the left edge fixed", kotlin.math.abs(leftPixel() - inactiveLeft) <= 2)
            preview("lyric-left-aligned-active")
            compose.runOnIdle { emphasis.value = false }
            compose.waitForIdle()
        }
    }

    @Test fun wordHighlightFinishesFirstVisualLineBeforeColoringTheSecond() {
        val progress = mutableFloatStateOf(0f)
        val text = "这句测试歌词需要分成两行显示"
        var result: TextLayoutResult? = null
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatchLyricText(text, 17f, true, Color.Gray, Modifier.width(190.dp),
                        centered = false, renderProgress = progress.floatValue, onTextLayout = { result = it })
                }
            }
        }
        compose.runOnIdle {
            assertEquals(2, requireNotNull(result).lineCount)
            progress.floatValue = requireNotNull(result).getLineEnd(0, visibleEnd = true).toFloat() / text.length
        }
        fun greenPixelsOnLowerLine(): Int {
            var count = 0
            compose.runOnIdle {
                val view = requireNotNull(previewView).rootView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                for (y in bitmap.height / 2 until bitmap.height) for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.green(pixel) > 100 &&
                        android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) * 1.5f) count++
                }
            }
            return count
        }
        compose.waitForIdle()
        assertEquals("Second visual line must not sweep with the first", 0, greenPixelsOnLowerLine())
        preview("lyric-word-timing-first-line")
        compose.runOnIdle { progress.floatValue = 1f }
        compose.waitForIdle()
        assertTrue("Second visual line should highlight after its timestamps", greenPixelsOnLowerLine() > 100)
        preview("lyric-word-timing-both-lines")
    }

    @Test fun longLyricsCanUseThreeLinesAtTheReducedEmphasisSize() {
        var result: TextLayoutResult? = null
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatchLyricText("这是需要分成三行才能完整显示的一句比较长的测试歌词", 17f, true, Color.White,
                        Modifier.width(190.dp), onTextLayout = { result = it })
                }
            }
        }
        compose.runOnIdle {
            assertEquals(3, requireNotNull(result).lineCount)
            assertFalse(requireNotNull(result).isLineEllipsized(2))
        }
        preview("lyric-three-lines-reduced-emphasis")
    }

    @Test fun lyricsBeyondTheViewportKeepAllWrappedLinesAndTheFinalCharacter() {
        val text = "Long lyrics wrap naturally and remain complete. 这是一段很长但不能省略的歌词。".repeat(8) + "终"
        var result: TextLayoutResult? = null
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                    item { WatchLyricText(text, 17f, true, Color.White,
                        Modifier.fillMaxWidth(), onTextLayout = { result = it }) }
                }
            }
        }
        compose.runOnIdle {
            val layout = requireNotNull(result)
            assertTrue(layout.lineCount > 6)
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
            assertFalse(layout.hasVisualOverflow)
            assertTrue((0 until layout.lineCount).none(layout::isLineEllipsized))
        }
    }

    @Test fun wordHighlightAdvancesBetweenPlaybackSamplesWithoutRemeasuringText() {
        compose.mainClock.autoAdvance = false
        var layouts = 0
        val text = "测试文字动画"
        val line = com.ronan.qmusicwatch.lyrics.LyricLine(1000, text,
            words = listOf(com.ronan.qmusicwatch.lyrics.LyricWord(text, 1000, 2000)))
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatchLyricText(text, 17f, true, Color.Gray, Modifier.width(190.dp),
                        renderProgress = 0f, wordTimedLine = line, playbackPositionMs = 1000,
                        playing = true, onTextLayout = { layouts++ })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val initialLayouts = layouts
        fun greenPixels(): Int {
            var count = 0
            compose.runOnIdle {
                val view = requireNotNull(previewView).rootView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.green(pixel) > 100 &&
                        android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) * 1.5f) count++
                }
            }
            return count
        }
        val initialGreen = greenPixels()
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        val middleGreen = greenPixels()
        preview("lyric-frame-progress-soft-edge-250ms")
        compose.mainClock.advanceTimeBy(250)
        compose.waitForIdle()
        val laterGreen = greenPixels()
        preview("lyric-frame-progress-soft-edge-500ms")
        assertTrue("Word highlight must advance without a new playback sample", middleGreen > initialGreen)
        assertTrue("Frame clock must keep the edge moving", laterGreen > middleGreen)
        compose.runOnIdle { assertEquals("Drawing frames must not remeasure lyrics", initialLayouts, layouts) }
    }

    @Test fun completedCharactersKeepTheSameGreenWhenTheLineFinishes() {
        val progress = mutableFloatStateOf(.75f)
        compose.setContent {
            QMusicWatchTheme("compact", true) {
                PreviewView()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WatchLyricText("已填充的颜色保持一致", 17f, true, Color.White.copy(alpha = .55f),
                        Modifier.width(190.dp), renderProgress = progress.floatValue)
                }
            }
        }
        fun bitmap(): Bitmap {
            var result: Bitmap? = null
            compose.runOnIdle {
                val view = requireNotNull(previewView).rootView
                result = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(requireNotNull(result)))
            }
            return requireNotNull(result)
        }
        val before = bitmap()
        var distance = Int.MAX_VALUE
        var bestX = 0
        var bestY = 0
        for (y in 0 until before.height) for (x in 0 until before.width / 2) {
            val pixel = before.getPixel(x, y)
            val delta = kotlin.math.abs(android.graphics.Color.red(pixel) - 51) +
                kotlin.math.abs(android.graphics.Color.green(pixel) - 176) +
                kotlin.math.abs(android.graphics.Color.blue(pixel) - 84)
            if (delta < distance) { distance = delta; bestX = x; bestY = y }
        }
        assertTrue("Filled text must already have the final green opacity", distance < 5)
        preview("lyric-edge-only-before-completion")
        compose.runOnIdle { progress.floatValue = 1f }
        compose.waitForIdle()
        val after = bitmap()
        assertEquals("Completing a line must not brighten its already filled text",
            before.getPixel(bestX, bestY), after.getPixel(bestX, bestY))
        preview("lyric-edge-only-after-completion")
    }
}
