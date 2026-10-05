package com.callagent.host.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_SIGNALING) {
            CallRuntime.disableForegroundListening(context)
            return
        }
        val callId = intent.getStringExtra(CallActionActivity.EXTRA_CALL_ID)?.takeIf { it.isNotBlank() } ?: return
        when (intent.action) {
            ACTION_REJECT -> CallRuntime.rejectFromNotification(context, callId)
            ACTION_HANGUP -> CallRuntime.hangupFromNotification(context, callId)
            ACTION_STOP_SIGNALING -> CallRuntime.disableForegroundListening(context)
        }
    }

    companion object {
        const val ACTION_REJECT = "com.callagent.host.calls.REJECT"
        const val ACTION_HANGUP = "com.callagent.host.calls.HANGUP"
        const val ACTION_STOP_SIGNALING = "com.callagent.host.calls.STOP_SIGNALING"
    }
}
