package com.ronan.qmusicwatch.performance

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import com.ronan.qmusicwatch.data.AppLog

/** Observe actual rendered frames without waking an idle UI every vsync. */
object FramePerformanceMonitor {
    @Volatile var section: String = "startup"
    private val thread by lazy { HandlerThread("qmusic-frame-metrics").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }
    private var window: Window? = null
    private var frames = 0
    private var slowFrames = 0
    private var frozenFrames = 0
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val frameMs = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000L
        frames++
        if (frameMs > 32L) slowFrames++
        if (frameMs > 700L) frozenFrames++
        if (frames >= 120) flush()
    }

    fun start(target: Window) {
        if (window === target) return
        stop()
        window = target
        target.addOnFrameMetricsAvailableListener(listener, handler)
    }

    fun stop() {
        val previous = window ?: return
        previous.removeOnFrameMetricsAvailableListener(listener)
        window = null
        handler.post { flush() }
    }

    private fun flush() {
        if (frames > 0) AppLog.write("PERF", "section=$section frames=$frames slow=$slowFrames frozen=$frozenFrames")
        frames = 0; slowFrames = 0; frozenFrames = 0
    }
}
