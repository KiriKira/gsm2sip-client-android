package com.callagent.host

import android.app.Application
import com.callagent.host.notify.HostNotificationChannels
import com.google.android.material.color.DynamicColors

class HostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        runCatching { HostNotificationChannels.ensure(this) }
    }
}
