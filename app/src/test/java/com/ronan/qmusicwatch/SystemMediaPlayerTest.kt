package com.ronan.qmusicwatch

import androidx.media3.common.Player
import com.ronan.qmusicwatch.playback.SystemMediaPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SystemMediaPlayerTest {
    private val listeners = mutableListOf<Player.Listener>()
    private val delegated = mutableListOf<String>()
    private fun engine(): Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
        when (method.name) {
            "getAvailableCommands" -> Player.Commands.EMPTY
            "isCommandAvailable" -> false
            "addListener" -> { listeners += args!![0] as Player.Listener; null }
            "removeListener" -> { listeners.remove(args!![0] as Player.Listener); null }
            "getApplicationLooper" -> android.os.Looper.getMainLooper()
            else -> { delegated += method.name; null }
        }
    } as Player

    @Test fun standardSkipCommandsUseTheApplicationQueueInsteadOfTheSingleStreamTimeline() {
        val skips = mutableListOf<Int>()
        val player = SystemMediaPlayer(engine()) { skips += it }
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        player.seekToNext(); player.seekToNextMediaItem(); player.seekToPrevious(); player.seekToPreviousMediaItem()
        assertEquals(listOf(1, 1, -1, -1), skips)
        assertFalse(delegated.any { it.startsWith("seekTo") })
        player.play(); player.pause()
        assertTrue(delegated.containsAll(listOf("play", "pause")))
    }
    @Test fun anEngineAvailabilityChangeCannotDisableExternalSkipButtons() {
        val player = SystemMediaPlayer(engine()) {}
        var available = Player.Commands.EMPTY
        val listener = object : Player.Listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) { available = availableCommands }
        }
        player.addListener(listener)
        listeners.single().onAvailableCommandsChanged(Player.Commands.EMPTY)
        assertTrue(available.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(available.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        player.removeListener(listener)
        assertTrue(listeners.isEmpty())
    }

    @Test fun enginePlaybackAndTimelineEventsReachTheSessionListener() {
        val player = SystemMediaPlayer(engine()) {}
        val seen = mutableListOf<String>()
        var eventPlayer: Player? = null
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { eventPlayer = player; seen += "events" }
            override fun onPlaybackStateChanged(state: Int) { seen += "state:$state" }
            override fun onIsPlayingChanged(playing: Boolean) { seen += "playing:$playing" }
            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) { seen += "ready:$ready" }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { seen += "timeline" }
            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) { seen += "metadata:${metadata.title}" }
        })
        val engineListener = listeners.single()
        engineListener.onPlaybackStateChanged(Player.STATE_READY)
        engineListener.onIsPlayingChanged(true)
        engineListener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        engineListener.onTimelineChanged(androidx.media3.common.Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        engineListener.onMediaMetadataChanged(androidx.media3.common.MediaMetadata.Builder().setTitle("test").build())
        engineListener.onEvents(engine(), Player.Events(androidx.media3.common.FlagSet.Builder()
            .add(Player.EVENT_IS_PLAYING_CHANGED).build()))
        assertEquals(listOf("state:3", "playing:true", "ready:true", "timeline", "metadata:test", "events"), seen)
        assertSame(player, eventPlayer)
    }

    @Test fun everyJavaDefaultCallbackHasAnExplicitForwarder() {
        val player = SystemMediaPlayer(engine()) {}
        player.addListener(object : Player.Listener {})
        // Unwrap Media3's own ForwardingListener to inspect our event adapter.
        val field = listeners.single().javaClass.getDeclaredField("listener").apply { isAccessible = true }
        val adapter = field.get(listeners.single())
        for (method in Player.Listener::class.java.declaredMethods.filter { it.isDefault }) {
            assertNotNull(adapter.javaClass.getDeclaredMethod(method.name, *method.parameterTypes))
        }
    }
}
