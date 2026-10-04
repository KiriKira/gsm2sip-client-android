package com.callagent.host.calls

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.HostSession
import com.callagent.host.data.RemoteCall
import com.callagent.host.data.SessionChanged
import com.callagent.host.data.SessionNeedsPairing
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SimLine
import com.callagent.host.data.SipConfiguration
import com.callagent.host.sip.SipCallDirection
import com.callagent.host.sip.SipCallSnapshot
import com.callagent.host.sip.SipCallState
import com.callagent.host.sip.SipEngine
import com.callagent.host.sip.SipEngineFactory
import com.callagent.host.sip.SipRegistrationState
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Short-lived service owner for SIP signaling and call control. Idle SIP is never promoted to FGS. */
class HostCallService : android.app.Service() {
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionStore by lazy { SessionStore(this) }
    private val credentialStore by lazy { SipCredentialStore(this) }
    private var sipEngine: SipEngine? = null
    private var sipConfiguration: SipConfiguration? = null
    @Volatile private var registrationState = SipRegistrationState.STOPPED
    @Volatile private var listeningRequested = false
    @Volatile private var foregroundListening = false
    private var signalingForeground = false
    private val listeningGeneration = AtomicLong(0L)
    private val networkEventGeneration = AtomicLong(0L)
    @Volatile private var engineGeneration = 0L
    @Volatile private var destroying = false
    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var networkMonitorGeneration = 0L
    private var currentDefaultNetwork: Network? = null
    private val callbackDefaultNetwork = AtomicReference<Network?>(null)
    private var networkUnavailable = false
    private var sipRetryPending = false
    private val networkRecoveryPolicy = SipNetworkRecoveryPolicy()
    private var networkLossWindow: SipNetworkRecoveryPolicy.LossWindow? = null
    private var networkLossTimeout: Runnable? = null
    private var pendingCallIntentId: String? = null
    private var pendingSipUri: String? = null
    private var pendingOutboundCallId: String? = null
    private var pendingOutboundExpiry = 0L
    private var pendingGatewayId: String? = null
    private var pendingSimId: String? = null
    private val dialStartedForCall = mutableSetOf<String>()
    private val readyWakeIds = mutableSetOf<String>()
    private val inboundEngineIds = mutableMapOf<String, String>()
    private val inboundExpiryCallbacks = ConcurrentHashMap<String, Runnable>()
    private var pendingAnswerCallId: String? = null

    private val pollPendingCalls = object : Runnable {
        override fun run() {
            if (!foregroundListening || registrationState != SipRegistrationState.REGISTERED) return
            if (CallRuntime.currentSessionForService()?.phase?.isInProgress() == true) return
            executeOnIo { preparePendingInboundCalls() }
            mainHandler.postDelayed(this, INBOUND_POLL_MILLIS)
        }
    }

    val isForegroundListening: Boolean get() = foregroundListening

    override fun onCreate() {
        super.onCreate()
        CallRuntime.attach(this)
        runCatching { CallTelecomController.ensurePhoneAccount(this) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            val persisted = runCatching { CallPreferences(this).enabledFor(sessionStore.read()) }.getOrDefault(false)
            if (persisted) setForegroundListening(true) else stopSelf(startId)
            return if (persisted) START_STICKY else START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_OUTBOUND -> {
                val gatewayId = intent.getStringExtra(EXTRA_GATEWAY_ID)
                val simId = intent.getStringExtra(EXTRA_SIM_ID)
                val simLabel = intent.getStringExtra(EXTRA_SIM_LABEL).orEmpty()
                val revision = intent.getLongExtra(EXTRA_MAPPING_REVISION, -1L)
                val destination = intent.getStringExtra(EXTRA_DESTINATION)
                if (gatewayId.isNullOrBlank() || simId.isNullOrBlank() || revision < 0L || destination.isNullOrBlank()) {
                    executeOnIo { failPendingOutbound("呼叫参数无效。") }
                } else {
                    executeOnIo { beginOutbound(gatewayId, simId, simLabel, revision, destination) }
                }
            }
            ACTION_LISTEN -> setForegroundListening(intent.getBooleanExtra(EXTRA_ENABLED, true))
            ACTION_ANSWER -> intent.getStringExtra(EXTRA_CALL_ID)?.let(::answerAfterForeground)
            ACTION_REJECT -> intent.getStringExtra(EXTRA_CALL_ID)?.let { rejectCall(it) }
            ACTION_HANGUP -> intent.getStringExtra(EXTRA_CALL_ID)?.let { hangupCall(it) }
            ACTION_STOP_IDLE -> stopIfIdle()
        }
        return if (signalingForeground || listeningRequested) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroying = true
        unregisterNetworkCallbackForDestroy()
        mainHandler.removeCallbacks(pollPendingCalls)
        inboundExpiryCallbacks.values.forEach(mainHandler::removeCallbacks)
        inboundExpiryCallbacks.clear()
        stopSignalingForeground()
        CallSignalingNotification.cancel(this)
        CallRuntime.detach(this)
        // Keep PJSUA2 teardown ordered after any call operation already on the serial worker.
        runCatching { io.execute { closeEngine() } }
        io.shutdown()
        super.onDestroy()
    }

    private fun executeOnIo(block: () -> Unit) {
        if (destroying) return
        try {
            io.execute {
                if (!destroying) block()
            }
        } catch (_: RejectedExecutionException) {
            // Service teardown won the race with a late Android/SIP callback.
        }
    }

    private fun listenerFor(generation: Long): SipEngine.Listener = object : SipEngine.Listener {
        override fun onRegistration(state: SipRegistrationState, statusCode: Int?, reason: String?) {
            executeOnIo {
                if (generation == engineGeneration) onRegistrationChanged(state, statusCode)
            }
        }

        override fun onCallState(snapshot: SipCallSnapshot) {
            executeOnIo {
                if (generation == engineGeneration) handleSipCallState(snapshot)
            }
        }

        override fun onEngineError(error: Throwable) {
            executeOnIo {
                if (generation == engineGeneration) onSipEngineError()
            }
        }
    }

