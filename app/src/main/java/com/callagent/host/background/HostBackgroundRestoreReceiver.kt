package com.callagent.host.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.callagent.host.data.SessionStore

class HostBackgroundRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        if (!HostBackgroundRuntime.restoreAllowed(app)) return
        val session = runCatching { SessionStore(app).read() }.getOrNull()
        if (session == null || session.role != "client") return
        try {
            val start = Intent(app, HostBackgroundService::class.java).setAction(HostBackgroundService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(start) else app.startService(start)
        } catch (_: RuntimeException) {
            RuntimeState.update(app, connection = "Stopped", issue = "Android blocked background restart. Open the app to resume; battery exemption may help.")
        }
    }
}
