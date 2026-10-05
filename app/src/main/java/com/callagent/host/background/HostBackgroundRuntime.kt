package com.callagent.host.background

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.callagent.host.data.SessionStore
import com.callagent.host.data.HostSession
import com.callagent.host.data.sameSessionInstance
import com.callagent.host.data.clientDatabaseName
import java.security.MessageDigest

data class BackgroundStatus(
    val enabled: Boolean,
    val running: Boolean,
    val notificationsEnabled: Boolean,
    val batteryExempt: Boolean,
    val connectionLabel: String,
    val lastSyncAt: Long,
    val issue: String?
)

object HostBackgroundRuntime {
    const val STATUS_CHANGED = "com.callagent.host.background.STATUS_CHANGED"
    const val BACKGROUND_SYNCED = "com.callagent.host.BACKGROUND_SYNCED"
    private val startLock = Any()
    private val startRequests = BackgroundStartRequests()

    fun snapshot(context: Context): BackgroundStatus {
        val app = context.applicationContext
        observeTaskManagerStop(app)
        val state = RuntimeState.preferences(app)
        val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val channelsEnabled = Build.VERSION.SDK_INT < 26 || listOf(
            HostBackgroundService.CHANNEL_SERVICE,
            HostBackgroundService.CHANNEL_INBOUND
        ).all { id -> notificationManager.getNotificationChannel(id)?.importance != android.app.NotificationManager.IMPORTANCE_NONE }
        val enabled = state.getBoolean(RuntimeState.KEY_ENABLED, HostBackgroundPolicy.DEFAULT_ENABLED)
        val running = HostBackgroundService.isRunning
        return BackgroundStatus(
            enabled = enabled,
            running = running,
            notificationsEnabled = notificationManager.areNotificationsEnabled() && channelsEnabled &&
                (Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
            batteryExempt = powerManager.isIgnoringBatteryOptimizations(app.packageName),
            connectionLabel = if (enabled && !running) "Not running" else state.getString(RuntimeState.KEY_CONNECTION, "Stopped") ?: "Stopped",
            lastSyncAt = state.getLong(RuntimeState.KEY_LAST_SYNC, 0L),
            issue = state.getString(RuntimeState.KEY_ISSUE, null)
        )
    }

    /** Enable is deliberately accepted only from a focused Activity following an explicit user action. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val app = context.applicationContext
        val state = RuntimeState.preferences(app)
        if (!enabled) {
            if (HostBackgroundService.isRunning) return HostBackgroundService.stopByUser()
            if (!state.edit().putBoolean(RuntimeState.KEY_ENABLED, false).commit()) return false
            HostBackgroundService.clearPending(
                app,
                state.getString(RuntimeState.KEY_SESSION_DB_NAME, null),
                state.getString(RuntimeState.KEY_SESSION_STAMP, null)
            )
            app.stopService(Intent(app, HostBackgroundService::class.java))
            RuntimeState.update(app, connection = "Stopped", issue = null)
            return true
        }

        val activity = context as? Activity ?: return false
        if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) return false
        val session = runCatching { SessionStore(app).read() }.getOrNull()
        if (session == null || session.role != "client") {
            RuntimeState.update(app, issue = "Pair this phone before enabling background sync.")
            return false
        }

        val currentSessionStamp = sessionStamp(session)
        val previousSessionStamp = state.getString(RuntimeState.KEY_SESSION_STAMP, null)
        val previousDatabaseName = state.getString(RuntimeState.KEY_SESSION_DB_NAME, null)
        val saved = state.edit()
            .putBoolean(RuntimeState.KEY_ENABLED, true)
            .putBoolean(RuntimeState.KEY_TASK_MANAGER_STOPPED, false)
            .putLong(RuntimeState.KEY_EXPLICIT_ENABLE_AT, System.currentTimeMillis())
            .putString(RuntimeState.KEY_SESSION_DB_NAME, clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId))
            .putString(RuntimeState.KEY_SESSION_STAMP, currentSessionStamp)
            .putString(RuntimeState.KEY_ISSUE, null)
            .commit()
        if (!saved) {
            RuntimeState.update(app, issue = "Could not save the background sync setting. Try again.")
            return false
        }
        if (previousSessionStamp != currentSessionStamp) {
            previousDatabaseName?.let { oldDb -> HostBackgroundService.clearPending(app, oldDb, previousSessionStamp) }
        }
        return try {
            val intent = Intent(app, HostBackgroundService::class.java).setAction(HostBackgroundService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
            true
        } catch (_: RuntimeException) {
            state.edit().putBoolean(RuntimeState.KEY_ENABLED, false).commit()
            RuntimeState.update(app, connection = "Stopped", issue = "Android blocked the background service start. Open the app to resume; battery exemption may help.")
            false
        }
    }

    /**
     * Bind background sync to the current pairing and start it from a focused Activity.
     * Calling this repeatedly for an already-running matching session does not rewrite its
     * binding timestamp or send another service start request.
     */
    fun ensureStartedForPairedSession(activity: Activity): Boolean {
        if (!isVisibleAndFocused(activity)) return false
        val app = activity.applicationContext
        return synchronized(startLock) {
            if (!isVisibleAndFocused(activity)) return@synchronized false
            observeTaskManagerStop(app)

            val session = runCatching { SessionStore(app).read() }.getOrNull()
            val pairedClient = session?.role == "client"
            if (!pairedClient || session == null) return@synchronized false

            val state = RuntimeState.preferences(app)
            val newSessionStamp = sessionStamp(session)
            val newDatabaseName = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
            val oldSessionStamp = state.getString(RuntimeState.KEY_SESSION_STAMP, null)
            val oldDatabaseName = state.getString(RuntimeState.KEY_SESSION_DB_NAME, null)
            val bindingMatches = oldSessionStamp == newSessionStamp && oldDatabaseName == newDatabaseName
            val systemTaskStopped = state.getBoolean(RuntimeState.KEY_TASK_MANAGER_STOPPED, false)
            val enabled = state.getBoolean(RuntimeState.KEY_ENABLED, HostBackgroundPolicy.DEFAULT_ENABLED)
            val serviceRunning = HostBackgroundService.isRunning

            if (!HostBackgroundPolicy.shouldStartForPairedSession(
                    pairedClient = pairedClient,
                    activityFocused = activity.hasWindowFocus(),
                    bindingMatches = bindingMatches,
                    enabled = enabled,
                    systemTaskStopped = systemTaskStopped,
                    serviceRunning = serviceRunning
                )
            ) {
                return@synchronized enabled && bindingMatches && !systemTaskStopped && serviceRunning
            }

            if (startRequests.isPending(newSessionStamp) && enabled && bindingMatches && !systemTaskStopped) {
                return@synchronized true
            }

            if (HostBackgroundPolicy.shouldEnablePairedBinding(pairedClient, bindingMatches, enabled, systemTaskStopped)) {
                val saved = state.edit()
                    .putBoolean(RuntimeState.KEY_ENABLED, true)
                    .putBoolean(RuntimeState.KEY_TASK_MANAGER_STOPPED, false)
                    .putLong(RuntimeState.KEY_EXPLICIT_ENABLE_AT, System.currentTimeMillis())
                    .putString(RuntimeState.KEY_SESSION_DB_NAME, newDatabaseName)
                    .putString(RuntimeState.KEY_SESSION_STAMP, newSessionStamp)
                    .putString(RuntimeState.KEY_ISSUE, null)
                    .commit()
                if (!saved) {
                    RuntimeState.update(app, issue = "Could not save the paired background session. Open the app to retry.")
                    return@synchronized false
                }
                if (!bindingMatches) {
                    oldDatabaseName?.let { oldDb -> HostBackgroundService.clearPending(app, oldDb, oldSessionStamp) }
                }
            }

            // Pairing can change from another app screen while state is being persisted.
            val latest = runCatching { SessionStore(app).read() }.getOrNull()
            if (latest == null || !session.sameSessionInstance(latest) || latest.role != "client") {
                return@synchronized false
            }
            if (bindingMatches && enabled && serviceRunning && !systemTaskStopped) return@synchronized true
            if (!isVisibleAndFocused(activity)) return@synchronized false

            try {
                val intent = Intent(app, HostBackgroundService::class.java).setAction(HostBackgroundService.ACTION_START)
                startRequests.request(newSessionStamp)
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
                true
            } catch (_: RuntimeException) {
                startRequests.onRejected(newSessionStamp)
                RuntimeState.update(app, connection = "Stopped", issue = "Android blocked background service startup. Open the app again to resume.")
                false
            }
        }
    }

    fun openBatterySettings(activity: Activity) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${activity.packageName}"))
        try {
            activity.startActivity(request)
        } catch (_: RuntimeException) {
            activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openNotificationSettings(activity: Activity) {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
        }
        activity.startActivity(intent)
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    /** Wakes the foreground service only when it is already running; it never starts a service from the background. */
    fun requestSync(context: Context) {
        if (!HostBackgroundService.isRunning) return
        HostBackgroundService.requestSync(context.applicationContext)
    }

    internal fun restoreAllowed(context: Context): Boolean {
        val app = context.applicationContext
        if (observeTaskManagerStop(app)) return false
        val state = RuntimeState.preferences(app)
        val session = runCatching { SessionStore(app).read() }.getOrNull()
        if (!HostBackgroundPolicy.shouldRestore(state.getBoolean(RuntimeState.KEY_ENABLED, HostBackgroundPolicy.DEFAULT_ENABLED), session?.role == "client")) {
            return false
        }
        val sessionMatchesOptIn = session != null &&
            state.getString(RuntimeState.KEY_SESSION_DB_NAME, null) == clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId) &&
            state.getString(RuntimeState.KEY_SESSION_STAMP, null) == sessionStamp(session)
        if (!sessionMatchesOptIn) {
            RuntimeState.update(app, issue = "The paired account changed. Open the app to resume background sync for this account.")
            return false
        }
        return true
    }

    internal fun onServiceStarted(session: HostSession) = synchronized(startLock) {
        val startedStamp = sessionStamp(session)
        startRequests.onStarted(startedStamp)
    }

    internal fun onServiceStartRejected(session: HostSession?) = synchronized(startLock) {
        startRequests.onRejected(session?.let(::sessionStamp))
    }

    internal fun onServiceStopped(session: HostSession?) = synchronized(startLock) {
        val stoppedStamp = session?.let(::sessionStamp)
        startRequests.onStopped(stoppedStamp)
    }

    /** Record system user-stop before an ordinary later process exit can replace it. */
    private fun observeTaskManagerStop(context: Context): Boolean {
        val app = context.applicationContext
        val state = RuntimeState.preferences(app)
        if (state.getBoolean(RuntimeState.KEY_TASK_MANAGER_STOPPED, false)) return true
        if (!state.getBoolean(RuntimeState.KEY_ENABLED, false) || Build.VERSION.SDK_INT < 30) return false
        val latest = runCatching {
            (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getHistoricalProcessExitReasons(app.packageName, 0, 1).firstOrNull()
        }.getOrNull() ?: return false
        val explicitEnableAt = state.getLong(RuntimeState.KEY_EXPLICIT_ENABLE_AT, 0L)
        if (!HostBackgroundPolicy.shouldSuppressUserStopRestore(
                enabled = true,
                pairedClient = true,
                lastExplicitEnableAt = explicitEnableAt,
                lastExitReason = latest.reason,
                lastExitAt = latest.timestamp,
                userRequestedReason = android.app.ApplicationExitInfo.REASON_USER_REQUESTED
            )) return false
        state.edit().putBoolean(RuntimeState.KEY_ENABLED, false)
            .putBoolean(RuntimeState.KEY_TASK_MANAGER_STOPPED, true).commit()
        HostBackgroundService.clearPending(app, state.getString(RuntimeState.KEY_SESSION_DB_NAME, null), state.getString(RuntimeState.KEY_SESSION_STAMP, null))
        RuntimeState.update(app, connection = "Stopped", issue = "Android stopped background sync from the system task manager. Enable it again to resume.")
        return true
    }

    private const val REQUEST_NOTIFICATIONS = 7031

    private fun isVisibleAndFocused(activity: Activity): Boolean =
        !activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus()
}

internal fun sessionStamp(session: HostSession): String = MessageDigest.getInstance("SHA-256")
    .digest(session.sessionInstanceId.toByteArray(Charsets.UTF_8))
    .take(16)
    .joinToString("") { byte -> "%02x".format(byte) }

internal object RuntimeState {
    const val PREFS = "host-background"
    const val KEY_ENABLED = "enabled"
    const val KEY_TASK_MANAGER_STOPPED = "task_manager_stopped"
    const val KEY_CONNECTION = "connection_label"
    const val KEY_LAST_SYNC = "last_sync_at"
    const val KEY_ISSUE = "issue"
    const val KEY_EXPLICIT_ENABLE_AT = "last_explicit_enable_at"
    const val KEY_SESSION_DB_NAME = "session_db_name"
    const val KEY_SESSION_STAMP = "session_stamp"

    fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun update(context: Context, connection: String? = null, lastSyncAt: Long? = null, issue: String? = null) {
        val edit = preferences(context).edit()
        if (connection != null) edit.putString(KEY_CONNECTION, connection)
        if (lastSyncAt != null) edit.putLong(KEY_LAST_SYNC, lastSyncAt)
        edit.putString(KEY_ISSUE, issue)
        edit.apply()
        context.sendBroadcast(Intent(HostBackgroundRuntime.STATUS_CHANGED).setPackage(context.packageName))
    }
}
