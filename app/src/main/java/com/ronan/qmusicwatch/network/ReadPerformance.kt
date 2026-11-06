package com.ronan.qmusicwatch.network

import android.os.SystemClock
import android.util.Log
import com.ronan.qmusicwatch.data.AppLog
import com.ronan.qmusicwatch.model.UserProfile

/** Identity can appear early, but must never provision unverified membership rights. */
internal fun mergeProfileIdentity(current: UserProfile?, incoming: UserProfile): UserProfile =
    (current ?: UserProfile()).copy(
        displayName = incoming.displayName.ifBlank { current?.displayName.orEmpty() },
        avatarUrl = incoming.avatarUrl.ifBlank { current?.avatarUrl.orEmpty() },
    )

/** Only operation/stage/time/count: no URL, account, query or credentials. */
internal fun reportReadPerformance(operation: String, stage: String, started: Long, count: Int = 0) {
    val value = "op=$operation stage=$stage ms=${SystemClock.elapsedRealtime() - started} count=$count"
    AppLog.write("READ_PERF", value)
    Log.i("QMusicReadPerf", value)
}
