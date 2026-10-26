package com.callagent.host.data

import android.os.SystemClock
import org.json.JSONObject

/** Coalesce successful renewals from the engine and node probes, without changing the 90s lease. */
internal object RtcCapabilityRefresh {
    private val recent = object : LinkedHashMap<String, Pair<Long, JSONObject>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, JSONObject>>?): Boolean = size > 128
    }

    fun request(sessionId: String, enabled: Boolean, send: () -> JSONObject): JSONObject =
        SessionWorkLocks.withLock("capability:$sessionId") {
            val now = SystemClock.elapsedRealtime()
            val previous = synchronized(recent) { recent[sessionId] }
            if (enabled && previous != null && now >= previous.first && now - previous.first < 30_000L) {
                return@withLock JSONObject(previous.second.toString())
            }
            val response = send()
            synchronized(recent) {
                if (enabled) recent[sessionId] = SystemClock.elapsedRealtime() to JSONObject(response.toString())
                else recent.remove(sessionId)
            }
            response
        }
}
