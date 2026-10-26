package com.callagent.host.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives FCM wake-ups. A message carries no content, only what to synchronise. */
class MadokaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = PushRegistration.onNewToken(this, token)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "sync" || data["v"] !in setOf(null, "1")) return
        com.callagent.host.data.StandbyDiagnostics.pushReceived(message.priority, message.originalPriority)
        android.util.Log.i("HostPush", "received priority=${message.priority} original=${message.originalPriority}")
        PushWake.handle(applicationContext, data["kind"], data["device_id"],
            allowServiceStart = message.priority == RemoteMessage.PRIORITY_HIGH)
    }
    override fun onDeletedMessages() {
        // A missing hint does not imply a missing durable event. Respect each pairing's opt-in.
        PushWake.recoverDeleted(applicationContext)
    }
}
