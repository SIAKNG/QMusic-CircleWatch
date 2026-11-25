package com.ronan.qmusicwatch.network

import com.ronan.qmusicwatch.model.*
import java.io.IOException
import kotlinx.coroutines.withTimeoutOrNull

internal const val PLAYBACK_QUALITY_TIMEOUT_MS = 4_000L
internal const val SLOW_BUFFER_TIMEOUT_MS = 6_000L

internal fun networkQualityMessage(quality: String): String =
    "网络较慢，本首歌曲已临时切换为${qualityShortLabel(quality)}音质"

internal fun lowerNetworkQuality(quality: String): String? = when (normalizeQualityId(quality)) {
    QUALITY_SQ -> QUALITY_HQ
    QUALITY_HQ -> QUALITY_STANDARD
    else -> null
}

/** A playback-only budget. Downloads and the saved default keep their chosen quality. */
internal suspend fun resolvePlaybackStream(
    preferred: String,
    timeoutMs: Long = PLAYBACK_QUALITY_TIMEOUT_MS,
    load: suspend (String) -> StreamData,
): StreamData {
    var quality = normalizeQualityId(preferred)
    var networkFallback = false
    while (true) {
        val lower = lowerNetworkQuality(quality)
        val stream = try {
            if (lower == null) load(quality) else withTimeoutOrNull(timeoutMs) { load(quality) }
        } catch (error: IOException) {
            if (lower == null) throw error
            null
        }
        if (stream != null) return stream.copy(networkFallback = networkFallback || stream.networkFallback)
        quality = lower ?: error("未能获取播放地址")
        networkFallback = true
    }
}
