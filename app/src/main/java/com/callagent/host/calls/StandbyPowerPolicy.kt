package com.callagent.host.calls

import android.content.Context

/** Device-local standby choices. Never restored through account backup. */
internal object StandbyPowerPolicy {
    private const val PREFS = "host-standby-runtime"
    fun releaseDuringFailure(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("release_failed_idle", false)

    fun setReleaseDuringFailure(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("release_failed_idle", enabled).apply()
        CallRuntime.requestCallSyncFromWakeHint()
    }

    /**
     * Idle reception without a continuous wake lock: on by default. Off keeps
     * the compatibility lease, which holds the CPU awake while listening.
     */
    fun sleepyListening(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("sleepy_listening", true)

    fun setSleepyListening(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("sleepy_listening", enabled).apply()
        CallRuntime.requestCallSyncFromWakeHint()
    }
}
