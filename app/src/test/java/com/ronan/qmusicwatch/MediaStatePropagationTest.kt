package com.ronan.qmusicwatch

import android.app.Application
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ronan.qmusicwatch.playback.SystemMediaPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MediaStatePropagationTest {
    private class Engine : SimpleBasePlayer(Looper.getMainLooper()) {
        var stateValue = State.Builder().setAvailableCommands(Player.Commands.Builder().addAllCommands().build()).build()
        override fun getState() = stateValue
        fun ready(position: Long) {
            stateValue = stateValue.buildUpon().setPlaylist(listOf(MediaItemData.Builder("song")
                .setMediaItem(MediaItem.Builder().setMediaId("song").build())
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Actual song").build())
                .setDurationUs(180_000_000L).build()))
                .setPlaybackState(Player.STATE_READY).setContentPositionMs(position)
                .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            invalidateState()
        }
        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            stateValue = stateValue.buildUpon()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }
    private fun drain() {
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(2) }
    }
    @Test fun controllerTracksActualPlaybackPositionMetadataAndPauseResume() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val engine = Engine()
        val session = MediaSession.Builder(context, SystemMediaPlayer(engine) {}).build()
        var controller: MediaController? = null
        try {
            val future = MediaController.Builder(context, session.token).buildAsync()
            drain()
            controller = future.get(2, TimeUnit.SECONDS)
            assertFalse(controller.isPlaying)
            engine.ready(1_200)
            drain()
            assertTrue(controller.isPlaying)
            assertEquals(180_000L, controller.duration)
            assertTrue(controller.currentPosition >= 1_200L)
            assertEquals("Actual song", controller.mediaMetadata.title.toString())
            engine.ready(8_000)
            drain()
            assertTrue(controller.currentPosition >= 8_000L)
            controller.pause(); drain()
            assertFalse(controller.isPlaying)
            assertFalse(controller.playWhenReady)
            controller.play(); drain()
            assertTrue(controller.isPlaying)
            assertTrue(controller.playWhenReady)
        } finally {
            controller?.release(); session.release(); engine.release(); drain()
        }
    }
}