    internal fun setForegroundListening(enabled: Boolean) {
        val generation = listeningGeneration.incrementAndGet()
        listeningRequested = enabled
        if (!enabled) {
            foregroundListening = false
            mainHandler.removeCallbacks(pollPendingCalls)
            stopSignalingForeground()
            if (CallRuntime.currentSessionForService()?.phase?.isInProgress() != true) {
                executeOnIo {
                    if (generation != listeningGeneration.get() || foregroundListening ||
                        CallRuntime.currentSessionForService()?.phase?.isInProgress() == true
                    ) return@executeOnIo
                    closeEngine()
                    stopSelf()
                }
            }
            return
        }
        if (foregroundListening && registrationState in setOf(SipRegistrationState.REGISTERING, SipRegistrationState.REGISTERED)) return
        foregroundListening = true
        if (CallRuntime.currentSessionForService()?.phase?.isInProgress() != true && !startSignalingForeground()) {
            foregroundListening = false
            listeningRequested = false
            CallRuntime.onListeningFailed(this, "Android 不允许启动来电信令服务。")
            executeOnIo {
                if (generation == listeningGeneration.get()) {
                    closeEngine()
                    stopSelf()
                }
            }
            return
        }
        executeOnIo {
            if (generation != listeningGeneration.get() || !foregroundListening) return@executeOnIo
            try {
                val api = apiForCurrentSession()
                val session = requireClientSession()
                val config = loadUsableSipConfiguration(api, session)
                if (generation != listeningGeneration.get() || !foregroundListening) return@executeOnIo
                if (sessionStore.read()?.sessionInstanceId != session.sessionInstanceId) throw SessionChanged()
                startEngine(config)
                if (generation == listeningGeneration.get() && registrationState == SipRegistrationState.REGISTERED) {
                    mainHandler.removeCallbacks(pollPendingCalls)
                    mainHandler.post(pollPendingCalls)
                }
            } catch (failure: Exception) {
                if (generation != listeningGeneration.get()) return@executeOnIo
                foregroundListening = false
                listeningRequested = false
                val message = readinessFailure(failure)
                CallRuntime.onListeningFailed(this@HostCallService, message)
                stopSignalingForeground()
                closeEngine()
                stopSelf()
            }
        }
    }

    internal fun stopForSessionChange() {
        listeningGeneration.incrementAndGet()
        listeningRequested = false
        foregroundListening = false
        mainHandler.removeCallbacks(pollPendingCalls)
        stopSignalingForeground()
        executeOnIo {
            val call = CallRuntime.currentSessionForService()?.takeIf { it.phase.isInProgress() }
            if (call != null) {
                val engineKey = inboundEngineIds[call.callId] ?: call.callId
                if (call.direction == CallDirection.INCOMING && call.phase == CallPhase.INCOMING_RINGING) {
                    runCatching { sipEngine?.reject(engineKey, 603) }
                } else {
                    runCatching { sipEngine?.hangup(engineKey) }
                }
                finishCall(
                    callId = call.callId,
                    failed = true,
                    failureReason = "Paired account changed",
                    statusText = "配对账户已变化，通话已结束。"
                )
            } else {
                closeEngine()
                stopSelf()
            }
        }
    }

    internal fun answerAfterForeground(callId: String) {
        pendingAnswerCallId = callId
        if (!CallForegroundService.start(this, callId, microphone = true)) {
            failCall(callId, "Android blocked microphone access for this call")
        }
    }

    internal fun onForegroundReady(callId: String, microphone: Boolean) {
        if (!microphone) return
        if (pendingAnswerCallId != callId) {
            val call = CallRuntime.snapshot(callId)
            if (call?.phase == CallPhase.ACTIVE) {
                executeOnIo {
                    if (CallRuntime.snapshot(callId)?.phase != CallPhase.ACTIVE) return@executeOnIo
                    val engineCallId = inboundEngineIds[callId] ?: callId
                    runCatching { requireSipEngine().enableAudio(engineCallId) }
                        .onFailure { failCall(callId, "Android could not enable call audio") }
                }
            }
            return
        }
        pendingAnswerCallId = null
        executeOnIo {
            val session = CallRuntime.snapshot(callId)
            val engineCallId = inboundEngineIds[callId]
            val authorizationExpired = session?.expiresAtEpochMillis?.let { it <= System.currentTimeMillis() } ?: true
            if (session?.phase != CallPhase.ANSWERING || authorizationExpired || engineCallId == null) {
                failCall(callId, "The incoming call expired before answer")
                return@executeOnIo
            }
            runCatching { requireSipEngine().answer(engineCallId) }
                .onFailure { failCall(callId, "SIP could not answer the incoming call") }
        }
    }

    internal fun rejectCall(callId: String, message: String? = null) {
        executeOnIo {
            val engineKey = inboundEngineIds[callId]
            if (engineKey != null) runCatching { sipEngine?.reject(engineKey, 603) }
            else pendingOutboundCallId?.takeIf { it == callId }?.let { runCatching { sipEngine?.hangup(it) } }
            if (message != null) CallRuntime.setStatus(message, callId)
            finishCall(callId, failed = false)
        }
    }

    internal fun hangupCall(callId: String) {
        executeOnIo {
            val engineKey = inboundEngineIds[callId] ?: callId
            runCatching { sipEngine?.hangup(engineKey) }
            val intentId = pendingCallIntentId
            if (pendingOutboundCallId == callId && intentId != null && callId !in dialStartedForCall) {
                runCatching { apiForCurrentSession().cancelCallIntent(intentId) }
            }
            finishCall(callId, failed = false)
        }
    }

    internal fun setMuted(callId: String, muted: Boolean) {
        executeOnIo {
            val engineKey = inboundEngineIds[callId] ?: callId
            runCatching { requireSipEngine().setMute(engineKey, muted) }
                .onFailure { failCall(callId, "Could not apply the call mute setting") }
        }
    }

    internal fun sendDtmf(callId: String, digits: String) {
        executeOnIo {
            val engineKey = inboundEngineIds[callId] ?: callId
            runCatching { requireSipEngine().sendDtmf(engineKey, digits) }
                .onFailure { CallRuntime.setStatus("无法发送 DTMF。", callId) }
        }
    }

    internal fun failCall(callId: String, message: String) {
        executeOnIo {
            val call = CallRuntime.snapshot(callId) ?: return@executeOnIo
            if (call.phase in setOf(CallPhase.ENDED, CallPhase.FAILED)) return@executeOnIo
            val engineKey = inboundEngineIds[callId] ?: callId
            if (call.direction == CallDirection.OUTGOING) {
                runCatching { sipEngine?.hangup(engineKey) }
            } else {
                runCatching { sipEngine?.reject(engineKey, 603) }
            }
            finishCall(callId, failed = true, failureReason = message, statusText = message)
        }
    }

