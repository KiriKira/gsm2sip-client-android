package com.callagent.host

import android.app.Application
import com.callagent.host.notify.HostNotificationChannels
import com.google.android.material.color.DynamicColors

class HostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // A restored full backup replaces data before anything opens it.
        runCatching { com.callagent.backup.FullBackup.applyPending(this, com.callagent.host.backup.HostFullBackupProfile) }
            .onFailure { android.util.Log.e("HostApp", "Full backup restore failed: ${it.javaClass.simpleName}") }
        com.callagent.host.network.NetworkEnvironment.start(this)
        com.callagent.host.calls.RtcSleep.install(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
        runCatching { HostNotificationChannels.ensure(this) }
    }
}
