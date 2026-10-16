package com.callagent.host.calls

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/** Exists only while a Telecom call is dialing, ringing or active. */
class CallForegroundService : Service() {
    private var inForeground = false

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.prepare(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pendingStarts.decrementAndGet() < 0) pendingStarts.set(0)
        val callId = intent?.getStringExtra(EXTRA_CALL_ID)
        val withMicrophone = intent?.getBooleanExtra(EXTRA_MICROPHONE, false) == true
        val call = callId?.let(CallRuntime::snapshot)
        if (call == null || call.phase in setOf(CallPhase.DISCONNECTING, CallPhase.ENDED, CallPhase.FAILED)) {
            stopUnstarted(startId)
            return START_NOT_STICKY
        }
        if (withMicrophone && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopUnstarted(startId)
            callId?.let { CallRuntime.onForegroundStartFailed(it, withMicrophone) }
            return START_NOT_STICKY
        }
        try {
            val notification = when (call.phase) {
                CallPhase.INCOMING_RINGING -> CallNotificationManager.incomingNotification(this, call)
                CallPhase.ACTIVE, CallPhase.ANSWERING -> CallNotificationManager.ongoingNotification(this, call)
                else -> CallNotificationManager.connectingNotification(this, call)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                if (withMicrophone) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(CallNotificationManager.NOTIFICATION_ID, notification, types)
            } else {
                @Suppress("DEPRECATION")
                startForeground(CallNotificationManager.NOTIFICATION_ID, notification)
            }
            inForeground = true
            CallRuntime.onForegroundReady(call.callId, withMicrophone)
        } catch (failure: RuntimeException) {
            // SecurityException included: Android refused this foreground type now.
            Log.w(TAG, "Call foreground service refused: ${failure.javaClass.simpleName}")
            stopUnstarted(startId)
            CallRuntime.onForegroundStartFailed(call.callId, withMicrophone)
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    /**
     * Stops after a start command that cannot be honoured. Every
     * startForegroundService() must still be answered with startForeground(),
     * even right before stopping: otherwise Android kills the whole app with
     * "Context.startForegroundService() did not then call Service.startForeground()".
     */
    private fun stopUnstarted(startId: Int) {
        if (!inForeground) {
            runCatching {
                val placeholder = CallNotificationManager.placeholderNotification(this)
                when {
                    Build.VERSION.SDK_INT >= 34 -> startForeground(PLACEHOLDER_NOTIFICATION_ID, placeholder,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
                    Build.VERSION.SDK_INT >= 29 -> startForeground(PLACEHOLDER_NOTIFICATION_ID, placeholder,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                    else -> startForeground(PLACEHOLDER_NOTIFICATION_ID, placeholder)
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        val current = CallRuntime.currentSession
        if (current == null || current.phase in setOf(CallPhase.ENDED, CallPhase.FAILED)) {
            CallNotificationManager.cancel(this)
        }
    }

    companion object {
        private const val TAG = "HostCall"
        private const val PLACEHOLDER_NOTIFICATION_ID = CallNotificationManager.NOTIFICATION_ID + 1
        private const val ACTION_START = "com.callagent.host.calls.START_FGS"
        private const val ACTION_STOP = "com.callagent.host.calls.STOP_FGS"
        private const val EXTRA_CALL_ID = CallActionActivity.EXTRA_CALL_ID
        private const val EXTRA_MICROPHONE = "com.callagent.host.calls.MICROPHONE"

        /** startForegroundService() calls whose onStartCommand has not run yet. */
        private val pendingStarts = AtomicInteger(0)

        fun start(context: Context, callId: String, microphone: Boolean): Boolean = try {
            val intent = Intent(context, CallForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_MICROPHONE, microphone)
            pendingStarts.incrementAndGet()
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (_: RuntimeException) {
            pendingStarts.decrementAndGet()
            false
        }

        fun stop(context: Context) {
            // Stopping while a start is still queued makes Android kill the app
            // ("did not then call Service.startForeground()"). The queued start
            // finds its call ended and stops the service itself.
            if (pendingStarts.get() > 0) return
            context.stopService(Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