    private fun beginOutbound(gatewayId: String, simId: String, simLabel: String, revision: Long, destination: String) {
        try {
            val session = requireClientSession()
            val api = apiForCurrentSession()
            val config = loadUsableSipConfiguration(api, session)
            verifyOutboundLine(api, gatewayId, simId, revision)
            val intent = api.createCallIntent(gatewayId, simId, revision, destination, UUID.randomUUID().toString())
            if (sessionStore.read()?.sessionInstanceId != session.sessionInstanceId) {
                runCatching { api.cancelCallIntent(intent.intentId) }
                throw SessionChanged()
            }
            val expiresAt = parseEpochMillis(intent.expiresAt) ?: throw IllegalStateException("Call authorization had no valid expiry")
            if (!CallRuntime.coordinator.outboundAuthorized(intent.callId, expiresAt, System.currentTimeMillis())) {
                runCatching { api.cancelCallIntent(intent.intentId) }
                return
            }
            pendingCallIntentId = intent.intentId
            pendingSipUri = intent.sipUri
            pendingOutboundCallId = intent.callId
            pendingOutboundExpiry = expiresAt
            pendingGatewayId = gatewayId
            pendingSimId = simId
            CallRuntime.setStatus("已验证 ${simLabel.ifBlank { "远程 SIM" }}，正在连接加密语音…", intent.callId)
            CallTelecomController.placeOutgoing(this, TelecomCallDetails(intent.callId, CallDirection.OUTGOING, destination, simLabel))
            if (!CallForegroundService.start(this, intent.callId, microphone = false)) {
                runCatching { api.cancelCallIntent(intent.intentId) }
                throw IllegalStateException("Android blocked the call foreground service")
            }
            stopSignalingForeground()
            if (sessionStore.read()?.sessionInstanceId != session.sessionInstanceId) {
                runCatching { api.cancelCallIntent(intent.intentId) }
                throw SessionChanged()
            }
            startEngine(config)
            if (registrationState == SipRegistrationState.REGISTERED) startAuthorizedOutbound()
        } catch (failure: Exception) {
            failPendingOutbound(readinessFailure(failure))
        }
    }

    private fun verifyOutboundLine(api: ApiClient, gatewayId: String, simId: String, revision: Long) {
        val gateway = api.listGateways().firstOrNull { it.gatewayId == gatewayId }
            ?: throw IllegalStateException("远程网关不可用。")
        if (!gateway.online) throw IllegalStateException("远程网关当前离线。")
        val (mappingRevision, lines) = api.listSims(gatewayId)
        val line: SimLine = lines.firstOrNull { it.simId == simId }
            ?: throw IllegalStateException("所选远程 SIM 已不存在，请刷新并重新选择。")
        if (!line.canSend || line.mappingRevision != revision || mappingRevision != revision) {
            throw IllegalStateException("远程 SIM 映射已变化或未确认，请刷新并重新选择。")
        }
    }

    private fun startAuthorizedOutbound() {
        val callId = pendingOutboundCallId ?: return
        if (callId in dialStartedForCall) return
        if (System.currentTimeMillis() >= pendingOutboundExpiry) {
            pendingCallIntentId?.let { id -> runCatching { apiForCurrentSession().cancelCallIntent(id) } }
            failCall(callId, "呼叫授权已过期，请重新拨号。")
            return
        }
        val uri = pendingSipUri ?: return
        if (!CallRuntime.coordinator.registrationReady(callId)) return
        dialStartedForCall += callId
        CallRuntime.setStatus("正在呼叫 ${CallRuntime.snapshot(callId)?.remoteNumber.orEmpty()}…", callId)
        CallTelecomController.setDialing(callId)
        try {
            requireSipEngine().makeCall(callId, uri)
        } catch (_: Exception) {
            failCall(callId, "SIP could not place the authorized call")
        } finally {
            pendingSipUri = null
        }
    }

    private fun preparePendingInboundCalls() {
        if (!foregroundListening || registrationState != SipRegistrationState.REGISTERED) return
        val session = runCatching { requireClientSession() }.getOrNull() ?: return
        val api = runCatching { apiForCurrentSession() }.getOrNull() ?: return
        val calls = runCatching { api.listCalls() }.getOrElse { return }
        val now = System.currentTimeMillis()
        calls.asSequence()
            .filter { it.direction in INCOMING_DIRECTIONS && it.state == "pending_wakeup" && !it.wakeNonce.isNullOrBlank() }
            .filter { parseEpochMillis(it.expiresAt)?.let { expiry -> expiry > now } == true }
            .filter { it.callId !in readyWakeIds }
            .forEach { call ->
                try {
                    val current = api.getCall(call.callId)
                    val nonce = current.wakeNonce?.takeIf { it.isNotBlank() } ?: return@forEach
                    val expiry = parseEpochMillis(current.expiresAt) ?: return@forEach
                    if (current.callId != call.callId || current.clientId != session.deviceId ||
                        current.direction !in INCOMING_DIRECTIONS || current.state != "pending_wakeup" ||
                        expiry <= System.currentTimeMillis()
                    ) return@forEach
                    api.ready(current.callId, nonce)
                    readyWakeIds += current.callId
                    CallRuntime.setStatus("服务器正在准备远程来电…", current.callId)
                } catch (_: Exception) {
                    // Expired, canceled or already consumed wakeups are resolved by the next snapshot.
                }
            }
    }

