package com.callagent.host.calls

import android.content.Context

/** Device-local experiment. Never restored through account backup. Normal online
 * reception retains its compatibility lease until sleep eligibility is supported. */
internal object StandbyPowerPolicy {
    private const val PREFS = "host-standby-runtime"
    fun releaseDuringFailure(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("release_failed_idle", false)

    fun setReleaseDuringFailure(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("release_failed_idle", enabled).apply()
        CallRuntime.requestCallSyncFromWakeHint()
    }
}
