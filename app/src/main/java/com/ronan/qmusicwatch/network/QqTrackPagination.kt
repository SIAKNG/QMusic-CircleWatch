package com.ronan.qmusicwatch.network

import com.ronan.qmusicwatch.model.Track
import java.security.MessageDigest
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

private val trackListKeys = listOf("songlist", "songList", "songInfoList", "songs", "list")

internal data class QqTrackPage(
    val items: JsonArray,
    val consumedCount: Int,
    val total: Int?,
    val hasMore: Boolean?,
) {
    fun nextOffset(offset: Int, requestedSize: Int): Int? {
        if (items.isEmpty()) return null
        val next = offset + consumedCount
        return next.takeIf {
            when {
                total != null -> next < total
                hasMore != null -> hasMore
                else -> items.size >= requestedSize
            }
        }
    }
}

internal fun parseQqTrackPage(root: JsonObject): QqTrackPage {
    fun containers(value: JsonObject): Sequence<JsonObject> = sequence {
        yield(value)
        value.values.filterIsInstance<JsonObject>().forEach { yieldAll(containers(it)) }
    }
    val container = containers(root).firstOrNull { item ->
        trackListKeys.any { item[it] is JsonArray }
    } ?: throw QqResponseException("QQ 音乐响应缺少歌曲列表，请稍后重试")
    val items = trackListKeys.firstNotNullOf { container[it] as? JsonArray }
    fun number(vararg names: String): Int? = names.firstNotNullOfOrNull { name ->
        (container[name] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }
    }
    val total = number("total_song_num", "totalNum", "total", "totalnum", "totalSongNum")
    val more = (container["hasmore"] ?: container["hasMore"]) as? JsonPrimitive
    // Pagination must count upstream slots, not parsed/deduplicated tracks.
    // A page may contain unavailable entries or duplicate song MIDs.
    val consumed = maxOf(items.size, number("songlist_size") ?: 0)
    return QqTrackPage(items, consumed, total, more?.booleanOrNull ?: more?.intOrNull?.let { it != 0 })
}

internal suspend fun collectQqTrackPages(
    pageSize: Int = 100,
    maxPages: Int = 100,
    parallelism: Int = 1,
    onProgress: (suspend (List<Track>) -> Unit)? = null,
    fetchPage: suspend (offset: Int, size: Int) -> JsonObject,
    parseTracks: (JsonElement) -> List<Track>,
): List<Track> = supervisorScope {
    require(pageSize > 0 && maxPages > 0 && parallelism in 1..3)
    val tracks = linkedMapOf<String, Track>()
    val seenPages = mutableSetOf<String>()
    val pending = linkedMapOf<Int, Deferred<JsonObject>>()
    var offset = 0
    var count = 0
    var fixedSize = true
    var previousTotal: Int? = null
    try {
        while (count < maxPages) {
            val page = parseQqTrackPage(pending.remove(offset)?.await() ?: fetchPage(offset, pageSize))
            count++
            if (page.items.isEmpty()) {
                if ((page.total ?: 0) > offset || page.hasMore == true) {
                    throw QqResponseException("QQ 音乐歌曲列表未完整返回，请稍后重试")
                }
                return@supervisorScope tracks.values.toList()
            }
            val batch = parseTracks(page.items)
            if (batch.isNotEmpty()) {
                val digest = MessageDigest.getInstance("SHA-256").digest(page.items.toString().toByteArray(Charsets.UTF_8))
                val signature = buildString(64) {
                    val hex = "0123456789abcdef"
                    digest.forEach { byte -> val value = byte.toInt() and 255; append(hex[value ushr 4]); append(hex[value and 15]) }
                }
                if (!seenPages.add(signature)) throw QqResponseException("QQ 音乐重复返回同一页歌曲，请稍后重试")
            }
            batch.forEach { tracks.putIfAbsent(it.id, it) }
            onProgress?.invoke(tracks.values.toList())
            val next = page.nextOffset(offset, pageSize) ?: return@supervisorScope tracks.values.toList()
            // Speculation is safe only with an explicit total and stable upstream slot size.
            // If a server caps/changes pages, discard future offsets and continue serially.
            if (page.consumedCount != pageSize || page.total == null ||
                (previousTotal != null && page.total != previousTotal)) {
                fixedSize = false
                pending.values.forEach { it.cancel() }; pending.clear()
            }
            previousTotal = page.total
            offset = next
            if (fixedSize && parallelism > 1) {
                val limit = minOf(parallelism, maxPages - count)
                var candidate = offset
                repeat(limit) {
                    if (candidate < (page.total ?: 0) && candidate !in pending) {
                        val start = candidate
                        pending[start] = async { fetchPage(start, pageSize) }
                    }
                    candidate += pageSize
                }
            }
        }
        throw QqResponseException("歌曲列表超过单次加载上限，请稍后重试")
    } finally { pending.values.forEach { it.cancel() } }
}