    private fun onRegistrationChanged(state: SipRegistrationState, statusCode: Int?) {
        registrationState = state
        when (state) {
            SipRegistrationState.REGISTERED -> {
                if (sipRetryPending) {
                    sipRetryPending = false
                    if (!networkUnavailable) {
                        networkRecoveryPolicy.networkRestored()
                        networkLossWindow = null
                        cancelNetworkLossTimeout()
                        val call = CallRuntime.currentSessionForService()?.takeIf {
                            it.phase == CallPhase.ACTIVE
                        }
                        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        val network = manager.activeNetwork
                        if (call != null && network != null && isValidatedNetwork(manager, network)) {
                            currentDefaultNetwork = network
                            callbackDefaultNetwork.set(network)
                            val eventGeneration = networkEventGeneration.incrementAndGet()
                            verifyCallAuthorityAfterNetworkRecovery(
                                call,
                                network,
                                networkMonitorGeneration,
                                eventGeneration
                            )
                        }
                    }
                }
                val current = CallRuntime.currentSessionForService()
                if (current?.direction == CallDirection.OUTGOING && current.phase == CallPhase.REGISTERING) {
                    startAuthorizedOutbound()
                }
                if (foregroundListening) {
                    if (signalingForeground) CallSignalingNotification.update(this, "SIP 已注册，等待远程 SIM 来电")
                    mainHandler.removeCallbacks(pollPendingCalls)
                    mainHandler.post(pollPendingCalls)
                    CallRuntime.setStatus("SIP 已注册；后台来电接收已启用。")
                }
            }
            SipRegistrationState.FAILED -> {
                val current = CallRuntime.currentSessionForService()
                when (SipRegistrationFailurePolicy.classify(statusCode)) {
                    SipRegistrationFailureKind.RETRYABLE -> {
                        sipRetryPending = true
                        val activeCall = current?.takeIf { it.phase.isInProgress() }
                        activeCall?.let { ensureNetworkLossGrace(it.callId) }
                        val callId = activeCall?.callId
                        val message = if (networkUnavailable) {
                            "网络连接中断，正在等待 SIP 自动重试…"
                        } else {
                            "SIP 暂时不可达，正在自动重试…"
                        }
                        CallRuntime.setStatus(message, callId)
                        if (signalingForeground) CallSignalingNotification.update(this, message)
                    }
                    SipRegistrationFailureKind.AUTHENTICATION_REJECTED,
                    SipRegistrationFailureKind.TERMINAL -> {
                        sipRetryPending = false
                        val reason = if (SipRegistrationFailurePolicy.classify(statusCode) ==
                            SipRegistrationFailureKind.AUTHENTICATION_REJECTED
                        ) {
                            "SIP credentials were rejected (401/403). Check the account configuration."
                        } else {
                            "SIP registration failed${statusCode?.let { " ($it)" }.orEmpty()}. Check the account configuration."
                        }
                        stopAfterTerminalRegistrationFailure(current?.takeIf { it.phase.isInProgress() }, reason)
                    }
                }
            }
            else -> if (foregroundListening) CallRuntime.setStatus("正在注册 SIP…")
        }
    }

    private fun handleSipCallState(snapshot: SipCallSnapshot) {
        if (snapshot.direction == SipCallDirection.INBOUND && snapshot.state in setOf(SipCallState.INCOMING, SipCallState.RINGING)) {
            matchIncomingInvite(snapshot)
            return
        }

        val current = CallRuntime.currentSessionForService() ?: return
        val serverCallId = when {
            current.callId == snapshot.callId -> current.callId
            inboundEngineIds[current.callId] == snapshot.callId -> current.callId
            else -> return
        }
        when (snapshot.state) {
            SipCallState.EARLY, SipCallState.RINGING -> {
                if (current.direction == CallDirection.OUTGOING) {
                    CallRuntime.coordinator.outboundRinging(serverCallId)
                    CallRuntime.setStatus("等待远程 SIM 接听…", serverCallId)
                    CallNotificationManager.showConnecting(this, CallRuntime.snapshot(serverCallId) ?: return)
                    CallTelecomController.setDialing(serverCallId)
                }
            }
            SipCallState.CONFIRMED -> {
                if (CallRuntime.coordinator.connected(serverCallId)) {
                    CallTelecomController.setActive(serverCallId)
                    CallNotificationManager.showOngoing(this, CallRuntime.snapshot(serverCallId) ?: return)
                    if (!CallForegroundService.start(this, serverCallId, microphone = true)) {
                        failCall(serverCallId, "Android could not start microphone access for this call")
                        return
                    }
                    CallRuntime.setStatus("通话中。", serverCallId)
                }
            }
            SipCallState.DISCONNECTED -> finishCall(serverCallId, failed = false)
            SipCallState.FAILED -> failCall(serverCallId, "通话失败${snapshot.terminalStatusCode?.let { "（$it）" }.orEmpty()}。")
            SipCallState.INCOMING -> Unit
        }
    }

    private fun matchIncomingInvite(snapshot: SipCallSnapshot) {
        val serverCallId = snapshot.serverCallIdHeader?.trim()?.takeIf { it.isNotBlank() }
        if (serverCallId == null) {
            runCatching { sipEngine?.reject(snapshot.callId, 603) }
            return
        }
        if (inboundEngineIds[serverCallId] == snapshot.callId) return
        var acceptedCallId: String? = null
        try {
            val session = requireClientSession()
            val api = apiForCurrentSession()
            val authority = api.getCall(serverCallId)
            if (authority.callId != serverCallId || authority.clientId != session.deviceId) {
                runCatching { sipEngine?.reject(snapshot.callId, 603) }
                return
            }
            if (!authorityStillRinging(api, authority)) {
                runCatching { sipEngine?.reject(snapshot.callId, 603) }
                return
            }
            val accepted = CallRuntime.coordinator.incomingInvite(
                invite = IncomingInviteIdentity(snapshot.dialogCallId ?: snapshot.callId, serverCallId),
                authority = CallAuthority(
                    callId = authority.callId,
                    direction = authority.direction,
                    state = authority.state,
                    simId = authority.simId,
                    remoteNumber = authority.from,
                    expiresAtEpochMillis = parseEpochMillis(authority.expiresAt) ?: 0L
                ),
                nowEpochMillis = System.currentTimeMillis()
            )
            if (!accepted) {
                runCatching { sipEngine?.reject(snapshot.callId, 603) }
                return
            }
            acceptedCallId = serverCallId
            inboundEngineIds[serverCallId] = snapshot.callId
            val call = CallRuntime.snapshot(serverCallId) ?: return
            scheduleInboundExpiry(call)
            CallRuntime.setStatus("远程 SIM 来电：${authority.simId}", serverCallId)
            CallTelecomController.addIncoming(this, TelecomCallDetails(serverCallId, CallDirection.INCOMING, authority.from, authority.simId))
            if (!CallForegroundService.start(this, serverCallId, microphone = false)) {
                failCall(serverCallId, "Android 无法显示入站通话通知。")
                return
            }
            stopSignalingForeground()
            CallNotificationManager.showIncoming(this, call)
            CallRuntime.notifyChanged(serverCallId)
        } catch (_: Exception) {
            if (acceptedCallId != null) {
                failCall(acceptedCallId, "来电处理失败，已结束。")
            } else {
                runCatching { sipEngine?.reject(snapshot.callId, 603) }
            }
        }
    }

