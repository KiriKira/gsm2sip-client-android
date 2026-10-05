package com.callagent.host.sip

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.callagent.host.data.SipConfiguration
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.AuthCredInfoVector
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.CallInfo
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.CallSetting
import org.pjsip.pjsua2.CodecParam
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.IpChangeParam
import org.pjsip.pjsua2.LogConfig
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import org.pjsip.pjsua2.StringVector
import org.pjsip.pjsua2.TlsConfig
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pjmedia_srtp_keying_method
import org.pjsip.pjsua2.pjmedia_srtp_use
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsip_transport_type_e
import org.pjsip.pjsua2.pjsua_call_media_status
import java.net.IDN
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/** PJSUA2-backed SIP engine. It creates only a TLS signalling transport. */
class Pjsua2SipEngine(context: Context) : SipEngine {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val calls = ConcurrentHashMap<String, ManagedCall>()
    private val closed = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private val nativeOperationLock = Any()
    private val registration = AtomicReference(SipRegistrationState.STOPPED)
    private val adaptation = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "gsm2sip-opus-stats").apply { isDaemon = true }
    }

    @Volatile private var endpoint: Endpoint? = null
    @Volatile private var account: EngineAccount? = null
    @Volatile private var listener: SipEngine.Listener? = null
    @Volatile private var transportId: Int = -1
    @Volatile private var audioDeviceOpened = false
    @Volatile private var hasStarted = false

    override fun start(config: SipConfiguration, listener: SipEngine.Listener) {
        synchronized(lifecycleLock) {
            check(!closed.get()) { "SIP engine is closed" }
            check(!hasStarted) { "SIP engine has already started" }
            this.listener = listener
            val checked = ValidatedConfig.from(config)
            val roots = trustedRootsPem(checked.customCaPem)

            val ep = Endpoint()
            try {
                ep.libCreate()
                val epConfig = EpConfig()
                epConfig.uaConfig.threadCnt = 1
                epConfig.uaConfig.maxCalls = 1
                epConfig.uaConfig.userAgent = "GSM2SIP-Android/0.3"
                epConfig.logConfig = LogConfig().apply {
                    level = 0L
                    consoleLevel = 0L
                    msgLogging = 0L
                }
                ep.libInit(epConfig)

                val tls = TlsConfig().apply {
                    caBuf = roots
                    verifyServer = true
                }
                val tlsTransport = TransportConfig().apply {
                    port = 0L
                    tlsConfig = tls
                }
                transportId = ep.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, tlsTransport)
                ep.libStart()
                // PJSUA2 must not open a real microphone or speaker during startup or REGISTER.
                ep.audDevManager().setNullDev()

                configureCodecs(ep)
                val accountConfig = makeAccountConfig(checked, transportId)
                val sipAccount = EngineAccount()
                try {
                    sipAccount.create(accountConfig, true)
                } finally {
                    accountConfig.delete()
                }

                endpoint = ep
                account = sipAccount
                hasStarted = true
                startOpusStatsSampler()
            } catch (failure: Throwable) {
                runCatching { ep.libDestroy() }
                runCatching { ep.delete() }
                endpoint = null
                account = null
                transportId = -1
                throw SafeSipException("Unable to initialize the SIP engine")
            }
        }
    }

    override fun register() {
        val acc = requireAccount()
        registration.set(SipRegistrationState.REGISTERING)
        notifyRegistration(SipRegistrationState.REGISTERING, null, null)
        try {
            onPjsipThread { acc.setRegistration(true) }
        } catch (_: Throwable) {
            notifyRegistration(SipRegistrationState.FAILED, null, "Registration could not be started")
            throw SafeSipException("Unable to start SIP registration")
        }
    }

    override fun makeCall(serverCallId: String, sipUri: String) {
        require(serverCallId.isNotBlank() && serverCallId.length <= 200) { "Invalid call id" }
        validateCallIntentUri(sipUri)
        val acc = requireAccount()
        check(registration.get() == SipRegistrationState.REGISTERED) { "SIP account is not registered" }
        check(!calls.containsKey(serverCallId)) { "Call is already active" }

        try {
            onPjsipThread {
                val call = ManagedCall(acc, -1, serverCallId, SipCallDirection.OUTBOUND)
                calls[serverCallId] = call
                val operation = callParam(pjsip_status_code.PJSIP_SC_NULL)
                try {
                    call.makeCall(sipUri, operation)
                } catch (failure: Throwable) {
                    calls.remove(serverCallId, call)
                    call.delete()
                    throw failure
                } finally {
                    operation.delete()
                }
            }
        } catch (_: Throwable) {
            emitEngineError("Unable to start the SIP call")
            throw SafeSipException("Unable to start the SIP call")
        }
    }

    override fun answer(callId: String) {
        val call = requireCall(callId)
        check(call.direction == SipCallDirection.INBOUND) { "Only an incoming call can be answered" }
        try {
            onPjsipThread {
                synchronized(call.audioGateLock) {
                    // This is the first point at which incoming-call audio hardware can open.
                    openAudioDevice()
                    call.audioAllowed = true
                    call.muted = false
                    val operation = callParam(pjsip_status_code.PJSIP_SC_OK)
                    try {
                        call.answer(operation)
                    } finally {
                        operation.delete()
                    }
                    connectAudioIfReady(call)
                }
            }
        } catch (_: Throwable) {
            call.audioAllowed = false
            if (calls.size <= 1 && audioDeviceOpened) {
                runCatching { requireEndpoint().audDevManager().setNullDev() }
                audioDeviceOpened = false
            }
            emitEngineError("Unable to answer the SIP call")
            throw SafeSipException("Unable to answer the SIP call")
        }
    }

    override fun reject(callId: String, statusCode: Int) {
        require(statusCode in 300..699) { "Invalid SIP rejection status" }
        finishCall(callId, statusCode)
    }

    override fun hangup(callId: String) {
        finishCall(callId, pjsip_status_code.PJSIP_SC_OK)
    }

    override fun sendDtmf(callId: String, digits: String) {
        require(digits.isNotEmpty() && digits.length <= 64 && digits.all { it in "0123456789*#ABCDabcd" }) {
            "Invalid DTMF digits"
        }
        val call = requireCall(callId)
        try {
            onPjsipThread { call.dialDtmf(digits.uppercase()) }
        } catch (_: Throwable) {
            throw SafeSipException("Unable to send DTMF")
        }
    }

    override fun setMute(callId: String, muted: Boolean) {
        val call = requireCall(callId)
        onPjsipThread {
            synchronized(call.audioGateLock) {
                call.muted = muted
                val media = call.audioMedia
                if (call.audioAllowed && media != null && audioDeviceOpened) {
                    val capture = requireEndpoint().audDevManager().captureDevMedia
                    if (muted) runCatching { capture.stopTransmit(media) }
                    else runCatching { capture.startTransmit(media) }
                }
            }
        }
    }

    override fun enableAudio(callId: String) {
        val call = requireCall(callId)
        try {
            onPjsipThread {
                synchronized(call.audioGateLock) {
                    call.audioAllowed = true
                    if (call.state == SipCallState.CONFIRMED) {
                        openAudioDevice()
                        connectAudioIfReady(call)
                    }
                }
            }
        } catch (_: Throwable) {
            call.audioAllowed = false
            emitEngineError("Unable to enable SIP call audio")
            throw SafeSipException("Unable to enable SIP call audio")
        }
    }

    override fun suspendAudio(callId: String) {
        val call = calls[callId] ?: return
        onPjsipThread {
            synchronized(call.audioGateLock) {
                call.audioAllowed = false
                call.stopAudio()
                if (calls.values.none { it.audioAllowed } && audioDeviceOpened) {
                    requireEndpoint().audDevManager().setNullDev()
                    audioDeviceOpened = false
                }
            }
        }
    }

    /** Telecom owns Android call routes; this method intentionally does not change them. */
    override fun setAudioRoute(route: SipAudioRoute) = Unit

    override fun refreshNetwork() {
        val ep = requireEndpoint()
        try {
            onPjsipThread {
                val change = IpChangeParam().apply {
                    restartListener = true
                    shutdownTransport = true
                }
                ep.handleIpChange(change)
                change.delete()
            }
        } catch (_: Throwable) {
            emitEngineError("Unable to refresh the SIP network connection")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        adaptation.shutdownNow()
        synchronized(lifecycleLock) {
            synchronized(nativeOperationLock) {
                calls.values.toList().forEach { call ->
                    synchronized(call.audioGateLock) {
                        call.audioAllowed = false
                        runCatching { call.stopAudio() }
                    }
                }
                val ep = endpoint
                val acc = account
                if (audioDeviceOpened) runCatching { ep?.audDevManager()?.setNullDev() }
                runCatching { ep?.hangupAllCalls() }
                runCatching {
                    if (ep != null && acc != null && acc.isValid) {
                        if (!ep.libIsThreadRegistered()) ep.libRegisterThread("gsm2sip-close")
                        acc.setRegistration(false)
                    }
                }
                runCatching { ep?.libDestroy() }
                calls.values.forEach { call -> runCatching { call.delete() } }
                calls.clear()
                notifyRegistration(SipRegistrationState.STOPPED, null, null)
                runCatching { acc?.delete() }
                runCatching { ep?.delete() }
                endpoint = null
                account = null
                hasStarted = false
                audioDeviceOpened = false
                transportId = -1
                listener = null
            }
        }
    }

    private fun finishCall(callId: String, statusCode: Int) {
        val call = requireCall(callId)
        try {
            onPjsipThread {
                val operation = callParam(statusCode)
                try {
                    call.hangup(operation)
                } finally {
                    operation.delete()
                }
            }
        } catch (_: Throwable) {
            throw SafeSipException("Unable to end the SIP call")
        }
    }

    private fun configureCodecs(ep: Endpoint) {
        val enabled = listOf("opus", "PCMU", "PCMA", "G722")
        val codecs = ep.codecEnum2()
        val codecIds = codecs.map { it.codecId }
        codecs.delete()
        for (id in codecIds) {
            val normalized = id.substringBefore('/').substringBefore(' ').lowercase()
            val preference = enabled.indexOfFirst { it.equals(normalized, ignoreCase = true) }
            val priority = if (preference >= 0) (255 - preference).toShort() else 0.toShort()
            ep.codecSetPriority(id, priority)
        }

        val opusId = codecIds.firstOrNull { it.substringBefore('/').equals("opus", true) }
        if (opusId != null) {
            runCatching {
                val parameters = ep.codecGetParam(opusId)
                parameters.setting.plc = true
                parameters.setting.packetLoss = 5L
                ep.codecSetParam(opusId, parameters)
                parameters.delete()
            }
        }
    }

    private fun makeAccountConfig(config: ValidatedConfig, tlsTransportId: Int): AccountConfig {
        val result = AccountConfig()
        result.idUri = config.aor
        result.regConfig.registrarUri = config.registrarUri
        result.regConfig.registerOnAdd = false
        result.regConfig.retryIntervalSec = 30L
        result.regConfig.firstRetryIntervalSec = 5L
        result.sipConfig.transportId = tlsTransportId
        result.sipConfig.proxies = StringVector(listOf(config.proxyUri))
        result.sipConfig.authCreds = AuthCredInfoVector(
            listOf(AuthCredInfo("Digest", config.realm, config.username, 0, config.password))
        )

        val media = result.mediaConfig
        media.srtpUse = pjmedia_srtp_use.PJMEDIA_SRTP_MANDATORY
        media.srtpSecureSignaling = 2 // SRTP is accepted only for a SIPS end-to-end request.
        media.srtpOpt.keyings = org.pjsip.pjsua2.IntVector(
            listOf(pjmedia_srtp_keying_method.PJMEDIA_SRTP_KEYING_SDES)
        )
        result.mediaConfig = media

        result.ipChangeConfig.hangupCalls = false
        result.ipChangeConfig.shutdownTp = true
        return result
    }

    private fun callParam(statusCode: Int): CallOpParam {
        val operation = CallOpParam(true)
        if (statusCode != pjsip_status_code.PJSIP_SC_NULL) operation.statusCode = statusCode
        val settings = CallSetting(true)
        settings.audioCount = 1L
        settings.videoCount = 0L
        settings.textCount = 0L
        operation.opt = settings
        return operation
    }

    private fun openAudioDevice() {
        if (audioDeviceOpened) return
        requireEndpoint().audDevManager().setSndDevMode(0L)
        audioDeviceOpened = true
    }

    private fun connectAudioIfReady(call: ManagedCall) {
        if (closed.get()) return
        synchronized(call.audioGateLock) {
            if (!call.audioAllowed || closed.get()) return
            val info = runCatching { call.info }.getOrNull() ?: return
            val activeAudio = info.media.indices.firstOrNull { index ->
                val media = info.media[index]
                media.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                    (media.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE ||
                        media.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_REMOTE_HOLD)
            } ?: return
            try {
                openAudioDevice()
                val callAudio = call.getAudioMedia(activeAudio)
                call.audioMedia = callAudio
                val audio = requireEndpoint().audDevManager()
                if (!call.audioPlaybackConnected) {
                    callAudio.startTransmit(audio.playbackDevMedia)
                    call.audioPlaybackConnected = true
                }
                if (!call.muted && !call.audioCaptureConnected) {
                    audio.captureDevMedia.startTransmit(callAudio)
                    call.audioCaptureConnected = true
                }
            } catch (_: Throwable) {
                emitEngineError("Unable to connect SIP call audio")
            }
        }
    }

    private fun onCallState(call: ManagedCall) {
        if (closed.get()) return
        val info = runCatching { call.info }.getOrNull() ?: return
        val state = mapCallState(call, info)
        if (state == SipCallState.CONFIRMED) {
            synchronized(call.audioGateLock) {
                if (!closed.get() && call.audioAllowed) {
                    try {
                        openAudioDevice()
                        connectAudioIfReady(call)
                    } catch (_: Throwable) {
                        emitEngineError("Unable to open call audio")
                        runCatching {
                            val operation = callParam(pjsip_status_code.PJSIP_SC_SERVICE_UNAVAILABLE)
                            try {
                                call.hangup(operation)
                            } finally {
                                operation.delete()
                            }
                        }
                    }
                }
            }
        }
        val snapshot = call.snapshot(info, state)
        emitCallState(snapshot)

        if (state == SipCallState.DISCONNECTED || state == SipCallState.FAILED) {
            synchronized(call.audioGateLock) {
                call.audioAllowed = false
                call.stopAudio()
            }
            calls.remove(call.operationId, call)
            // Let the JNI director callback return before releasing its Java wrapper.
            mainHandler.postDelayed({ runCatching { call.delete() } }, 100L)
            if (calls.isEmpty() && audioDeviceOpened) {
                runCatching { requireEndpoint().audDevManager().setNullDev() }
                audioDeviceOpened = false
            }
        }
    }

    private fun mapCallState(call: ManagedCall, info: CallInfo): SipCallState = when (info.state) {
        pjsip_inv_state.PJSIP_INV_STATE_INCOMING -> SipCallState.INCOMING
        pjsip_inv_state.PJSIP_INV_STATE_CALLING -> SipCallState.RINGING
        pjsip_inv_state.PJSIP_INV_STATE_EARLY -> {
            if (call.direction == SipCallDirection.INBOUND && info.lastStatusCode == pjsip_status_code.PJSIP_SC_RINGING) {
                SipCallState.RINGING
            } else {
                SipCallState.EARLY
            }
        }
        pjsip_inv_state.PJSIP_INV_STATE_CONNECTING -> SipCallState.EARLY
        pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> SipCallState.CONFIRMED
        pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED ->
            if (info.lastStatusCode >= 300) SipCallState.FAILED else SipCallState.DISCONNECTED
        else -> SipCallState.EARLY
    }

    private fun onIncomingCall(param: OnIncomingCallParam) {
        val acc = account ?: return
        if (closed.get()) return
        val incoming = parseInvite(param.rdata.wholeMsg)
        val operationId = "incoming-${UUID.randomUUID()}"
        val call = ManagedCall(acc, param.callId, operationId, SipCallDirection.INBOUND).apply {
            requestUri = incoming.requestUri
            incomingDialogCallId = incoming.dialogCallId
            serverCallIdHeader = incoming.serverCallIdHeader
        }
        calls[operationId] = call

        val initialInfo = runCatching { call.info }.getOrNull()
        emitCallState(
            if (initialInfo != null) call.snapshot(initialInfo, SipCallState.INCOMING)
            else SipCallSnapshot(
                callId = operationId,
                direction = SipCallDirection.INBOUND,
                state = SipCallState.INCOMING,
                remoteUri = "",
                requestUri = incoming.requestUri,
                dialogCallId = incoming.dialogCallId,
                serverCallIdHeader = incoming.serverCallIdHeader
            )
        )

        // 180 is provisional: it creates no confirmed dialog and does not enable media.
        runCatching {
            val provisional = callParam(pjsip_status_code.PJSIP_SC_RINGING)
            try {
                call.answer(provisional)
            } finally {
                provisional.delete()
            }
        }.onFailure {
            emitEngineError("Unable to signal the incoming SIP call")
        }
    }

    private fun updateRegistration(param: OnRegStateParam) {
        if (closed.get()) return
        val success = param.status == 0 && param.code in 200..299
        val state = if (success) SipRegistrationState.REGISTERED else SipRegistrationState.FAILED
        registration.set(state)
        notifyRegistration(state, param.code.takeIf { it > 0 }, sanitizeSipReason(param.reason))
    }

    private fun startOpusStatsSampler() {
        adaptation.scheduleWithFixedDelay({
            if (closed.get()) return@scheduleWithFixedDelay
            calls.values.toList().forEach { call ->
                if (!call.audioAllowed || call.state != SipCallState.CONFIRMED) return@forEach
                runCatching { adaptOpus(call) }
            }
        }, 5, 5, TimeUnit.SECONDS)
    }

    private fun adaptOpus(call: ManagedCall) {
        val ep = endpoint ?: return
        onPjsipThread {
            val info = call.info
            for (index in info.media.indices) {
                val media = info.media[index]
                if (media.type != pjmedia_type.PJMEDIA_TYPE_AUDIO ||
                    media.status != pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
                ) continue
                val stream = call.getStreamInfo(media.index)
                if (!stream.codecName.equals("opus", ignoreCase = true)) continue

                val stats = call.getStreamStat(media.index).rtcp
                val rx = stats.rxStat
                val previous = call.lossSamples.put(media.index, LossSample(rx.loss, rx.pkt))
                val deltaLoss = if (previous == null) 0L else (rx.loss - previous.loss).coerceAtLeast(0L)
                val deltaPackets = if (previous == null) 0L else (rx.pkt - previous.packets).coerceAtLeast(0L)
                val lossPercent = if (deltaLoss + deltaPackets > 0) {
                    100.0 * deltaLoss.toDouble() / (deltaLoss + deltaPackets).toDouble()
                } else 0.0
                val jitterUsec = rx.jitterUsec.mean.coerceAtLeast(0)
                val rttUsec = stats.rttUsec.mean.coerceAtLeast(0)
                val targetBitrate = when {
                    lossPercent >= 3.0 || jitterUsec >= 180_000 || rttUsec >= 350_000 -> 16_000L
                    lossPercent >= 1.0 || jitterUsec >= 100_000 || rttUsec >= 220_000 -> 24_000L
                    else -> 32_000L
                }
                if (targetBitrate == call.lastOpusBitrate) continue

                val parameters: CodecParam = stream.audCodecParam
                parameters.info.avgBps = targetBitrate
                parameters.info.maxBps = maxOf(targetBitrate, 32_000L)
                parameters.setting.packetLoss = lossPercent.toInt().coerceIn(0, 20).toLong()
                parameters.setting.plc = true // Opus maps PLC to negotiated in-band FEC.
                call.audStreamModifyCodecParam(media.index.toInt(), parameters)
                parameters.delete()
                call.lastOpusBitrate = targetBitrate
            }
        }
    }

    private fun onPjsipThread(block: () -> Unit) {
        synchronized(nativeOperationLock) {
            check(!closed.get()) { "SIP engine is closed" }
            val ep = requireEndpoint()
            if (!ep.libIsThreadRegistered()) ep.libRegisterThread("gsm2sip-app")
            block()
        }
    }

    private fun requireEndpoint(): Endpoint = endpoint ?: error("SIP engine is not initialized")
    private fun requireAccount(): EngineAccount = account ?: error("SIP engine is not initialized")
    private fun requireCall(callId: String): ManagedCall = calls[callId] ?: error("SIP call is not active")

    private fun emitCallState(snapshot: SipCallSnapshot) {
        val target = listener ?: return
        if (closed.get()) return
        mainHandler.post { if (!closed.get()) runCatching { target.onCallState(snapshot) } }
    }

    private fun notifyRegistration(state: SipRegistrationState, statusCode: Int?, reason: String?) {
        val target = listener ?: return
        if (closed.get() && state != SipRegistrationState.STOPPED) return
        mainHandler.post { runCatching { target.onRegistration(state, statusCode, reason) } }
    }

    private fun emitEngineError(message: String) {
        val target = listener ?: return
        if (closed.get()) return
        mainHandler.post { if (!closed.get()) runCatching { target.onEngineError(SafeSipException(message)) } }
    }

    private inner class EngineAccount : Account() {
        override fun onIncomingCall(param: OnIncomingCallParam) = this@Pjsua2SipEngine.onIncomingCall(param)
        override fun onRegState(param: OnRegStateParam) {
            if (!closed.get()) updateRegistration(param)
        }
    }

    private inner class ManagedCall(
        acc: Account,
        nativeCallId: Int,
        val operationId: String,
        val direction: SipCallDirection
    ) : Call(acc, nativeCallId) {
        @Volatile var requestUri: String? = null
        @Volatile var incomingDialogCallId: String? = null
        @Volatile var serverCallIdHeader: String? = null
        val audioGateLock = Any()
        @Volatile var audioAllowed = false
        @Volatile var muted = false
        @Volatile var audioMedia: org.pjsip.pjsua2.AudioMedia? = null
        @Volatile var audioCaptureConnected = false
        @Volatile var audioPlaybackConnected = false
        @Volatile var state: SipCallState = if (direction == SipCallDirection.INBOUND) SipCallState.INCOMING else SipCallState.RINGING
        @Volatile var lastOpusBitrate = 0L
        val lossSamples = ConcurrentHashMap<Long, LossSample>()

        override fun onCallState(param: OnCallStateParam?) {
            if (closed.get()) return
            val current = runCatching { info }.getOrNull() ?: return
            state = mapCallState(this, current)
            this@Pjsua2SipEngine.onCallState(this)
        }

        override fun onCallMediaState(param: OnCallMediaStateParam?) {
            if (closed.get()) return
            connectAudioIfReady(this)
        }

        fun stopAudio() {
            val media = audioMedia ?: return
            runCatching { requireEndpoint().audDevManager().captureDevMedia.stopTransmit(media) }
            runCatching { media.stopTransmit(requireEndpoint().audDevManager().playbackDevMedia) }
            audioMedia = null
            audioCaptureConnected = false
            audioPlaybackConnected = false
        }

        fun snapshot(info: CallInfo, state: SipCallState) = SipCallSnapshot(
            callId = operationId,
            direction = direction,
            state = state,
            remoteUri = info.remoteUri,
            requestUri = requestUri,
            dialogCallId = incomingDialogCallId ?: info.callIdString,
            serverCallIdHeader = serverCallIdHeader,
            terminalStatusCode = info.lastStatusCode.takeIf { state == SipCallState.DISCONNECTED || state == SipCallState.FAILED },
            terminalReason = sanitizeSipReason(info.lastReason).takeIf { state == SipCallState.DISCONNECTED || state == SipCallState.FAILED }
        )
    }

    private data class LossSample(val loss: Long, val packets: Long)

    private class SafeSipException(message: String) : IllegalStateException(message)

    private data class ValidatedConfig(
        val username: String,
        val realm: String,
        val aor: String,
        val registrarUri: String,
        val proxyUri: String,
        val serverName: String,
        val customCaPem: String?,
        val password: String
    ) {
        companion object {
            fun from(config: SipConfiguration): ValidatedConfig {
                require(config.available) { "SIP is unavailable" }
                val username = config.username.requireValue("username")
                val realm = config.realm.requireValue("realm")
                val aor = config.aor.requireValue("AOR")
                val registrar = config.registrarUri.requireValue("registrar")
                val proxy = config.outboundProxyUri.requireValue("TLS proxy")
                val serverName = normalizeHost(config.serverName.requireValue("TLS server name"))
                val password = config.password.requireValue("credential")
                require(username.length <= 256 && username.none { it.isISOControl() })
                require(realm.length <= 253 && realm.none { it.isISOControl() })

                val parsedAor = parseSipUri(aor)
                require(parsedAor.scheme == "sips" && parsedAor.user.isNotBlank()) { "AOR must be a SIPS identity" }
                val parsedRegistrar = parseSipUri(registrar)
                require(parsedRegistrar.scheme == "sips" && hasNoOrTlsTransport(registrar)) { "Registrar must use TLS" }
                val parsedProxy = parseSipUri(proxy)
                val secureProxy = parsedProxy.transportTls ||
                    (parsedProxy.scheme == "sips" && hasNoOrTlsTransport(proxy))
                require(secureProxy && parsedProxy.user.isEmpty()) { "Proxy must use TLS" }
                require('?' !in registrar && '?' !in proxy) { "SIP configuration URIs cannot contain headers" }
                require(normalizeHost(parsedProxy.host) == serverName) {
                    "TLS server name must identify the configured outbound proxy"
                }
                val ca = config.caPem?.trim()?.takeIf { it.isNotEmpty() }
                if (ca != null) require(ca.contains("-----BEGIN CERTIFICATE-----")) { "Custom CA is not PEM" }

                return ValidatedConfig(
                    username,
                    realm,
                    aor,
                    ensureTlsParam(registrar),
                    ensureLooseRoute(ensureTlsParam(proxy)),
                    serverName,
                    ca,
                    password
                )
            }
        }
    }

    private data class SipUriParts(
        val scheme: String,
        val user: String,
        val host: String,
        val transportTls: Boolean
    )

    private data class InviteHeaders(
        val requestUri: String?,
        val dialogCallId: String?,
        val serverCallIdHeader: String?
    )

    companion object {
        private val SIP_URI = Pattern.compile(
            "^(sips?):(?:([^@;?]+)@)?(\\[[^]]+]|[^;:?]+)(?::([0-9]{1,5}))?(?:;([^?]*))?(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE
        )
        private val CALL_INTENT_URI = Pattern.compile(
            "^sips:call\\.([A-Za-z0-9._~-]{1,180})@gsm2sip;transport=tls$",
            Pattern.CASE_INSENSITIVE
        )
        private const val SERVER_CALL_HEADER = "x-gsm2sip-call-id"

        fun loadNativeLibrary(): Boolean = try {
            System.loadLibrary("pjsua2")
            true
        } catch (_: Throwable) {
            false
        }

        private fun trustedRootsPem(customCaPem: String?): String {
            val pem = StringBuilder()
            val store = KeyStore.getInstance("AndroidCAStore")
            store.load(null)
            val aliases = store.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                // AndroidCAStore contains both user-added and system roots. This app uses
                // platform system roots plus its authenticated, optional custom CA only.
                if (!alias.startsWith("system:", ignoreCase = true)) continue
                val cert = store.getCertificate(alias) as? X509Certificate ?: continue
                pem.append("-----BEGIN CERTIFICATE-----\n")
                pem.append(Base64.encodeToString(cert.encoded, Base64.NO_WRAP).chunked(64).joinToString("\n"))
                pem.append("\n-----END CERTIFICATE-----\n")
            }
            if (!customCaPem.isNullOrBlank()) {
                pem.append('\n').append(customCaPem.trim()).append('\n')
            }
            check(pem.isNotEmpty()) { "Android system CA roots are unavailable" }
            return pem.toString()
        }

        private fun parseSipUri(uri: String): SipUriParts {
            val match = SIP_URI.matcher(uri.trim())
            require(match.matches()) { "Invalid SIP URI" }
            val params = match.group(5).orEmpty().split(';').map { it.substringBefore('=').lowercase() }
            return SipUriParts(
                scheme = match.group(1).orEmpty().lowercase(),
                user = match.group(2).orEmpty(),
                host = match.group(3).orEmpty().removePrefix("[").removeSuffix("]"),
                transportTls = "transport" in params && Regex("(?:^|;)transport=tls(?:;|$)", RegexOption.IGNORE_CASE).containsMatchIn(uri)
            )
        }

        private fun validateCallIntentUri(uri: String) {
            require(CALL_INTENT_URI.matcher(uri).matches()) {
                "Call intents must use the configured SIPS token URI"
            }
        }

        private fun ensureTlsParam(uri: String): String {
            val parsed = parseSipUri(uri)
            return if (parsed.transportTls) uri else "$uri;transport=tls"
        }

        private fun hasNoOrTlsTransport(uri: String): Boolean {
            val parameters = uri.substringBefore('?').substringAfter(';', "").split(';')
            val transport = parameters.firstOrNull { it.substringBefore('=').equals("transport", true) }
                ?.substringAfter('=', "")
            return transport == null || transport.equals("tls", true)
        }

        private fun ensureLooseRoute(uri: String): String {
            val params = uri.substringAfter(';', "")
            if (params.split(';').any { it.equals("lr", true) }) return uri
            return if (';' in uri) "$uri;lr" else "$uri;lr"
        }

        private fun normalizeHost(host: String): String {
            val withoutBrackets = host.removePrefix("[").removeSuffix("]")
            require(withoutBrackets.isNotBlank() && withoutBrackets.length <= 253)
            val ascii = IDN.toASCII(withoutBrackets, IDN.USE_STD3_ASCII_RULES).trimEnd('.')
            require(ascii.isNotBlank() && ascii.split('.').all { label ->
                label.isNotEmpty() && label.length <= 63 && label.firstOrNull()?.isLetterOrDigit() == true &&
                    label.lastOrNull()?.isLetterOrDigit() == true
            }) { "Invalid TLS server name" }
            return ascii.lowercase()
        }

        private fun parseInvite(message: String): InviteHeaders {
            val lines = message.replace("\r\n", "\n").split('\n')
            val requestUri = lines.firstOrNull()?.let { first ->
                val fields = first.trim().split(Regex("\\s+"))
                fields.getOrNull(1)?.takeIf { fields.size >= 3 && fields[2].equals("SIP/2.0", true) }
            }
            val unfolded = mutableListOf<String>()
            for (line in lines.drop(1)) {
                if (line.isEmpty()) break
                if ((line.startsWith(' ') || line.startsWith('\t')) && unfolded.isNotEmpty()) {
                    unfolded[unfolded.lastIndex] = unfolded.last() + line.trim()
                } else {
                    unfolded += line
                }
            }
            val values = mutableMapOf<String, MutableList<String>>()
            for (line in unfolded) {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                values.getOrPut(line.substring(0, colon).trim().lowercase()) { mutableListOf() }
                    .add(line.substring(colon + 1).trim())
            }
            fun uniqueHeader(name: String): String? = values[name]?.singleOrNull()?.takeIf { value ->
                value.isNotEmpty() && value.length <= 256 && value.none { it.isISOControl() }
            }
            return InviteHeaders(
                requestUri = requestUri,
                dialogCallId = uniqueHeader("call-id"),
                serverCallIdHeader = uniqueHeader(SERVER_CALL_HEADER)
            )
        }

        private fun String?.requireValue(name: String): String = this?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing SIP $name")

        private fun sanitizeSipReason(reason: String?): String? = reason
            ?.replace(Regex("[\\r\\n\\u0000-\\u001f]"), " ")
            ?.trim()
            ?.take(160)
            ?.takeIf { it.isNotEmpty() }
    }
}

object SipEngineFactory {
    fun isAvailable(context: Context): Boolean = Pjsua2SipEngine.loadNativeLibrary()

    fun create(context: Context): SipEngine {
        check(isAvailable(context)) { "PJSUA2 native library is unavailable" }
        return Pjsua2SipEngine(context.applicationContext)
    }
}
