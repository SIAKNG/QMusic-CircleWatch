package com.ronan.qmusicwatch.network

import android.util.Log
import com.ronan.qmusicwatch.data.AppLog
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

internal data class ReadRequestTag(val operation: String)

/** Records durations only. Remote addresses, URLs, bodies and headers are never logged. */
internal class NetworkTiming : EventListener() {
    private var operation: String? = null
    private var started = 0L
    private var dnsAt = 0L
    private var connectAt = 0L
    private var tlsAt = 0L
    private var dnsMs = 0L
    private var connectMs = 0L
    private var tlsMs = 0L
    private var firstByteMs = 0L
    private var code = 0
    private fun elapsed(at: Long) = (System.nanoTime() - at) / 1_000_000L
    override fun callStart(call: Call) {
        operation = call.request().tag(ReadRequestTag::class.java)?.operation
        started = System.nanoTime()
    }
    override fun dnsStart(call: Call, domainName: String) { dnsAt = System.nanoTime() }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { dnsMs += elapsed(dnsAt) }
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { connectAt = System.nanoTime() }
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) { connectMs += elapsed(connectAt) }
    override fun secureConnectStart(call: Call) { tlsAt = System.nanoTime() }
    override fun secureConnectEnd(call: Call, handshake: Handshake?) { tlsMs += elapsed(tlsAt) }
    override fun responseHeadersEnd(call: Call, response: Response) { code = response.code; firstByteMs = elapsed(started) }
    override fun callEnd(call: Call) = report(null)
    override fun callFailed(call: Call, ioe: IOException) = report(ioe.javaClass.simpleName)
    private fun report(error: String?) {
        val label = operation ?: return
        val value = "op=$label total_ms=${elapsed(started)} dns_ms=$dnsMs connect_ms=$connectMs tls_ms=$tlsMs headers_ms=$firstByteMs status=$code result=${error ?: "ok"}"
        AppLog.write("NETWORK_PERF", value)
        Log.i("QMusicNetworkPerf", value)
    }
}
