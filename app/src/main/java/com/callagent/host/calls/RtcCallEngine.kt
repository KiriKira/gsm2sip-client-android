package com.callagent.host.calls

import android.content.Context
import android.util.Log
import com.callagent.rtcmedia.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Server call control and native WebRTC media. */
internal class RtcCallEngine(
    private val context: Context,
    private val api: MediaApi,
    private val listener: CallEngine.Listener,
    private val pcmForCall: ((String) -> PcmBridge?)? = null,
    /** This node's FCM wake-ups are registered for the current token. */
    private val pushReady: () -> Boolean = { false },
    private val sessionInstanceId: String? = null,
    private val sessionIsCurrent: () -> Boolean = { true },
    private val listeningEnabled: () -> Boolean = { true }
) : CallEngine {
    private val control = RtcCallControl(api)
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it,"RTC-Call-Control") }
    private val closed = AtomicBoolean()
    private val started = AtomicBoolean()
    private val wakeLease = RtcSignalingWakeLease(context)
    private val lifecycle = Any()
    private val budgetGuard = Any()
    private val activeBudgets = mutableSetOf<com.callagent.host.data.RequestBudget>()
    private val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
    private fun hasNetwork() = connectivity.activeNetwork != null
    private fun hasLiveCall() = media.isNotEmpty() || delivered.values.any { it !in TERMINAL }
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) { wake() }
        override fun onLost(network: android.net.Network) {
            if (!hasNetwork() && !hasLiveCall()) wakeLease.pause()
            wake()
        }
    }
    private var networkRegistered = false
    private val media = ConcurrentHashMap<String,RtcAudioSession>()
    private val snapshots = ConcurrentHashMap<String,JSONObject>()
    private val delivered = ConcurrentHashMap<String,EngineCallState>()
    private val terminalSeenAt = ConcurrentHashMap<String, Long>()
    private val audioAllowed = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var lastControl = System.nanoTime()
    private var lastCapability = 0L
    @Volatile private var ready = false
    private val polling = CoalescedCallPoll(worker,
        { com.callagent.host.data.StandbyDiagnostics.withCause("rtc") { poll() } },
        { nextPollDelay() })
    private var failedPolls=0

    override fun connect() {
        if (!started.compareAndSet(false,true)) return
        networkRegistered = runCatching { connectivity.registerDefaultNetworkCallback(networkCallback); true }.getOrDefault(false)
        if (hasNetwork()) updateContinuousLease()
        polling.wake()
    }
    /** Coalesce every hint with the current fetch; each engine is node/session scoped. */
    override fun wake() = polling.wake()

    private fun updateContinuousLease() {
        when {
            hasLiveCall() -> wakeLease.renew(if (media.isEmpty()) RtcSignalingWakeLease.Reason.RINGING else RtcSignalingWakeLease.Reason.LIVE_CALL)
            !hasNetwork() || !listeningEnabled() -> wakeLease.releaseContinuous()
            failedPolls >= 3 && StandbyPowerPolicy.releaseDuringFailure(context) -> wakeLease.releaseContinuous()
            else -> wakeLease.renew(RtcSignalingWakeLease.Reason.COMPAT_IDLE)
        }
    }
    /**
     * A live call keeps the fast cadence. Idle, a new invitation arrives as a
     * WebSocket wake hint, so polling only refreshes the RTC capability (the
     * server routes calls to it for 90 s) and backs up a lost hint; HTTPS every
     * 750 ms kept the radio busy and the phone hot, worst on a weak signal.
     */
    private fun nextPollDelay():Long=idlePollDelay(
        live = hasLiveCall(),
        wakeChannelLive = CallRuntime.wakeChannelLive,
        pushReady = runCatching(pushReady).getOrDefault(false),
        failedPolls = failedPolls)
    private fun poll() {
        if(closed.get())return
        if (!sessionIsCurrent()) { close(); return }
        if (!listeningEnabled() && !hasLiveCall()) {
            wakeLease.pause()
            sessionInstanceId?.let { com.callagent.host.push.PushWakeHandoff.consumed(it, com.callagent.host.push.PushWakeHandoff.Channel.CALL) }
            return
        }
        if (!hasNetwork() && !hasLiveCall() && networkRegistered) {
            wakeLease.pause()
            lastCapability = 0L
            sessionInstanceId?.let { com.callagent.host.push.PushWakeHandoff.consumed(it, com.callagent.host.push.PushWakeHandoff.Channel.CALL) }
            return
        }
        com.callagent.host.data.StandbyDiagnostics.event(if (hasLiveCall()) "rtc_poll_live" else "rtc_poll_idle")
        com.callagent.host.mesh.NodeMonitor.maintain(context)
        val budget = com.callagent.host.data.RequestBudget.until(android.os.SystemClock.elapsedRealtime() + CONTROL_BUDGET_MS)
        synchronized(budgetGuard) {
            if (closed.get()) { budget.close(); return }
            activeBudgets.add(budget)
        }
        val work = wakeLease.acquire(RtcSignalingWakeLease.Reason.CONTROL_WORK, CONTROL_BUDGET_MS)
        sessionInstanceId?.let { com.callagent.host.push.PushWakeHandoff.consumed(it, com.callagent.host.push.PushWakeHandoff.Channel.CALL) }
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val environment = com.callagent.host.network.NetworkEnvironment.token
        try { budget.run {
            updateContinuousLease()
            val now=System.nanoTime()
            if(now-lastCapability>30_000_000_000L){
                control.capability(true);lastCapability=now
                com.callagent.host.data.StandbyDiagnostics.event("rtc_capability")
            }
            val all=control.calls()
            if (closed.get() || !sessionIsCurrent()) return@run
            if(!ready){ready=true;listener.onConnectionState(EngineConnectionState.READY)}
            all.forEach { snapshot ->
                val id=snapshot.getString("call_id")
                // An application restart cannot restore native media or replay
                // an outgoing dial. Only a live incoming invitation is new.
                if(!snapshots.containsKey(id) && !liveInvitation(snapshot))return@forEach
                if(snapshots[id]==null && snapshot.getString("direction")!="incoming")return@forEach
                store(id,snapshot);report(id)
            }
            // A truncated list is not a terminal event. Confirm any missing
            // live ID individually, so a missed end cannot pin 750ms polling.
            val present = all.map { it.getString("call_id") }.toSet()
            delivered.filter { (id, state) -> state !in TERMINAL && id !in present }.keys.forEach { id ->
                budget.remainingMillis()
                try { store(id, control.get(id)); report(id) }
                catch (error: MediaApiException) {
                    if (error.status == 404) fail(id, "control_call_gone") else throw error
                }
            }
            // Terminal IDs still returned by the server remain fenced; older
            // IDs need no full snapshots or native resources in this engine.
            val retired = delivered.filter { (id, state) -> state in TERMINAL && id !in present }.keys
            retired.forEach { snapshots.remove(it); audioAllowed.remove(it) }
            val nowMs = android.os.SystemClock.elapsedRealtime()
            terminalSeenAt.filterValues { nowMs - it >= 3_600_000L }.keys.forEach { id ->
                synchronized(lifecycle) {
                    if (delivered[id] in TERMINAL) {
                        delivered.remove(id); snapshots.remove(id); terminalSeenAt.remove(id)
                    }
                }
            }
            lastControl=System.nanoTime();failedPolls=0
            sessionInstanceId?.let {
                com.callagent.host.mesh.NodeMonitor.controlSucceeded(it,
                    android.os.SystemClock.elapsedRealtime() - startedAt, environment)
            }
            com.callagent.host.data.StandbyDiagnostics.event("rtc_poll_succeeded")
            lastPollSucceededAt=android.os.SystemClock.elapsedRealtime()
            sessionInstanceId?.let { id -> synchronized(pollEvidence) { pollEvidence[id] = lastPollSucceededAt } }
        } }catch(_:Throwable){
            if (closed.get()) return
            com.callagent.host.data.StandbyDiagnostics.event("rtc_poll_failed")
            com.callagent.host.network.NetworkEnvironment.failed()
            com.callagent.host.mesh.NodeMonitor.requestFailover(context)
            // Idle polls are seconds apart, so time alone would fail on one miss.
            if(++failedPolls>=3 && System.nanoTime()-lastControl>15_000_000_000L){
                media.values.forEach{it.enableAudio(false)}
                snapshots.keys.forEach{fail(it,"control_authorization_unavailable")}
                ready=false
                listener.onConnectionState(EngineConnectionState.FAILED,503,"RTC control unavailable")
            }
        } finally {
            // Work completion cannot release the independent live-call owner.
            budget.close()
            synchronized(budgetGuard) { activeBudgets.remove(budget) }
            try { updateContinuousLease() } finally { wakeLease.release(work) }
        }
    }
    /** Keeps the newest snapshot: the poll and an operation race on separate threads. */
    private fun store(id:String,snapshot:JSONObject){
        snapshots.merge(id,snapshot){old,new->if(new.optLong("state_revision",Long.MAX_VALUE)>=old.optLong("state_revision",0))new else old}
    }
    private fun direction(snapshot:JSONObject)=if(snapshot.getString("direction")=="incoming")EngineCallDirection.INBOUND else EngineCallDirection.OUTBOUND
    private fun report(id:String){
        val snapshot=snapshots[id]?:return
        val state=when {
            snapshot.optBoolean("end_requested") || snapshot.getString("state")=="ended" -> EngineCallState.DISCONNECTED
            snapshot.getString("state")=="unknown" -> EngineCallState.FAILED
            snapshot.getString("state")=="active" -> {
                val rtc=media[id]?:return
                if(!rtc.isConnected || !rtc.cellularActive)return
                EngineCallState.CONNECTED
            }
            snapshot.getString("direction")=="incoming" -> EngineCallState.INCOMING
            snapshot.getString("state")=="ringing" -> EngineCallState.RINGING
            else -> EngineCallState.EARLY
        }
        // Never step back from a terminal state: a poll that read the call
        // before a hangup can report it after, and an "early" state then
        // restarted the outgoing ringback on an ended call.
        val retired = synchronized(lifecycle) {
            if (closed.get()) return
            val previous = delivered[id]
            if (previous == state) return
            if (previous in TERMINAL && state !in TERMINAL) return
            delivered[id] = state
            if (state in TERMINAL) {
                terminalSeenAt.putIfAbsent(id, android.os.SystemClock.elapsedRealtime())
                audioAllowed.remove(id)
                media.remove(id)
            } else null
        }
        retired?.close()
        updateContinuousLease()
        Log.i("GsmRtc","media=webrtc call_control=rtc call_id=$id event=control_${state.name.lowercase()}")
        listener.onCallState(EngineCallSnapshot(id,direction(snapshot),state,serverCallId=id,
            terminalReason=snapshot.optString("reason").takeIf{it.isNotBlank()}))
    }
    private fun startMedia(id:String){
        val admission=RtcAudioSession.select(api,id)
        check(admission.getString("call_control")=="rtc"){"Server call control required"}
        val rtc=RtcAudioSession(context,admission,api,false,pcmForCall?.invoke(id),onReady={report(id)},
            onFailure={reason->handleMediaFailure(id,reason)})
        synchronized(lifecycle) {
            if (closed.get() || delivered[id] in TERMINAL) { rtc.close(); return }
            if (media.putIfAbsent(id,rtc) != null) { rtc.close(); error("Media already exists") }
            try {
                rtc.enableAudio(id in audioAllowed)
                rtc.start()
            } catch (error: Throwable) {
                media.remove(id, rtc)
                rtc.close()
                throw error
            }
        }
        updateContinuousLease()
    }
    private fun handleMediaFailure(id:String,reason:String){
        runCatching{worker.execute{
            // A normal remote hangup closes media authorization immediately;
            // its control snapshot can arrive after the faster media poll.
            // Confirm that terminal intent before reporting a media failure.
            if(reason=="server_authorization_closed"){
                val current=runCatching{control.get(id)}.getOrNull()
                if(current!=null){
                    store(id,current)
                    val latest=snapshots[id]
                    if(latest?.optBoolean("end_requested")==true || latest?.optString("state")=="ended"){
                        report(id)
                        return@execute
                    }
                }
            }
            fail(id,reason)
            runCatching{control.operation(id,"hangup")}
        }}
    }
    private fun fail(id:String,reason:String){
        val snapshot=snapshots[id]?:return
        val retired = synchronized(lifecycle) {
            if (closed.get() || delivered[id] in TERMINAL) return
            delivered[id] = EngineCallState.FAILED
            terminalSeenAt.putIfAbsent(id, android.os.SystemClock.elapsedRealtime())
            audioAllowed.remove(id)
            media.remove(id)
        }
        retired?.close()
        updateContinuousLease()
        Log.w("GsmRtc","media=webrtc call_control=rtc call_id=$id event=failed reason=$reason")
        listener.onCallState(EngineCallSnapshot(id,direction(snapshot),EngineCallState.FAILED,serverCallId=id,
            terminalReason=reason))
    }
    private fun <T> controlAction(block: () -> T): T {
        check(!closed.get() && sessionIsCurrent()) { "Call engine retired" }
        val budget = com.callagent.host.data.RequestBudget.until(android.os.SystemClock.elapsedRealtime() + CONTROL_BUDGET_MS)
        synchronized(budgetGuard) {
            if (closed.get()) { budget.close(); error("Call engine retired") }
            activeBudgets.add(budget)
        }
        val work = wakeLease.acquire(RtcSignalingWakeLease.Reason.CONTROL_WORK, CONTROL_BUDGET_MS)
        try { return budget.run(block) } finally {
            budget.close()
            synchronized(budgetGuard) { activeBudgets.remove(budget) }
            try { updateContinuousLease() } finally { wakeLease.release(work) }
        }
    }

    override fun dial(serverCallId:String,destination:String) = controlAction {
        store(serverCallId,control.get(serverCallId))
        startMedia(serverCallId)
        try {
            store(serverCallId,control.operation(serverCallId,"start"))
            report(serverCallId)
            wake()
        } catch (error: Throwable) { fail(serverCallId,"dial_setup_failed"); throw error }
    }
    override fun answer(callId:String) = controlAction {
        store(callId,control.operation(callId,"accept"))
        try { startMedia(callId); wake() }
        catch (error: Throwable) { fail(callId,"answer_setup_failed"); throw error }
    }
    override fun reject(callId:String,reason:CallRejection) = controlAction {
        try { store(callId,control.operation(callId,"reject"));report(callId) }
        catch (error: Throwable) { fail(callId,"local_reject_unconfirmed"); throw error }
    }
    override fun hangup(callId:String) = controlAction {
        media[callId]?.enableAudio(false)
        try { store(callId,control.operation(callId,"hangup"));report(callId) }
        catch (error: Throwable) { fail(callId,"local_hangup_unconfirmed"); throw error }
    }
    override fun sendDtmf(callId:String,digits:String)=checkNotNull(media[callId]).sendDtmf(digits)
    override fun setMute(callId:String,muted:Boolean)=checkNotNull(media[callId]).mute(muted)
    override fun enableAudio(callId:String){audioAllowed.add(callId);media[callId]?.enableAudio(true)}
    override fun suspendAudio(callId:String){audioAllowed.remove(callId);media[callId]?.enableAudio(false)}
    override fun refreshNetwork()=wake()
    internal companion object {
        internal fun liveInvitation(snapshot: JSONObject): Boolean {
            if (snapshot.optString("state") != "ringing" || snapshot.optBoolean("end_requested")) return false
            val expires = snapshot.optString("expires_at").takeIf { it.isNotBlank() } ?: return true
            return runCatching { java.time.Instant.parse(expires).isAfter(java.time.Instant.now()) }.getOrDefault(false)
        }

        private val TERMINAL=setOf(EngineCallState.DISCONNECTED,EngineCallState.FAILED)
        private const val LIVE_POLL_MS=750L
        private const val CONTROL_BUDGET_MS=15_000L
        // A ringing call expires 25 s after the gateway offers it; a lost wake
        // must still leave most of that window to ring and answer.
        private const val IDLE_POLL_MS=8_000L
        private const val IDLE_POLL_WITHOUT_WAKE_MS=3_000L
        /** With FCM wake-ups registered on this node, polling is only the last backup. */
        private const val IDLE_POLL_WITH_PUSH_MS=30_000L
        private const val IDLE_POLL_WITH_PUSH_WITHOUT_WAKE_MS=10_000L

        /** When call control last answered, on the elapsed-realtime clock. */
        @Volatile internal var lastPollSucceededAt = 0L

        private val pollEvidence = object : LinkedHashMap<String, Long>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 128
        }
        internal fun lastSuccessfulPoll(sessionId: String): Long = synchronized(pollEvidence) { pollEvidence[sessionId] ?: 0L }

        /** Longest wait between idle polls that keep failing. */
        private const val FAILED_POLL_MAX_MS=60_000L

        /**
         * Without push the cadence is exactly as before, so a node without FCM
         * costs nothing extra. Idle polls that keep failing (a weak signal, a
         * node that is down) back off to a minute: retrying every 3 s kept the
         * radio and, through the signaling lease, the CPU awake for nothing.
         * A network change or a wake hint still polls at once.
         */
        internal fun idlePollDelay(live: Boolean, wakeChannelLive: Boolean, pushReady: Boolean, failedPolls: Int = 0): Long {
            if (live) return LIVE_POLL_MS
            val base = when {
                wakeChannelLive -> if (pushReady) IDLE_POLL_WITH_PUSH_MS else if (com.callagent.host.BuildConfig.STANDBY_SLOW_RTC) 12_000L else IDLE_POLL_MS
                else -> if (pushReady) IDLE_POLL_WITH_PUSH_WITHOUT_WAKE_MS else if (com.callagent.host.BuildConfig.STANDBY_SLOW_RTC) 5_000L else IDLE_POLL_WITHOUT_WAKE_MS
            }
            if (failedPolls <= 0) return base
            return (base shl failedPolls.coerceAtMost(5)).coerceAtMost(maxOf(base, FAILED_POLL_MAX_MS))
        }
    }
    override fun close(){
        if(!closed.compareAndSet(false,true))return
        polling.close()
        synchronized(budgetGuard) { activeBudgets.forEach { it.close() }; activeBudgets.clear() }
        wakeLease.close()
        com.callagent.host.mesh.NodeMonitor.maintain(context, force = true)
        if (networkRegistered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        worker.shutdownNow()
        val retired = synchronized(lifecycle) {
            media.values.toList().also { media.clear(); snapshots.clear(); delivered.clear(); terminalSeenAt.clear(); audioAllowed.clear() }
        }
        retired.forEach { it.close() }
    }
}
