package com.ronan.qmusicwatch.playback

import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import java.util.IdentityHashMap

/** ExoPlayer holds one resolved URL; the application owns the actual queue. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class SystemMediaPlayer(player: Player, private val skip: (Int) -> Unit) : ForwardingPlayer(player) {
    private val listeners = IdentityHashMap<Player.Listener, Player.Listener>()
    private fun commands(base: Player.Commands) = base.buildUpon()
        .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()
    override fun getAvailableCommands(): Player.Commands = commands(super.getAvailableCommands())
    override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
    override fun seekToNext() = skip(1)
    override fun seekToPrevious() = skip(-1)
    override fun seekToNextMediaItem() = skip(1)
    override fun seekToPreviousMediaItem() = skip(-1)
    override fun addListener(listener: Player.Listener) {
        if (listeners.containsKey(listener)) return
        // Kotlin interface delegation does not forward Java default methods.
        // Player.Listener supplies defaults for every event, so delegate each
        // callback explicitly or MediaSession silently freezes at its old state.
        val forwarding = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = listener.onEvents(this@SystemMediaPlayer, events)
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = listener.onTimelineChanged(timeline, reason)
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = listener.onMediaItemTransition(mediaItem, reason)
            override fun onTracksChanged(tracks: Tracks) = listener.onTracksChanged(tracks)
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = listener.onMediaMetadataChanged(mediaMetadata)
            override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = listener.onPlaylistMetadataChanged(mediaMetadata)
            override fun onIsLoadingChanged(isLoading: Boolean) = listener.onIsLoadingChanged(isLoading)
            @Deprecated("Legacy callback")
            override fun onLoadingChanged(isLoading: Boolean) = listener.onLoadingChanged(isLoading)
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                listener.onAvailableCommandsChanged(commands(availableCommands))
            }
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = listener.onTrackSelectionParametersChanged(parameters)
            @Deprecated("Legacy callback")
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = listener.onPlayerStateChanged(playWhenReady, playbackState)
            override fun onPlaybackStateChanged(playbackState: Int) = listener.onPlaybackStateChanged(playbackState)
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = listener.onPlayWhenReadyChanged(playWhenReady, reason)
            override fun onPlaybackSuppressionReasonChanged(reason: Int) = listener.onPlaybackSuppressionReasonChanged(reason)
            override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)
            override fun onRepeatModeChanged(repeatMode: Int) = listener.onRepeatModeChanged(repeatMode)
            override fun onShuffleModeEnabledChanged(enabled: Boolean) = listener.onShuffleModeEnabledChanged(enabled)
            override fun onPlayerError(error: PlaybackException) = listener.onPlayerError(error)
            override fun onPlayerErrorChanged(error: PlaybackException?) = listener.onPlayerErrorChanged(error)
            @Deprecated("Legacy callback")
            override fun onPositionDiscontinuity(reason: Int) = listener.onPositionDiscontinuity(reason)
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) = listener.onPositionDiscontinuity(oldPosition, newPosition, reason)
            override fun onPlaybackParametersChanged(parameters: PlaybackParameters) = listener.onPlaybackParametersChanged(parameters)
            override fun onSeekBackIncrementChanged(incrementMs: Long) = listener.onSeekBackIncrementChanged(incrementMs)
            override fun onSeekForwardIncrementChanged(incrementMs: Long) = listener.onSeekForwardIncrementChanged(incrementMs)
            override fun onMaxSeekToPreviousPositionChanged(maxPositionMs: Long) = listener.onMaxSeekToPreviousPositionChanged(maxPositionMs)
            override fun onAudioSessionIdChanged(id: Int) = listener.onAudioSessionIdChanged(id)
            override fun onAudioAttributesChanged(attributes: AudioAttributes) = listener.onAudioAttributesChanged(attributes)
            override fun onVolumeChanged(volume: Float) = listener.onVolumeChanged(volume)
            override fun onSkipSilenceEnabledChanged(enabled: Boolean) = listener.onSkipSilenceEnabledChanged(enabled)
            override fun onDeviceInfoChanged(info: DeviceInfo) = listener.onDeviceInfoChanged(info)
            override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = listener.onDeviceVolumeChanged(volume, muted)
            override fun onVideoSizeChanged(videoSize: VideoSize) = listener.onVideoSizeChanged(videoSize)
            override fun onSurfaceSizeChanged(width: Int, height: Int) = listener.onSurfaceSizeChanged(width, height)
            override fun onRenderedFirstFrame() = listener.onRenderedFirstFrame()
            @Deprecated("Legacy callback")
            override fun onCues(cues: List<androidx.media3.common.text.Cue>) = listener.onCues(cues)
            override fun onCues(cues: androidx.media3.common.text.CueGroup) = listener.onCues(cues)
            override fun onMetadata(metadata: Metadata) = listener.onMetadata(metadata)
        }
        listeners[listener] = forwarding
        super.addListener(forwarding)
    }
    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)?.let { super.removeListener(it) }
    }
}
