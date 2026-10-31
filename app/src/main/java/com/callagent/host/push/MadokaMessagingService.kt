package com.callagent.host.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives FCM wake-ups. A message carries no content, only what to synchronise. */
class MadokaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = PushRegistration.onNewToken(this, token)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // Wire 2 comes from nodes this phone switched to reception v2: it says
        // whether a call or messages changed.
        val wire = when (data["v"]) { null, "1" -> 1; "2" -> 2; else -> return }
        if (data["type"] != "sync") return
        android.util.Log.i("HostPush", "received wire=$wire kind=${data["kind"]} priority=${message.priority} original=${message.originalPriority}")
        PushWake.handle(applicationContext, data["kind"], data["device_id"],
            allowServiceStart = message.priority == RemoteMessage.PRIORITY_HIGH,
            actualPriority = message.priority, originalPriority = message.originalPriority, wire = wire)
    }
    override fun onDeletedMessages() {
        // A missing hint does not imply a missing durable event. Respect each pairing's opt-in.
        PushWake.recoverDeleted(applicationContext)
    }
}
