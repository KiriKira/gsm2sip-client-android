package com.callagent.host.background

import android.content.Context

/**
 * The WebSocket heartbeat a reception v2 node keeps when push cannot wake
 * this phone. Only the node pings, at the interval the phone asks for:
 * every ping is a packet that wakes a sleeping phone, so the interval starts
 * at two minutes and grows to five while connections survive it. A
 * connection that dies between one and two intervals after opening was most
 * likely dropped as idle by a NAT or VPN, so the interval halves, down to a
 * minute. Kept per node, across restarts.
 */
internal object WakeHeartbeat {
    const val START_SECONDS = 120
    const val MIN_SECONDS = 60
    const val MAX_SECONDS = 300
    private const val PREFS = "host-wake-heartbeat"

    /** The interval to use after a connection that lived [livedMs] ended. */
    fun next(current: Int, livedMs: Long, failed: Boolean): Int {
        val interval = current * 1000L
        return when {
            failed && livedMs in interval until 2 * interval -> maxOf(MIN_SECONDS, current / 2)
            livedMs >= 4 * interval -> minOf(MAX_SECONDS, current * 3 / 2)
            else -> current
        }
    }

    fun seconds(context: Context, apiRoot: String): Int =
        prefs(context).getInt(apiRoot, START_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)

    fun ended(context: Context, apiRoot: String, livedMs: Long, failed: Boolean) {
        val current = seconds(context, apiRoot)
        val next = next(current, livedMs, failed)
        if (next != current) {
            prefs(context).edit().putInt(apiRoot, next).apply()
            com.callagent.host.data.StandbyDiagnostics.event("wss_heartbeat_$next")
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
