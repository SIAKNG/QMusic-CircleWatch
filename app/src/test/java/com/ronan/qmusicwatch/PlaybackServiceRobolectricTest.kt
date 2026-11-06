package com.ronan.qmusicwatch

import androidx.media3.common.C
import androidx.media3.session.MediaSession
import com.ronan.qmusicwatch.playback.BACKGROUND_RECOVERY_ATTEMPTS
import com.ronan.qmusicwatch.playback.BACKGROUND_RECOVERY_DELAY_MS
import com.ronan.qmusicwatch.playback.BACKGROUND_SNAPSHOT_INTERVAL_MS
import com.ronan.qmusicwatch.playback.BACKGROUND_PLAYBACK_WAKE_MODE
import com.ronan.qmusicwatch.playback.BACKGROUND_AUTO_SKIP_ATTEMPTS
import com.ronan.qmusicwatch.playback.BACKGROUND_AUTO_SKIP_RETRY_DELAY_MS
import com.ronan.qmusicwatch.playback.BACKGROUND_TRANSITION_WAKE_LOCK_MS
import com.ronan.qmusicwatch.playback.PlaybackRecoveryRequest
import com.ronan.qmusicwatch.playback.PlaybackRecoveryTracker
import com.ronan.qmusicwatch.playback.PlaybackService
import com.ronan.qmusicwatch.playback.mergePlaybackProgressSnapshot
import com.ronan.qmusicwatch.playback.mergeRecoveredPlaybackSnapshot
import com.ronan.qmusicwatch.model.PlaybackSnapshot
import com.ronan.qmusicwatch.model.StreamData
import com.ronan.qmusicwatch.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackServiceRobolectricTest {
    @Test fun mediaServiceIsDiscoverableWithoutAnUndeclaredBindingPermission() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val component = android.content.ComponentName(context, PlaybackService::class.java)
        val service = context.packageManager.getServiceInfo(component, 0)
        assertTrue(service.exported)
        assertNull(service.permission)
        val services = context.packageManager.queryIntentServices(
            android.content.Intent("android.media.browse.MediaBrowserService").setPackage(context.packageName), 0)
        assertTrue(services.any { it.serviceInfo.name == PlaybackService::class.java.name })
        val music = context.packageManager.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_APP_MUSIC).setPackage(context.packageName), 0)
        assertTrue(music.any { it.activityInfo.name == MainActivity::class.java.name })
    }
    @Test fun backgroundPlaybackKeepsCpuAndNetworkAwake() {
        assertEquals(C.WAKE_MODE_NETWORK, BACKGROUND_PLAYBACK_WAKE_MODE)
        assertTrue(BACKGROUND_SNAPSHOT_INTERVAL_MS in 5_000L..30_000L)
        assertTrue(BACKGROUND_RECOVERY_DELAY_MS in 500L..5_000L)
        assertTrue(BACKGROUND_RECOVERY_ATTEMPTS >= 3)
        assertTrue(BACKGROUND_AUTO_SKIP_ATTEMPTS >= 2)
        assertTrue(BACKGROUND_AUTO_SKIP_RETRY_DELAY_MS in 250L..5_000L)
        assertTrue(BACKGROUND_TRANSITION_WAKE_LOCK_MS in 10_000L..120_000L)
    }

    @Test fun consecutiveFailuresCannotBeClearedByTheOlderRecovery() {
        val tracker = PlaybackRecoveryTracker()
        val first = tracker.replace(
            PlaybackRecoveryRequest(
                mediaId = "track-a",
                failedUri = "https://heyboxlite.xyz/media/old",
                positionMs = 1_000,
                playWhenReady = true,
            ),
        )
        val second = tracker.replace(
            PlaybackRecoveryRequest(
                mediaId = "track-a",
                failedUri = "https://heyboxlite.xyz/media/new",
                positionMs = 2_000,
                playWhenReady = true,
            ),
        )

        assertFalse(tracker.clearIfCurrent(first))
        assertEquals(second, tracker.current())
        assertTrue(tracker.clearIfCurrent(second))
        assertNull(tracker.current())
    }

    @Test fun recoveredTransitionStaysValidWhenMedia3DeliversItAsynchronously() {
        val tracker = PlaybackRecoveryTracker()
        val request = tracker.replace(
            PlaybackRecoveryRequest(
                mediaId = "track-a",
                failedUri = "https://heyboxlite.xyz/media/expired",
                positionMs = 1_000,
                playWhenReady = true,
            ),
        )
        val refreshed = "https://heyboxlite.xyz/media/refreshed"

        assertTrue(tracker.expectReplacement(request, refreshed))
        assertNull(
            tracker.invalidateUnlessMatches(
                mediaId = request.mediaId,
                uri = refreshed,
                applyingGeneration = null,
            ),
        )
        assertTrue(tracker.isCurrent(request))
    }

    @Test fun switchingTracksInvalidatesAnInFlightRecoveryAndRejectsItsSnapshot() {
        val tracker = PlaybackRecoveryTracker()
        val request = tracker.replace(
            PlaybackRecoveryRequest(
                mediaId = "track-a",
                failedUri = "https://heyboxlite.xyz/media/a",
                positionMs = 4_000,
                playWhenReady = true,
            ),
        )
        val invalidated = tracker.invalidateUnlessMatches(
            mediaId = "track-b",
            uri = "https://heyboxlite.xyz/media/b",
            applyingGeneration = null,
        )
        val latest = PlaybackSnapshot(
            track = Track("track-b", "B"),
            queue = listOf(Track("track-b", "B")),
            ownerAccountId = "owner",
        )

        assertEquals(request, invalidated)
        assertFalse(tracker.isCurrent(request))
        assertNull(
            mergeRecoveredPlaybackSnapshot(
                latest,
                "owner",
                request,
                StreamData("https://heyboxlite.xyz/media/refreshed-a", "hq", 99_000),
            ),
        )
    }

    @Test fun recoveryAndProgressMergesPreserveTheLatestQueueState() {
        val current = Track("track-a", "A")
        val latestQueue = listOf(current, Track("track-c", "C"), Track("track-b", "B"))
        val snapshot = PlaybackSnapshot(
            track = current,
            queue = latestQueue,
            positionMs = 100,
            queueReversed = true,
            streamUrl = "https://heyboxlite.xyz/media/expired",
            streamExpiresAt = 1,
            quality = "standard",
            ownerAccountId = "owner",
        )
        val request = PlaybackRecoveryRequest(
            generation = 7,
            mediaId = current.id,
            failedUri = snapshot.streamUrl,
            positionMs = 7_000,
            playWhenReady = true,
        )

        val recovered = mergeRecoveredPlaybackSnapshot(
            snapshot,
            "owner",
            request,
            StreamData("https://heyboxlite.xyz/media/refreshed", "hq", 99_000),
        )!!
        val progressed = mergePlaybackProgressSnapshot(
            recovered,
            "owner",
            current.id,
            recovered.streamUrl,
            8_000,
        )!!

        assertEquals(latestQueue, recovered.queue)
        assertTrue(recovered.queueReversed)
        assertEquals(7_000, recovered.positionMs)
        assertEquals("hq", recovered.quality)
        assertEquals(latestQueue, progressed.queue)
        assertEquals(8_000, progressed.positionMs)
    }

    @Test fun aNewRemoteUriIsNotPersistedWithThePreviousUrisExpiry() {
        val track = Track("track-a", "A")
        val expired = "https://isure.stream.qqmusic.qq.com/M500expired.mp3?vkey=old"
        val refreshed = "https://isure.stream.qqmusic.qq.com/M500refreshed.mp3?vkey=new"
        val snapshot = PlaybackSnapshot(
            track = track,
            queue = listOf(track),
            streamUrl = expired,
            streamExpiresAt = 50_000,
            ownerAccountId = "owner",
        )

        val changed = mergePlaybackProgressSnapshot(
            snapshot = snapshot,
            accountId = "owner",
            mediaId = track.id,
            uri = refreshed,
            positionMs = 2_000,
        )!!

        assertEquals(refreshed, changed.streamUrl)
        assertEquals(0L, changed.streamExpiresAt)
    }

    @Test fun mediaSessionPublishesStandardLegacySkipActionsAndBrowsableRoot() {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        val service = controller.get()
        try {
            val field = PlaybackService::class.java.getDeclaredField("session").apply { isAccessible = true }
            val session = field.get(service) as MediaSession
            assertTrue(session is androidx.media3.session.MediaLibraryService.MediaLibrarySession)
            assertTrue(session.player.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT))
            assertTrue(session.player.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS))
            // Robolectric does not bridge platform controller tokens. Inspect the
            // actual legacy state generated by the service's Media3 session.
            val impl = MediaSession::class.java.getDeclaredField("impl").apply { isAccessible = true }.get(session)
            val implType = Class.forName("androidx.media3.session.MediaSessionImpl")
            val stub = implType.getDeclaredField("sessionLegacyStub").apply { isAccessible = true }.get(impl)
            val wrapper = implType.getDeclaredField("playerWrapper").apply { isAccessible = true }.get(impl)
            val state = stub.javaClass.getDeclaredMethod("createPlaybackStateCompat", wrapper.javaClass)
                .apply { isAccessible = true }.invoke(stub, wrapper)
            val actions = state.javaClass.getMethod("getActions").invoke(state) as Long
            assertTrue(actions and android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)
            assertTrue(actions and android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
            val future = androidx.media3.session.MediaBrowser.Builder(service, session.token).buildAsync()
            repeat(100) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                if (!future.isDone) Thread.sleep(5)
            }
            val browser = future.get(2, java.util.concurrent.TimeUnit.SECONDS)
            try {
                assertTrue(browser.availableSessionCommands.contains(
                    androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                val root = browser.getLibraryRoot(null)
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals("qmusic.root", root.get(2, java.util.concurrent.TimeUnit.SECONDS).value?.mediaId)
            } finally { browser.release() }
        } finally {
            controller.destroy()
        }
    }

}
