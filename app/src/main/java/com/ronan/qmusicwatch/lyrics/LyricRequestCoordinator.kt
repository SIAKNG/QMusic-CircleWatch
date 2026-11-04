package com.ronan.qmusicwatch.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class LyricRequestKey(val owner: String, val trackId: String, val sessionGeneration: Long)
internal data class LyricLoadResult(
    val lines: List<LyricLine> = emptyList(), val loading: Boolean = false, val failed: Boolean = false,
)

/** One request per active song, bounded retry, account-scoped cache and stale-result protection. */
internal class LyricRequestCoordinator(
    private val scope: CoroutineScope,
    private val load: suspend (LyricRequestKey, Boolean) -> List<LyricLine>,
    private val publish: (LyricRequestKey, LyricLoadResult) -> Unit,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val retryPause: suspend () -> Unit = { delay(700) },
) {
    private var job: Job? = null
    private var current: LyricRequestKey? = null
    private var generation = 0L
    private var finishedAt = Long.MIN_VALUE
    private val cache = LinkedHashMap<LyricRequestKey, List<LyricLine>>()

    fun request(key: LyricRequestKey, force: Boolean = false) {
        if (key == current && job?.isActive == true && !force) return
        if (key == current && !force && finishedAt != Long.MIN_VALUE && now() - finishedAt < 3_000) return
        job?.cancel()
        current = key
        val requestGeneration = ++generation
        if (!force) cache[key]?.let { publish(key, LyricLoadResult(it)); return }
        publish(key, LyricLoadResult(loading = true))
        job = scope.launch {
            var lines = emptyList<LyricLine>()
            var failed = false
            for (attempt in 0..2) {
                if (attempt > 0) retryPause()
                try {
                    lines = load(key, force || attempt > 0)
                    failed = false
                    if (lines.isNotEmpty() || attempt == 1) break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                }
            }
            if (requestGeneration != generation || current != key) return@launch
            finishedAt = now()
            if (lines.isNotEmpty()) {
                cache.remove(key)
                cache[key] = lines
                while (cache.size > 8) cache.remove(cache.keys.first())
            }
            publish(key, LyricLoadResult(lines, failed = failed))
        }
    }

    fun clear() {
        generation++; job?.cancel(); job = null; current = null
        finishedAt = Long.MIN_VALUE; cache.clear()
    }
}