    private fun scheduleInboundExpiry(call: CallSession) {
        inboundExpiryCallbacks.remove(call.callId)?.let(mainHandler::removeCallbacks)
        val timeout = Runnable {
            executeOnIo {
                inboundExpiryCallbacks.remove(call.callId)
                val current = CallRuntime.snapshot(call.callId) ?: return@executeOnIo
                if (current.direction != CallDirection.INCOMING ||
                    current.phase !in setOf(CallPhase.INCOMING_RINGING, CallPhase.ANSWERING) ||
                    current.expiresAtEpochMillis > System.currentTimeMillis()
                ) return@executeOnIo
                val engineCallId = inboundEngineIds[call.callId]
                if (engineCallId != null) runCatching { sipEngine?.reject(engineCallId, 408) }
                finishCall(call.callId, failed = false, statusText = "来电已超时。")
            }
        }
        inboundExpiryCallbacks[call.callId] = timeout
        mainHandler.postDelayed(timeout, (call.expiresAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    private fun authorityStillRinging(api: ApiClient, call: RemoteCall): Boolean {
        if (call.direction !in INCOMING_DIRECTIONS) return false
        if (call.state !in setOf("pending_wakeup", "ringing", "connecting")) return false
        val expiresAt = parseEpochMillis(call.expiresAt) ?: return false
        if (expiresAt <= System.currentTimeMillis()) return false
        val (_, lines) = api.listSims(call.gatewayId)
        val line = lines.firstOrNull { it.simId == call.simId } ?: return false
        return line.canSend && line.mappingRevision == call.mappingRevision
    }

    private fun finishCall(
        callId: String,
        failed: Boolean,
        failureReason: String = "Call ended",
        statusText: String? = null
    ) {
        val current = CallRuntime.snapshot(callId) ?: return
        if (current.phase in setOf(CallPhase.ENDED, CallPhase.FAILED)) return
        networkRecoveryPolicy.invalidate()
        networkLossWindow = null
        cancelNetworkLossTimeout()
        if (failed) CallRuntime.coordinator.failed(callId, failureReason) else CallRuntime.coordinator.ended(callId)
        CallTelecomController.disconnect(
            callId,
            if (failed) android.telecom.DisconnectCause.ERROR else android.telecom.DisconnectCause.REMOTE
        )
        CallNotificationManager.cancel(this)
        CallForegroundService.stop(this)
        inboundExpiryCallbacks.remove(callId)?.let(mainHandler::removeCallbacks)
        inboundEngineIds.remove(callId)
        readyWakeIds.remove(callId)
        if (pendingOutboundCallId == callId) {
            pendingOutboundCallId = null
            pendingCallIntentId = null
            pendingSipUri = null
            pendingOutboundExpiry = 0L
            pendingGatewayId = null
            pendingSimId = null
            dialStartedForCall.remove(callId)
        }
        if (pendingAnswerCallId == callId) pendingAnswerCallId = null
        CallRuntime.setStatus(statusText ?: if (failed) "通话失败。" else "通话已结束。", callId)
        if (listeningRequested && foregroundListening) {
            if (startSignalingForeground()) {
                mainHandler.removeCallbacks(pollPendingCalls)
                mainHandler.post(pollPendingCalls)
            } else {
                foregroundListening = false
                listeningRequested = false
                CallRuntime.onListeningFailed(this, "Android 无法恢复来电信令服务。")
                closeEngine()
                stopSelf()
            }
        } else {
            closeEngine()
            stopSelf()
        }
    }

    private fun failPendingOutbound(message: String) {
        val pending = CallRuntime.currentSessionForService()
        if (pending?.callId == "pending") {
            CallRuntime.coordinator.failed("pending", message)
            CallRuntime.setStatus(message, "pending")
        }
        pendingCallIntentId?.let { intentId -> runCatching { apiForCurrentSession().cancelCallIntent(intentId) } }
        pendingOutboundCallId?.let { callId ->
            CallRuntime.coordinator.failed(callId, message)
            CallTelecomController.disconnect(callId, android.telecom.DisconnectCause.ERROR)
            CallRuntime.setStatus(message, callId)
        }
        CallForegroundService.stop(this)
        pendingOutboundCallId = null
        pendingCallIntentId = null
        pendingSipUri = null
        pendingOutboundExpiry = 0L
        pendingGatewayId = null
        pendingSimId = null
        closeEngine()
        stopSelf()
    }

    private fun loadUsableSipConfiguration(api: ApiClient, session: HostSession): SipConfiguration {
        val publicConfig = api.getSipConfiguration()
        if (!publicConfig.available) throw IllegalStateException(publicConfig.reason ?: "SIP calling is not available for this account.")
        val endpoint = publicConfig.endpointId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SIP endpoint is unavailable.")
        val username = publicConfig.username?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("SIP account is unavailable.")
        val password = credentialStore.readPassword(session, endpoint, username)
            ?: run {
                val idempotencyKey = credentialStore.readPendingBootstrapKey(session)
                    ?: UUID.randomUUID().toString().also { credentialStore.writePendingBootstrapKey(session, it) }
                val rotated = try {
                    api.rotateSipCredentials(idempotencyKey)
                } catch (failure: ApiFailure) {
                    if (failure.code in TERMINAL_BOOTSTRAP_ERRORS) credentialStore.clearPendingBootstrapKey()
                    throw failure
                }
                if (!rotated.available || rotated.endpointId != endpoint || rotated.username != username || rotated.password.isNullOrBlank()) {
                    throw IllegalStateException(rotated.reason ?: "Could not issue protected SIP credentials.")
                }
                credentialStore.write(session, endpoint, username, rotated.password)
                rotated.password
            }
            ?: throw IllegalStateException("Could not issue protected SIP credentials.")
        if (password.isBlank()) throw IllegalStateException("Could not issue protected SIP credentials.")
        return publicConfig.copy(password = password)
    }

    private fun startEngine(config: SipConfiguration) {
        startNetworkMonitoring()
        val existing = sipEngine
        if (existing != null && sipConfiguration != config) {
            engineGeneration += 1L // Ignore terminal callbacks queued by the previous native endpoint.
            sipEngine = null
            sipConfiguration = null
            runCatching { existing.close() }
            registrationState = SipRegistrationState.STOPPED
        }
        if (sipEngine == null) {
            val candidate = SipEngineFactory.create(this)
            val generation = engineGeneration + 1L
            engineGeneration = generation
            candidate.start(config, listenerFor(generation))
            sipEngine = candidate
            sipConfiguration = config
        }
        if (registrationState !in setOf(SipRegistrationState.REGISTERED, SipRegistrationState.REGISTERING)) {
            registrationState = SipRegistrationState.REGISTERING
            requireSipEngine().register()
        }
    }

    private fun startNetworkMonitoring() {
        if (destroying || networkCallback != null) return
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        currentDefaultNetwork = manager.activeNetwork
        callbackDefaultNetwork.set(currentDefaultNetwork)
        val initiallyUnavailable = !isValidatedNetwork(manager, currentDefaultNetwork)
        networkUnavailable = false
        val generation = ++networkMonitorGeneration
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (destroying) return
                callbackDefaultNetwork.set(network)
                val eventGeneration = networkEventGeneration.incrementAndGet()
                executeOnIo {
                    if (generation == networkMonitorGeneration) {
                        handleDefaultNetworkAvailable(network, generation, eventGeneration)
                    }
                }
            }

            override fun onLost(network: Network) {
                if (destroying) return
                val observed = callbackDefaultNetwork.get()
                if (observed != network || !callbackDefaultNetwork.compareAndSet(observed, null)) return
                networkEventGeneration.incrementAndGet()
                executeOnIo {
                    if (generation == networkMonitorGeneration && currentDefaultNetwork == network) {
                        currentDefaultNetwork = null
                        markNetworkUnavailable()
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (destroying) return
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (!validated && callbackDefaultNetwork.get() == network) networkEventGeneration.incrementAndGet()
                executeOnIo {
                    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    if (generation != networkMonitorGeneration || currentDefaultNetwork != network ||
                        manager.activeNetwork != network
                    ) return@executeOnIo
                    if (validated) {
                        handleDefaultNetworkAvailable(network, generation, networkEventGeneration.get())
                    } else if (!networkUnavailable) {
                        markNetworkUnavailable()
                    }
                }
            }
        }
        networkCallback = callback
        try {
            manager.registerDefaultNetworkCallback(callback)
            if (destroying) {
                if (networkCallback === callback) networkCallback = null
                runCatching { manager.unregisterNetworkCallback(callback) }
                return
            }
        } catch (_: RuntimeException) {
            networkMonitorGeneration += 1L
            if (networkCallback === callback) networkCallback = null
        }
        if (initiallyUnavailable) markNetworkUnavailable()
    }

    private fun handleDefaultNetworkAvailable(
        network: Network,
        generation: Long,
        eventGeneration: Long,
        forceRecovery: Boolean = false
    ) {
        if (generation != networkMonitorGeneration) return
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (manager.activeNetwork != network) return
        val previous = currentDefaultNetwork
        val wasUnavailable = networkUnavailable
        val changed = previous != null && previous != network
        currentDefaultNetwork = network
        if (!isValidatedNetwork(manager, network)) {
            if (!networkUnavailable) markNetworkUnavailable()
            networkUnavailable = true
            return
        }

        networkUnavailable = false
        if (!sipRetryPending) {
            networkRecoveryPolicy.networkRestored()
            cancelNetworkLossTimeout()
            networkLossWindow = null
        }
        if (!forceRecovery && !wasUnavailable && !changed && registrationState != SipRegistrationState.FAILED) return

        val call = CallRuntime.currentSessionForService()?.takeIf {
            it.phase.isInProgress() && it.phase != CallPhase.DISCONNECTING
        }
        if (call != null && (wasUnavailable || changed || forceRecovery)) {
            val engineCallId = inboundEngineIds[call.callId] ?: call.callId
            runCatching { sipEngine?.suspendAudio(engineCallId) }
        }
        try {
            sipEngine?.refreshNetwork()
        } catch (_: Exception) {
            if (call != null) {
                failCall(call.callId, "Could not refresh the SIP connection after network recovery")
                return
            }
        }

        if (call != null) verifyCallAuthorityAfterNetworkRecovery(call, network, generation, eventGeneration)
    }

    private fun markNetworkUnavailable() {
        if (networkUnavailable) return
        networkUnavailable = true
        val call = CallRuntime.currentSessionForService()?.takeIf {
            it.phase.isInProgress() && it.phase != CallPhase.DISCONNECTING
        }
        call?.let { active ->
            val engineCallId = inboundEngineIds[active.callId] ?: active.callId
            runCatching { sipEngine?.suspendAudio(engineCallId) }
        }
        val window = networkRecoveryPolicy.networkLost(call?.callId, SystemClock.elapsedRealtime())
        networkLossWindow = window
        cancelNetworkLossTimeout()
        if (window != null) scheduleNetworkLossTimeout(window)
        if (foregroundListening) {
            CallRuntime.setStatus("网络连接中断，正在等待 SIP 恢复…", call?.callId)
            if (signalingForeground) CallSignalingNotification.update(this, "网络连接中断，正在尝试恢复")
        }
    }

    private fun scheduleNetworkLossTimeout(window: SipNetworkRecoveryPolicy.LossWindow) {
        val timeout = Runnable {
            executeOnIo {
                if (networkLossWindow != window) return@executeOnIo
                val current = CallRuntime.currentSessionForService()
                if (!networkRecoveryPolicy.shouldEndCall(
                        window,
                        current?.callId,
                        SystemClock.elapsedRealtime()
                    ) || current == null || current.phase == CallPhase.DISCONNECTING
                ) return@executeOnIo

                val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val active = manager.activeNetwork
                if (active != null && isValidatedNetwork(manager, active) && networkUnavailable) {
                    handleDefaultNetworkAvailable(active, networkMonitorGeneration, networkEventGeneration.get())
                    if (!networkUnavailable && !sipRetryPending) return@executeOnIo
                }

                val engineCallId = inboundEngineIds[current.callId] ?: current.callId
                if (current.direction == CallDirection.INCOMING) {
                    runCatching { sipEngine?.reject(engineCallId, 408) }
                } else {
                    runCatching { sipEngine?.hangup(engineCallId) }
                }
                finishCall(
                    current.callId,
                    failed = true,
                    failureReason = "SIP network was unavailable for more than 30 seconds",
                    statusText = "网络中断超过 30 秒，通话已结束。"
                )
            }
        }
        networkLossTimeout = timeout
        mainHandler.postDelayed(timeout, (window.deadlineElapsedMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
    }

    private fun cancelNetworkLossTimeout() {
        networkLossTimeout?.let(mainHandler::removeCallbacks)
        networkLossTimeout = null
    }

    private fun ensureNetworkLossGrace(callId: String) {
        if (networkLossWindow?.callId == callId) return
        val window = networkRecoveryPolicy.networkLost(callId, SystemClock.elapsedRealtime()) ?: return
        networkLossWindow = window
        cancelNetworkLossTimeout()
        scheduleNetworkLossTimeout(window)
    }

    private fun stopAfterTerminalRegistrationFailure(call: CallSession?, message: String) {
        foregroundListening = false
        listeningRequested = false
        mainHandler.removeCallbacks(pollPendingCalls)
        CallRuntime.onListeningFailed(this, message)
        stopSignalingForeground()
        if (call != null) {
            val engineCallId = inboundEngineIds[call.callId] ?: call.callId
            if (call.direction == CallDirection.OUTGOING && pendingOutboundCallId == call.callId &&
                call.callId !in dialStartedForCall
            ) {
                pendingCallIntentId?.let { intentId ->
                    runCatching { apiForCurrentSession().cancelCallIntent(intentId) }
                }
            }
            if (call.direction == CallDirection.INCOMING && call.phase == CallPhase.INCOMING_RINGING) {
                runCatching { sipEngine?.reject(engineCallId, 603) }
            } else {
                runCatching { sipEngine?.hangup(engineCallId) }
            }
            finishCall(call.callId, failed = true, failureReason = message, statusText = message)
        } else {
            closeEngine()
            stopSelf()
        }
    }

    private fun stopNetworkMonitoring() {
        networkMonitorGeneration += 1L
        val callback = networkCallback
        networkCallback = null
        currentDefaultNetwork = null
        callbackDefaultNetwork.set(null)
        networkUnavailable = true
        networkRecoveryPolicy.invalidate()
        networkLossWindow = null
        cancelNetworkLossTimeout()
        if (callback != null) {
            val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    private fun unregisterNetworkCallbackForDestroy() {
        networkMonitorGeneration += 1L
        val callback = networkCallback
        networkCallback = null
        callbackDefaultNetwork.set(null)
        if (callback != null) {
            val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }

    private fun verifyCallAuthorityAfterNetworkRecovery(
        call: CallSession,
        network: Network,
        generation: Long,
        eventGeneration: Long
    ) {
        if (!isCurrentRecoveredNetwork(network, generation, eventGeneration) || CallRuntime.snapshot(call.callId) == null) return
        val session = runCatching { requireClientSession() }.getOrElse {
            failCall(call.callId, "The paired session is unavailable after network recovery")
            return
        }
        val remote = try {
            apiForCurrentSession().getCall(call.callId)
        } catch (_: Exception) {
            if (isCurrentRecoveredNetwork(network, generation, eventGeneration) && CallRuntime.snapshot(call.callId) != null) {
                failCall(call.callId, "Could not verify the call after network recovery")
            }
            return
        }
        if (!isCurrentRecoveredNetwork(network, generation, eventGeneration)) return
        if (sessionStore.read()?.sessionInstanceId != session.sessionInstanceId) {
            stopForSessionChange()
            return
        }
        val current = CallRuntime.snapshot(call.callId) ?: return
        if (current.phase == CallPhase.DISCONNECTING) return
        if (remote.callId != call.callId || remote.clientId != session.deviceId || !remoteCallMatches(current, remote)) {
            val engineCallId = inboundEngineIds[call.callId] ?: call.callId
            runCatching { sipEngine?.hangup(engineCallId) }
            finishCall(
                call.callId,
                failed = true,
                failureReason = "Server no longer authorizes this call after network recovery",
                statusText = "服务器已结束或取消通话。"
            )
        } else if (current.phase == CallPhase.ACTIVE && remote.state == "active") {
            if (sipRetryPending) {
                CallRuntime.setStatus("服务器仍保持通话，正在等待 SIP 注册恢复…", current.callId)
                return
            }
            if (!CallForegroundService.start(this, current.callId, microphone = true)) {
                failCall(current.callId, "Android could not restore microphone access after network recovery")
            }
        }
    }

    private fun isCurrentRecoveredNetwork(network: Network, generation: Long, eventGeneration: Long): Boolean =
        generation == networkMonitorGeneration && eventGeneration == networkEventGeneration.get() &&
            currentDefaultNetwork == network && !networkUnavailable

    private fun remoteCallMatches(call: CallSession, remote: RemoteCall): Boolean {
        if (call.direction == CallDirection.OUTGOING && call.phase == CallPhase.REGISTERING &&
            pendingOutboundCallId == call.callId && pendingOutboundExpiry <= System.currentTimeMillis()
        ) return false
        val directionMatches = when (call.direction) {
            CallDirection.INCOMING -> remote.direction in INCOMING_DIRECTIONS
            CallDirection.OUTGOING -> remote.direction in OUTGOING_DIRECTIONS
        }
        if (!directionMatches) return false
        return when (call.phase) {
            CallPhase.INCOMING_RINGING, CallPhase.ANSWERING ->
                remote.state in setOf("pending_wakeup", "ringing", "connecting", "active")
            CallPhase.REGISTERING -> if (call.direction == CallDirection.OUTGOING) {
                remote.state in setOf("reserved", "dialing", "connecting", "active")
            } else {
                remote.state in setOf("pending_wakeup", "ringing", "connecting", "active")
            }
            CallPhase.DIALING, CallPhase.OUTBOUND_RINGING ->
                remote.state in setOf("dialing", "connecting", "active")
            CallPhase.ACTIVE -> remote.state == "active"
            else -> false
        }
    }

    private fun isValidatedNetwork(manager: ConnectivityManager, network: Network?): Boolean {
        val capabilities = network?.let { manager.getNetworkCapabilities(it) } ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun closeEngine() {
        mainHandler.removeCallbacks(pollPendingCalls)
        stopNetworkMonitoring()
        engineGeneration += 1L
        sipRetryPending = false
        val old = sipEngine
        sipEngine = null
        sipConfiguration = null
        registrationState = SipRegistrationState.STOPPED
        runCatching { old?.close() }
    }

    private fun requireSipEngine(): SipEngine = sipEngine ?: error("SIP engine unavailable")

    private fun startSignalingForeground(message: String = "正在连接 SIP…"): Boolean = try {
        val notification = CallSignalingNotification.build(this, message)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                CallSignalingNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(CallSignalingNotification.NOTIFICATION_ID, notification)
        }
        signalingForeground = true
        true
    } catch (_: RuntimeException) {
        signalingForeground = false
        false
    }

    private fun stopSignalingForeground() {
        if (!signalingForeground) return
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        signalingForeground = false
        CallSignalingNotification.cancel(this)
    }

    private fun requireClientSession(): HostSession = sessionStore.read()?.takeIf { it.role == "client" }
        ?: throw SessionNeedsPairing()

    private fun apiForCurrentSession(): ApiClient {
        val current = requireClientSession()
        return ApiClient(current.apiBaseUrl, sessionStore)
    }

    private fun onSipEngineError() {
        if (networkUnavailable || sipRetryPending || registrationState == SipRegistrationState.FAILED) {
            if (registrationState == SipRegistrationState.FAILED) {
                sipRetryPending = true
                CallRuntime.currentSessionForService()?.takeIf { it.phase.isInProgress() }
                    ?.let { ensureNetworkLossGrace(it.callId) }
            }
            val message = if (networkUnavailable) {
                "网络连接中断，正在等待 SIP 恢复…"
            } else {
                "SIP 暂时不可达，正在自动重试…"
            }
            CallRuntime.setStatus(message)
            return
        }
        val current = CallRuntime.currentSessionForService()
        if (current?.phase?.isInProgress() == true) failCall(current.callId, "加密 SIP 通话连接失败。")
        else if (foregroundListening) {
            foregroundListening = false
            listeningRequested = false
            CallRuntime.onListeningFailed(this, "SIP 引擎不可用，来电接收已关闭。")
            stopSignalingForeground()
            closeEngine()
            stopSelf()
        }
    }

    private fun readinessFailure(failure: Exception): String = when (failure) {
        is SessionNeedsPairing -> "配对会话已失效。请重新配对后拨号。"
        is SessionChanged -> "配对账户已变化，请刷新后重试。"
        is ApiFailure -> when (failure.code) {
            "gateway_offline" -> "远程网关当前离线。"
            "sim_mapping_changed", "stale_mapping_revision" -> "SIM 映射已变化，请刷新并重新选择。"
            "gateway_busy", "call_busy" -> "网关正在通话。请稍后重试。"
            "calling_not_ready", "sip_unavailable" -> "服务器通话服务尚不可用。"
            else -> "服务器拒绝此次呼叫（${failure.code}）。"
        }
        else -> failure.message?.takeIf { it in SAFE_READINESS_MESSAGES } ?: "通话服务暂不可用，请刷新服务器状态后重试。"
    }

    private fun parseEpochMillis(value: String?): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

    private fun stopIfIdle() {
        if (CallRuntime.currentSessionForService()?.phase?.isInProgress() == true || foregroundListening) return
        val generation = listeningGeneration.get()
        executeOnIo {
            if (generation != listeningGeneration.get() || foregroundListening ||
                CallRuntime.currentSessionForService()?.phase?.isInProgress() == true
            ) return@executeOnIo
            closeEngine()
            stopSelf()
        }
    }

    private fun CallPhase.isInProgress(): Boolean = this !in setOf(CallPhase.IDLE, CallPhase.ENDED, CallPhase.FAILED)

    companion object {
        private const val ACTION_OUTBOUND = "com.callagent.host.calls.OUTBOUND"
        private const val ACTION_LISTEN = "com.callagent.host.calls.LISTEN"
        private const val ACTION_ANSWER = "com.callagent.host.calls.ANSWER"
        private const val ACTION_REJECT = "com.callagent.host.calls.REJECT_SERVICE"
        private const val ACTION_HANGUP = "com.callagent.host.calls.HANGUP_SERVICE"
        private const val ACTION_STOP_IDLE = "com.callagent.host.calls.STOP_IDLE"
        private const val EXTRA_GATEWAY_ID = "com.callagent.host.calls.GATEWAY_ID"
        private const val EXTRA_SIM_ID = "com.callagent.host.calls.SIM_ID"
        private const val EXTRA_SIM_LABEL = "com.callagent.host.calls.SIM_LABEL"
        private const val EXTRA_MAPPING_REVISION = "com.callagent.host.calls.MAPPING_REVISION"
        private const val EXTRA_DESTINATION = "com.callagent.host.calls.DESTINATION"
        private const val EXTRA_CALL_ID = CallActionActivity.EXTRA_CALL_ID
        private const val EXTRA_ENABLED = "com.callagent.host.calls.ENABLED"
        private const val INBOUND_POLL_MILLIS = 3_000L
        private val INCOMING_DIRECTIONS = setOf("incoming", "inbound")
        private val OUTGOING_DIRECTIONS = setOf("outgoing", "outbound")
        private val SAFE_READINESS_MESSAGES = setOf(
            "远程网关不可用。", "远程网关当前离线。", "所选远程 SIM 已不存在，请刷新并重新选择。",
            "远程 SIM 映射已变化或未确认，请刷新并重新选择。", "SIP endpoint is unavailable.",
            "SIP account is unavailable.", "Call authorization had no valid expiry", "Android blocked the call foreground service"
        )
        private val TERMINAL_BOOTSTRAP_ERRORS = setOf("SIP_BOOTSTRAP_EXPIRED", "IDEMPOTENCY_KEY_REUSED")

        fun startOutbound(
            context: Context,
            gatewayId: String,
            simId: String,
            simLabel: String,
            mappingRevision: Long,
            destination: String
        ): Boolean = start(context, Intent(context, HostCallService::class.java)
            .setAction(ACTION_OUTBOUND)
            .putExtra(EXTRA_GATEWAY_ID, gatewayId)
            .putExtra(EXTRA_SIM_ID, simId)
            .putExtra(EXTRA_SIM_LABEL, simLabel)
            .putExtra(EXTRA_MAPPING_REVISION, mappingRevision)
            .putExtra(EXTRA_DESTINATION, destination))

        fun setForegroundListening(context: Context, enabled: Boolean): Boolean = start(context,
            Intent(context, HostCallService::class.java).setAction(ACTION_LISTEN).putExtra(EXTRA_ENABLED, enabled),
            foreground = enabled)

        fun rejectFromNotification(context: Context, callId: String) {
            start(context, Intent(context, HostCallService::class.java).setAction(ACTION_REJECT).putExtra(EXTRA_CALL_ID, callId))
        }

        fun hangupFromNotification(context: Context, callId: String) {
            start(context, Intent(context, HostCallService::class.java).setAction(ACTION_HANGUP).putExtra(EXTRA_CALL_ID, callId))
        }

        private fun start(context: Context, intent: Intent, foreground: Boolean = false): Boolean = try {
            if (foreground) ContextCompat.startForegroundService(context.applicationContext, intent)
            else context.applicationContext.startService(intent)
            true
        } catch (_: RuntimeException) {
            false
        }
    }
}
