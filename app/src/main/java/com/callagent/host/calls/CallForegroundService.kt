package com.callagent.host.calls

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/** Exists only while a Telecom call is dialing, ringing or active. */
class CallForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.prepare(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID)
        val withMicrophone = intent?.getBooleanExtra(EXTRA_MICROPHONE, false) == true
        val call = callId?.let(CallRuntime::snapshot)
        if (call == null || call.phase in setOf(CallPhase.DISCONNECTING, CallPhase.ENDED, CallPhase.FAILED)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (withMicrophone && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callId?.let { CallRuntime.onForegroundStartFailed(it, withMicrophone) }
            stopSelf(startId)
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
            CallRuntime.onForegroundReady(call.callId, withMicrophone)
        } catch (_: SecurityException) {
            CallRuntime.onForegroundStartFailed(call.callId, withMicrophone)
            stopSelf(startId)
            return START_NOT_STICKY
        } catch (_: RuntimeException) {
            CallRuntime.onForegroundStartFailed(call.callId, withMicrophone)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
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
        private const val ACTION_START = "com.callagent.host.calls.START_FGS"
        private const val ACTION_STOP = "com.callagent.host.calls.STOP_FGS"
        private const val EXTRA_CALL_ID = CallActionActivity.EXTRA_CALL_ID
        private const val EXTRA_MICROPHONE = "com.callagent.host.calls.MICROPHONE"

        fun start(context: Context, callId: String, microphone: Boolean): Boolean = try {
            val intent = Intent(context, CallForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_MICROPHONE, microphone)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (_: RuntimeException) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
