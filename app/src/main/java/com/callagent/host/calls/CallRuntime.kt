package com.callagent.host.calls

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.callagent.host.data.HostSession
import com.callagent.host.data.SessionStore

/** Process-wide call owner. Activities render its snapshots; no Activity owns a SIP dialog. */
object CallRuntime {
    const val ACTION_STATE_CHANGED = "com.callagent.host.calls.STATE_CHANGED"
    internal const val EXTRA_CHANGED_CALL_ID = "com.callagent.host.calls.CHANGED_CALL_ID"
    internal val coordinator = CallSessionCoordinator()

    @Volatile
    private var service: HostCallService? = null

    @Volatile
    private var appVisible = false

    @Volatile
    private var wantsForegroundListening = false

    @Volatile
    private var rememberedSessionInstance: String? = null

    @Volatile
    private var statusText: String = ""

    val currentCallId: String? get() = coordinator.current?.callId?.takeUnless { it == "pending" }
    val currentSession: CallSession? get() = coordinator.current
    val currentStatus: String get() = statusText
    val isForegroundListening: Boolean get() = service?.isForegroundListening == true
    val isListeningRequested: Boolean get() = wantsForegroundListening

    fun restoreForegroundPreference(context: Context, session: HostSession?) {
        val nextSessionInstance = session?.sessionInstanceId
        val previousSessionInstance = rememberedSessionInstance
        if (previousSessionInstance != null && previousSessionInstance != nextSessionInstance) {
            wantsForegroundListening = false
            CallPreferences(context).clear()
            service?.stopForSessionChange()
        }
        SipCredentialStore(context).clearForDifferentSession(session)
        wantsForegroundListening = CallPreferences(context).enabledFor(session)
        rememberedSessionInstance = nextSessionInstance
    }

    fun snapshot(callId: String): CallSession? = coordinator.current?.takeIf { it.callId == callId }

    fun clearTerminal(callId: String) {
        if (coordinator.clearTerminal(callId)) notifyChanged(callId)
    }

    internal fun updateEndedNotice(callId: String, notice: String) {
        synchronized(coordinator) {
            if (!coordinator.updateEndedNotice(callId, notice)) return
            statusText = notice
            notifyChanged(callId)
        }
    }

    fun startOutbound(
        context: Activity,
        gatewayId: String,
        simId: String,
        simLabel: String,
        mappingRevision: Long,
        destination: String
    ): Boolean {
        if (context.isFinishing || context.isDestroyed) return false
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        if (!coordinator.beginOutbound(simId, destination)) return false
        statusText = "正在检查远程 SIM 和 SIP 服务…"
        notifyChanged("pending")
        val accepted = HostCallService.startOutbound(
            context.applicationContext,
            gatewayId = gatewayId,
            simId = simId,
            simLabel = simLabel,
            mappingRevision = mappingRevision,
            destination = destination
        )
        if (!accepted) {
            coordinator.failed("pending", "Could not start the call service")
            statusText = "Android 未允许启动通话服务。"
            notifyChanged("pending")
        }
        return accepted
    }

