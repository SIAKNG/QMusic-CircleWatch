package com.ronan.qmusicwatch.playback

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ronan.qmusicwatch.data.AppLog
import com.ronan.qmusicwatch.network.safeLocalOrArtworkUri
import com.ronan.qmusicwatch.network.safeLocalOrQqMediaUri
import kotlin.math.roundToInt

data class DeviceVolumeState(
    val current: Int,
    val max: Int,
    val muted: Boolean = false,
) {
    val percent: Int?
        get() = max.takeIf { it > 0 }
            ?.let { ((current.coerceIn(0, it).toFloat() / it) * 100f).roundToInt() }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackConnection(context: Context) {
    private companion object {
        // A single crown detent can be reported as several MotionEvents or
        // repeated volume-key downs. Keep one media-volume step per window so
        // the first turn cannot jump from 0 to a loud mid-scale value.
        const val MIN_VOLUME_STEP_INTERVAL_MS = 55L
    }
    private val audio = context.getSystemService(AudioManager::class.java)
    private val future: ListenableFuture<MediaController> = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
    )
        // Device-volume commands are filtered for local sessions unless this
        // is explicitly enabled. Without it, crowns that deliver rotary
        // events reach the app but Media3 silently declines the adjustment.
        .setAllowDeviceVolumeCommandsForLocalPlayback(true)
        .buildAsync()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _sleepRemaining = MutableStateFlow(0L)
    val sleepRemaining = _sleepRemaining.asStateFlow()
    private var sleepJob: Job? = null
    private var lastVolumeAdjustmentAt = 0L
    private var stopAfterCurrent = false
    private var sleepVolume: Float? = null
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val _deviceVolume = MutableStateFlow<DeviceVolumeState?>(null)
    val deviceVolume = _deviceVolume.asStateFlow()
    var onError: ((PlaybackErrorEvent) -> Unit)? = null
    var onMediaItemChanged: ((String, String) -> Unit)? = null
    init {
        publishSystemVolume()
        future.addListener({
            controllerOrNull()?.let { controller ->
                publishControllerVolume(controller)
                controller.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val causes = generateSequence<Throwable>(error) { it.cause }.joinToString(" <- ") { "${it.javaClass.simpleName}:${it.message.orEmpty()}" }
                    AppLog.write("PLAYER", "${error.errorCodeName} $causes")
                    val controller = controllerOrNull() ?: return
                    onError?.invoke(PlaybackErrorEvent(
                        error = error,
                        mediaId = controller.currentMediaItem?.mediaId.orEmpty(),
                        positionMs = controller.currentPosition.coerceAtLeast(0),
                        isLocalFile = controller.currentMediaItem?.localConfiguration?.uri?.scheme == "file",
                    ))
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem ?: return
                    onMediaItemChanged?.invoke(mediaItem.mediaId, mediaItem.localConfiguration?.uri?.toString().orEmpty())
                }
                override fun onDeviceVolumeChanged(deviceVolume: Int, muted: Boolean) {
                    publishControllerVolume(controller, deviceVolume, muted)
                }
            })
            } ?: AppLog.write("PLAYER", "controller connection failed")
        }, mainExecutor)
    }
    private fun controllerOrNull(): MediaController? =
        if (!future.isDone || future.isCancelled) null else runCatching { future.get() }
            .onFailure { AppLog.write("PLAYER", "controller ${it.javaClass.simpleName}:${it.message.orEmpty()}") }
            .getOrNull()

    private fun withController(action: (MediaController) -> Unit) {
        val run = { controllerOrNull()?.let { controller -> runCatching { action(controller) }.onFailure { AppLog.write("PLAYER", "action ${it.javaClass.simpleName}:${it.message.orEmpty()}") } }; Unit }
        if (future.isDone) run() else future.addListener(run, mainExecutor)
    }
    fun replaceStream(
        id: String,
        uri: String,
        title: String,
        artist: String,
        artwork: String,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        AppLog.write("PLAYER", "prepare track=$id scheme=${android.net.Uri.parse(uri).scheme.orEmpty()} start_ms=${startPositionMs.coerceAtLeast(0)}")
        withController { controller ->
            controller.apply {
                setMediaItem(
                    playbackMediaItem(id, uri, title, artist, artwork),
                    startPositionMs.coerceAtLeast(0),
                )
                prepare()
                if (playWhenReady) play() else pause()
            }
        }
    }
    fun pause() = withController(MediaController::pause)
    fun stopAndClear() {
        cancelSleepTimer()
        withController { controller -> controller.stop(); controller.clearMediaItems() }
    }
    fun resume() = withController(MediaController::play)
    fun seek(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun position() = controllerOrNull()?.currentPosition?.coerceAtLeast(0) ?: 0L
    fun duration() = controllerOrNull()?.duration?.coerceAtLeast(0) ?: 0L
    /** Single controller fetch for poll loops instead of three separate get() calls per tick. */
    fun snapshot(trackId: String): Triple<Boolean, Long, Long> {
        val controller = controllerOrNull()?.takeIf { it.currentMediaItem?.mediaId == trackId }
        return Triple(
            controller?.isPlaying == true,
            controller?.currentPosition?.coerceAtLeast(0) ?: 0L,
            controller?.duration?.coerceAtLeast(0) ?: 0L,
        )
    }
    fun currentMediaId() = controllerOrNull()?.currentMediaItem?.mediaId.orEmpty()
    fun currentUri() = controllerOrNull()?.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
    fun isPlaying() = controllerOrNull()?.isPlaying == true
    fun playWhenReady() = controllerOrNull()?.playWhenReady == true
    /** The panel and crown use the same media stream and volume steps. */
    fun adjustVolume(direction: Int): Boolean {
        if (direction == 0 || !acceptVolumeAdjustment()) return false
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return setMediaVolume(current + if (direction > 0) 1 else -1)
    }

    private fun acceptVolumeAdjustment(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastVolumeAdjustmentAt < MIN_VOLUME_STEP_INTERVAL_MS) return false
        lastVolumeAdjustmentAt = now
        return true
    }

    fun refreshDeviceVolume() = publishSystemVolume()

    fun setMediaVolume(level: Int): Boolean =
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val min = if (android.os.Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
            val target = level.coerceIn(min, max)
            val changed = target != audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (changed) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
            publishSystemVolume()
            changed
        }.onFailure { AppLog.write("PLAYER", "set media volume ${it.javaClass.simpleName}") }.getOrDefault(false)

    private fun publishControllerVolume(
        controller: MediaController,
        current: Int = controller.deviceVolume,
        muted: Boolean = controller.isDeviceMuted,
    ) {
        // MediaController can briefly expose stale 0/50% values while the
        // service connects. AudioManager is the actual stream used by
        // ExoPlayer, so keep it as the single source for the UI state.
        publishSystemVolume(mutedOverride = muted)
    }

    private fun publishSystemVolume(mutedOverride: Boolean? = null) {
        runCatching {
            _deviceVolume.value = DeviceVolumeState(
                current = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                muted = mutedOverride ?: audio.isStreamMute(AudioManager.STREAM_MUSIC),
            )
        }.onFailure {
            AppLog.write("PLAYER", "volume-state ${it.javaClass.simpleName}:${it.message.orEmpty()}")
        }
    }

    fun startSleepTimer(minutes: Int, finishCurrent: Boolean = false) {
        // Cancel first, then restore: the old job's tail never runs after cancel,
        // so its fade-out would otherwise leave the volume permanently lowered.
        sleepJob?.cancel()
        restoreVolume()
        stopAfterCurrent = false
        sleepJob = scope.launch {
            _sleepRemaining.value = minutes.coerceIn(1, 1440) * 60L
            while (_sleepRemaining.value > 0) {
                delay(1_000); _sleepRemaining.value--
                if (!finishCurrent && _sleepRemaining.value <= 10) {
                    val player = controllerOrNull() ?: continue
                    val initial = sleepVolume ?: player.volume.also { sleepVolume = it }
                    player.volume = initial * (_sleepRemaining.value / 10f)
                }
            }
            if (finishCurrent) stopAfterCurrent = true else {
                controllerOrNull()?.let { pause() } ?: pause()
                restoreVolume()
            }
        }
    }
    fun cancelSleepTimer() {
        sleepJob?.cancel(); sleepJob = null; stopAfterCurrent = false
        restoreVolume()
        _sleepRemaining.value = 0
    }

    /** Restores the volume captured before fade-out exactly once, on every exit path. */
    private fun restoreVolume() {
        val saved = sleepVolume ?: return
        sleepVolume = null
        withController { it.volume = saved }
    }
    fun consumeStopAfterCurrentAtEnd(): Boolean {
        if (!stopAfterCurrent) return false
        stopAfterCurrent = false
        cancelSleepTimer()
        pause()
        return true
    }
    fun release() {
        scope.cancel()
        MediaController.releaseFuture(future)
    }
}

internal fun playbackMediaItem(id: String, uri: String, title: String, artist: String, artwork: String): MediaItem {
    val playableUri = safeLocalOrQqMediaUri(uri)
    require(playableUri.isNotBlank()) { "播放地址不受信任" }
    val safeArtwork = safeLocalOrArtworkUri(artwork)
    return MediaItem.Builder().setMediaId(id).setUri(playableUri).setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { if (safeArtwork.isNotBlank()) setArtworkUri(android.net.Uri.parse(safeArtwork)) }.build()
    ).build()
}
