package com.ronan.qmusicwatch.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/** Read/close the entire response on OkHttp's worker; cancellation aborts body reads too. */
internal suspend fun <T> Call.awaitBody(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }
        override fun onResponse(call: Call, response: Response) {
            if (!continuation.isActive) { response.close(); return }
            val result = runCatching { response.use(read) }
            if (continuation.isActive) continuation.resumeWith(result)
        }
    })
}
