package com.callagent.host.calls

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import java.util.concurrent.ConcurrentHashMap

internal data class TelecomCallDetails(
    val callId: String,
    val direction: CallDirection,
    val remoteNumber: String?,
    val simLabel: String
)

/** Registers one self-managed account and submits only calls already authorized/matched by HTTPS. */
internal object CallTelecomController {
    private const val ACCOUNT_ID = "gsm2sip-remote-voice"
    internal const val EXTRA_CALL_ID = "com.callagent.host.calls.CALL_ID"
    internal const val EXTRA_DIRECTION = "com.callagent.host.calls.DIRECTION"
    internal const val EXTRA_NUMBER = "com.callagent.host.calls.REMOTE_NUMBER"
    internal const val EXTRA_SIM_LABEL = "com.callagent.host.calls.SIM_LABEL"
    private val liveConnections = ConcurrentHashMap<String, HostCallConnection>()
    private val audioStateByCall = ConcurrentHashMap<String, CallAudioState>()

    fun ensurePhoneAccount(context: Context): PhoneAccountHandle {
        val app = context.applicationContext
        val component = ComponentName(app, HostCallConnectionService::class.java)
        val handle = PhoneAccountHandle(component, ACCOUNT_ID)
        val telecom = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val account = PhoneAccount.builder(handle, "Madoka 远程 SIM")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .setSupportedUriSchemes(listOf("gsm2sip"))
            .build()
        telecom.registerPhoneAccount(account)
        return handle
    }

    fun addIncoming(context: Context, details: TelecomCallDetails) {
        require(details.direction == CallDirection.INCOMING)
        val handle = ensurePhoneAccount(context)
        val extras = Bundle().apply { putDetails(details) }
        (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).addNewIncomingCall(handle, extras)
    }

    fun placeOutgoing(context: Context, details: TelecomCallDetails) {
        require(details.direction == CallDirection.OUTGOING)
        val app = context.applicationContext
        val handle = ensurePhoneAccount(app)
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            // Telecom forwards this nested Bundle as ConnectionRequest.extras;
            // custom keys on the outer placeCall Bundle are not forwarded.
            putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, Bundle().apply { putDetails(details) })
        }
        val address = Uri.fromParts("gsm2sip", details.callId, null)
        try {
            (app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).placeCall(address, extras)
        } catch (denied: SecurityException) {
            throw IllegalStateException("Android Telecom rejected the self-managed outgoing call", denied)
        }
    }

    private fun Bundle.putDetails(details: TelecomCallDetails) {
        putString(EXTRA_CALL_ID, details.callId)
        putString(EXTRA_DIRECTION, details.direction.name)
        putString(EXTRA_NUMBER, details.remoteNumber)
        putString(EXTRA_SIM_LABEL, details.simLabel)
    }

    internal fun onCreated(connection: HostCallConnection) {
        liveConnections[connection.callId] = connection
        CallRuntime.onTelecomConnectionCreated(connection.callId)
    }

    internal fun setActive(callId: String) {
        liveConnections[callId]?.setActive()
    }

    internal fun setRinging(callId: String) {
        liveConnections[callId]?.setRinging()
    }

    internal fun setDialing(callId: String) {
        liveConnections[callId]?.setDialing()
    }

    internal fun disconnect(callId: String, cause: Int = DisconnectCause.REMOTE) {
        val connection = liveConnections.remove(callId) ?: return
        audioStateByCall.remove(callId)
        connection.setDisconnected(DisconnectCause(cause))
        connection.destroy()
    }

    internal fun allConnections(): List<HostCallConnection> = liveConnections.values.toList()

    internal fun audioEndpoints(callId: String): List<CallAudioEndpoint> {
        val state = audioStateByCall[callId] ?: return emptyList()
        return buildList {
            if (state.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) add(CallAudioEndpoint.EARPIECE)
            if (state.supportedRouteMask and CallAudioState.ROUTE_SPEAKER != 0) add(CallAudioEndpoint.SPEAKER)
            if (state.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(CallAudioEndpoint.WIRED_HEADSET)
            if (state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) add(CallAudioEndpoint.BLUETOOTH)
        }
    }

    internal fun currentAudioEndpoint(callId: String): CallAudioEndpoint? =
        audioStateByCall[callId]?.route?.toEndpoint()

    /** Returns false when no live Telecom connection can take the route. */
    internal fun selectAudioEndpoint(callId: String, endpoint: CallAudioEndpoint): Boolean {
        val route = when (endpoint) {
            // Leaving the speaker returns to a plugged-in headset when there is one.
            CallAudioEndpoint.EARPIECE -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
            CallAudioEndpoint.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallAudioEndpoint.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            CallAudioEndpoint.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
        }
        val connection = liveConnections[callId] ?: return false
        connection.setAudioRoute(route)
        return true
    }

    internal fun onAudioState(callId: String, state: CallAudioState) {
        val previous = audioStateByCall.put(callId, state)
        if (previous?.isMuted != state.isMuted) CallRuntime.onTelecomMute(callId, state.isMuted)
        CallRuntime.onTelecomAudioRoute(callId, state.route.toEndpoint())
        CallRuntime.notifyChanged(callId)
    }

    internal fun onMuteState(callId: String, muted: Boolean) {
        audioStateByCall.computeIfPresent(callId) { _, old ->
            CallAudioState(muted, old.route, old.supportedRouteMask)
        }
        CallRuntime.onTelecomMute(callId, muted)
    }
}

class HostCallConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val details = request?.extras?.toDetails() ?: return Connection.createFailedConnection(
            DisconnectCause(DisconnectCause.ERROR, "Missing call details")
        )
        if (details.direction != CallDirection.INCOMING || CallRuntime.currentCallId != details.callId) {
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED, "Call expired"))
        }
        return HostCallConnection(this, details).also {
            it.setRinging()
            CallTelecomController.onCreated(it)
        }
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val details = request?.extras?.toDetails() ?: return Connection.createFailedConnection(
            DisconnectCause(DisconnectCause.ERROR, "Missing call details")
        )
        if (details.direction != CallDirection.OUTGOING || CallRuntime.currentCallId != details.callId) {
            return Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED, "Call expired"))
        }
        return HostCallConnection(this, details).also {
            it.setDialing()
            CallTelecomController.onCreated(it)
        }
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        request?.extras?.getString(CallTelecomController.EXTRA_CALL_ID)?.let(CallRuntime::onTelecomIncomingFailed)
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        request?.extras?.getString(CallTelecomController.EXTRA_CALL_ID)?.let(CallRuntime::onTelecomOutgoingFailed)
    }

    private fun Bundle.toDetails(): TelecomCallDetails? {
        val callId = getString(CallTelecomController.EXTRA_CALL_ID)?.takeIf { it.isNotBlank() } ?: return null
        val direction = runCatching { CallDirection.valueOf(getString(CallTelecomController.EXTRA_DIRECTION).orEmpty()) }
            .getOrNull() ?: return null
        return TelecomCallDetails(
            callId = callId,
            direction = direction,
            remoteNumber = getString(CallTelecomController.EXTRA_NUMBER),
            simLabel = getString(CallTelecomController.EXTRA_SIM_LABEL).orEmpty()
        )
    }
}

internal class HostCallConnection(
    private val context: Context,
    val details: TelecomCallDetails
) : Connection() {
    val callId: String get() = details.callId

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        connectionCapabilities = CAPABILITY_MUTE
        setAudioModeIsVoip(true)
        setAddress(Uri.fromParts("tel", details.remoteNumber.orEmpty(), null), TelecomManager.PRESENTATION_ALLOWED)
        setCallerDisplayName(details.simLabel, TelecomManager.PRESENTATION_ALLOWED)
        setInitializing()
    }

    override fun onAnswer() = CallRuntime.onTelecomAnswer(callId)

    /**
     * Telecom lets this self-managed app show its incoming-call UI (no other
     * app's call is in the way). The UI is the CallStyle notification with a
     * full-screen intent: Android opens the call screen on a locked or idle
     * phone and shows a heads-up with Answer/Decline on an unlocked one.
     * Starting the Activity directly works only while Madoka is on screen;
     * from the background Android blocks it, so that is done only then.
     */
    override fun onShowIncomingCallUi() {
        if (details.direction != CallDirection.INCOMING || state != STATE_RINGING) return
        val call = CallRuntime.snapshot(callId)?.takeIf { it.phase == CallPhase.INCOMING_RINGING } ?: return
        if (CallRuntime.isAppVisible) context.startCallUi(callId)
        else CallNotificationManager.ensureIncoming(context, call)
    }

    override fun onAnswer(videoState: Int) = onAnswer()

    override fun onReject() = CallRuntime.onTelecomReject(callId)

    override fun onDisconnect() = CallRuntime.onTelecomDisconnect(callId)

    override fun onHold() = CallRuntime.onTelecomHold(callId, true)

    override fun onUnhold() = CallRuntime.onTelecomHold(callId, false)

    override fun onCallAudioStateChanged(state: CallAudioState) {
        CallTelecomController.onAudioState(callId, state)
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        CallTelecomController.onMuteState(callId, isMuted)
    }

    override fun onPlayDtmfTone(c: Char) {
        CallRuntime.onTelecomDtmf(callId, c)
    }
}

enum class CallAudioEndpoint {
    EARPIECE,
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH;

    fun displayName(): String = when (this) {
        EARPIECE -> "听筒"
        SPEAKER -> "扬声器"
        WIRED_HEADSET -> "有线耳机"
        BLUETOOTH -> "蓝牙"
    }
}

private fun Int.toEndpoint(): CallAudioEndpoint? = when (this) {
    CallAudioState.ROUTE_EARPIECE -> CallAudioEndpoint.EARPIECE
    CallAudioState.ROUTE_SPEAKER -> CallAudioEndpoint.SPEAKER
    CallAudioState.ROUTE_WIRED_HEADSET -> CallAudioEndpoint.WIRED_HEADSET
    CallAudioState.ROUTE_BLUETOOTH -> CallAudioEndpoint.BLUETOOTH
    else -> null
}

internal fun Context.startCallUi(callId: String) {
    val intent = Intent(this, CallActionActivity::class.java)
        .setAction(CallActionActivity.ACTION_SHOW_CALL)
        .putExtra(CallActionActivity.EXTRA_CALL_ID, callId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    startActivity(intent)
}