    fun enableForegroundListening(activity: Activity): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val session = SessionStore(activity.applicationContext).read() ?: return false
        if (!CallPreferences(activity).setEnabled(session, true)) return false
        wantsForegroundListening = true
        statusText = "正在启动后台 SIP 来电接收…"
        notifyChanged(currentCallId)
        val started = HostCallService.setForegroundListening(activity.applicationContext, true)
        if (!started) {
            wantsForegroundListening = false
            CallPreferences(activity).setEnabled(session, false)
        }
        return started
    }

    fun disableForegroundListening(context: Context) {
        val session = SessionStore(context.applicationContext).read()
        CallPreferences(context).setEnabled(session, false)
        wantsForegroundListening = false
        if (service != null) service?.setForegroundListening(false)
        else HostCallService.setForegroundListening(context.applicationContext, false)
        statusText = "前台来电接收已关闭。"
        notifyChanged(currentCallId)
    }

    fun setAppVisible(context: Context, visible: Boolean) {
        appVisible = visible
        if (visible && wantsForegroundListening) {
            if (service != null) service?.setForegroundListening(true)
            else HostCallService.setForegroundListening(context.applicationContext, true)
        }
    }

    fun answerFromVisibleUser(context: Context, callId: String): Boolean {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val host = service ?: return false
        if (!coordinator.answer(callId)) {
            expireIncomingAnswerIfNeeded(host, callId)
            return false
        }
        statusText = "正在建立加密语音…"
        notifyChanged(callId)
        host.answerAfterForeground(callId)
        return true
    }

    internal fun onTelecomAnswer(callId: String) {
        val app = service ?: return
        if (app.applicationContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            rejectFromService(callId, "Microphone permission is not granted")
            return
        }
        if (!coordinator.answer(callId)) {
            expireIncomingAnswerIfNeeded(app, callId)
            return
        }
        statusText = "正在建立加密语音…"
        notifyChanged(callId)
        app.answerAfterForeground(callId)
    }

    private fun expireIncomingAnswerIfNeeded(host: HostCallService, callId: String) {
        val call = snapshot(callId) ?: return
        if (call.direction == CallDirection.INCOMING && call.phase == CallPhase.INCOMING_RINGING &&
            call.expiresAtEpochMillis <= System.currentTimeMillis()
        ) {
            host.rejectCall(callId, "来电已超时。")
        }
    }

    fun rejectFromUi(context: Context, callId: String) = reject(callId, context)

    fun rejectFromNotification(context: Context, callId: String) = reject(callId, context)

    private fun reject(callId: String, context: Context) {
        val call = snapshot(callId) ?: return
        if (call.phase != CallPhase.INCOMING_RINGING) return
        if (!coordinator.disconnect(callId)) return
        statusText = "正在拒接…"
        notifyChanged(callId)
        service?.rejectCall(callId) ?: HostCallService.rejectFromNotification(context, callId)
    }

    fun hangupFromUi(context: Context, callId: String) = hangup(callId, context)

    fun hangupFromNotification(context: Context, callId: String) = hangup(callId, context)

    private fun hangup(callId: String, context: Context) {
        val call = snapshot(callId) ?: return
        if (call.phase in setOf(CallPhase.ENDED, CallPhase.FAILED, CallPhase.DISCONNECTING)) return
        if (!coordinator.disconnect(callId)) return
        statusText = "正在结束通话…"
        notifyChanged(callId)
        service?.hangupCall(callId) ?: HostCallService.hangupFromNotification(context, callId)
    }

    fun setMuted(context: Context, callId: String, muted: Boolean) {
        if (!coordinator.setMuted(callId, muted)) return
        service?.setMuted(callId, muted)
        notifyChanged(callId)
    }

    fun toggleSpeaker(context: Context, callId: String, enabled: Boolean) {
        val endpoint = if (enabled) CallAudioEndpoint.SPEAKER else CallAudioEndpoint.EARPIECE
        selectAudioEndpoint(context, callId, endpoint)
    }

    fun audioEndpoints(callId: String): List<CallAudioEndpoint> = CallTelecomController.audioEndpoints(callId)

    fun selectedAudioEndpoint(callId: String): CallAudioEndpoint? = CallTelecomController.currentAudioEndpoint(callId)

    fun selectAudioEndpoint(context: Context, callId: String, endpoint: CallAudioEndpoint) {
        CallTelecomController.selectAudioEndpoint(callId, endpoint)
    }

    fun sendDtmf(context: Context, callId: String, digits: String) {
        val normalized = digits.filter { it in "0123456789*#ABCDabcd" }.uppercase().take(32)
        if (normalized.isNotEmpty()) service?.sendDtmf(callId, normalized)
    }

    internal fun onTelecomReject(callId: String) {
        val host = service ?: return
        reject(callId, host.applicationContext)
    }

    internal fun onTelecomDisconnect(callId: String) {
        val host = service ?: return
        hangup(callId, host.applicationContext)
    }

    internal fun onTelecomHold(callId: String, hold: Boolean) = Unit

    internal fun onTelecomAudioRoute(callId: String, endpoint: CallAudioEndpoint?) {
        coordinator.setSpeaker(callId, endpoint == CallAudioEndpoint.SPEAKER)
        notifyChanged(callId)
    }

    internal fun onTelecomMute(callId: String, muted: Boolean) {
        if (coordinator.setMuted(callId, muted)) service?.setMuted(callId, muted)
    }

    internal fun onTelecomDtmf(callId: String, digit: Char) = service?.sendDtmf(callId, digit.toString())

    internal fun onTelecomConnectionCreated(callId: String) {
        notifyChanged(callId)
    }

    internal fun onForegroundReady(callId: String, microphone: Boolean) {
        service?.onForegroundReady(callId, microphone)
    }

    internal fun onForegroundStartFailed(callId: String, microphone: Boolean) {
        service?.failCall(callId, if (microphone) "Android blocked microphone access for this call" else "Android blocked the call notification service")
    }

    internal fun onListeningFailed(context: Context, message: String) {
        val session = SessionStore(context.applicationContext).read()
        CallPreferences(context).setEnabled(session, false)
        wantsForegroundListening = false
        statusText = message
        notifyChanged(currentCallId)
    }

    internal fun onTelecomIncomingFailed(callId: String) {
        service?.failCall(callId, "Telecom could not add the incoming call")
    }

    internal fun onTelecomOutgoingFailed(callId: String) {
        service?.failCall(callId, "Telecom could not place the call")
    }

    internal fun attach(hostCallService: HostCallService) {
        service = hostCallService
    }

    /** Wake an already-owned call service without starting SIP or another foreground service. */
    internal fun requestCallSyncFromWakeHint() {
        service?.requestCallSyncFromWakeHint()
    }

    internal fun detach(hostCallService: HostCallService) {
        if (service === hostCallService) service = null
    }

    internal fun publishCall(call: CallSession, text: String? = null) {
        statusText = text.orEmpty()
        notifyChanged(call.callId)
    }

    internal fun setStatus(text: String, callId: String? = currentCallId) {
        statusText = text
        notifyChanged(callId)
    }

    internal fun notifyChanged(callId: String?) {
        val host = service?.applicationContext ?: return
        host.sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(host.packageName)
                .putExtra(EXTRA_CHANGED_CALL_ID, callId)
        )
    }

    internal fun rejectFromService(callId: String, reason: String) {
        service?.rejectCall(callId, reason)
    }

    internal fun startMicrophoneForeground(context: Context, callId: String): Boolean =
        CallForegroundService.start(context, callId, microphone = true)

    internal fun currentSessionForService(): CallSession? = coordinator.current

}
