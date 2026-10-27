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
    private val pushReady: () -> Boolean = { false }
) : CallEngine {
    private val control = RtcCallControl(api)
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it,"RTC-Call-Control") }
    private val closed = AtomicBoolean()
    private val started = AtomicBoolean()
    private val wakeLease = RtcSignalingWakeLease(context)
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
        if (hasNetwork()) wakeLease.renew()
        polling.wake()
    }
    /** Coalesce every hint with the current fetch; each engine is node/session scoped. */
    override fun wake() = polling.wake()

    private fun updateContinuousLease() {
        when {
            hasLiveCall() -> wakeLease.renew(RtcSignalingWakeLease.Reason.LIVE_CALL)
            !hasNetwork() -> wakeLease.releaseContinuous()
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
        if (!hasNetwork() && !hasLiveCall() && networkRegistered) {
            wakeLease.pause()
            lastCapability = 0L
            return
        }
        com.callagent.host.mesh.NodeMonitor.maintain(context)
        val work = wakeLease.acquire(RtcSignalingWakeLease.Reason.CONTROL_WORK)
        try {
            updateContinuousLease()
            val now=System.nanoTime()
            if(now-lastCapability>30_000_000_000L){control.capability(true);lastCapability=now}
            val all=control.calls();lastControl=System.nanoTime();failedPolls=0
            lastPollSucceededAt=android.os.SystemClock.elapsedRealtime()
            if(!ready){ready=true;listener.onConnectionState(EngineConnectionState.READY)}
            all.forEach { snapshot ->
                val id=snapshot.getString("call_id")
                // An application restart cannot restore native media or replay
                // an outgoing dial. Only a live incoming invitation is new.
                if(!snapshots.containsKey(id) && snapshot.getString("state")!="ringing")return@forEach
                if(snapshots[id]==null && snapshot.getString("direction")!="incoming")return@forEach
                store(id,snapshot);report(id)
            }
        }catch(_:Throwable){
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
            updateContinuousLease()
            wakeLease.release(work)
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
        val previous=delivered[id]
        if(previous==state)return
        if(previous in TERMINAL && state !in TERMINAL)return
        delivered[id]=state
        if(state==EngineCallState.DISCONNECTED || state==EngineCallState.FAILED){media.remove(id)?.close();audioAllowed.remove(id)}
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
        check(media.putIfAbsent(id,rtc)==null)
        rtc.enableAudio(id in audioAllowed);rtc.start()
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
        var newlyFailed=false
        delivered.compute(id){_,previous->
            if(previous in TERMINAL)previous else EngineCallState.FAILED.also{newlyFailed=true}
        }
        if(!newlyFailed)return
        media.remove(id)?.close();audioAllowed.remove(id)
        updateContinuousLease()
        Log.w("GsmRtc","media=webrtc call_control=rtc call_id=$id event=failed reason=$reason")
        listener.onCallState(EngineCallSnapshot(id,direction(snapshot),EngineCallState.FAILED,serverCallId=id,
            terminalReason=reason))
    }
    override fun dial(serverCallId:String,destination:String){
        check(!closed.get())
        store(serverCallId,control.get(serverCallId))
        startMedia(serverCallId)
        store(serverCallId,control.operation(serverCallId,"start"))
        report(serverCallId)
        wake()
    }
    override fun answer(callId:String){
        store(callId,control.operation(callId,"accept"))
        startMedia(callId)
        wake()
    }
    override fun reject(callId:String,reason:CallRejection){
        store(callId,control.operation(callId,"reject"));report(callId)
    }
    override fun hangup(callId:String){
        media[callId]?.enableAudio(false)
        store(callId,control.operation(callId,"hangup"));report(callId)
    }
    override fun sendDtmf(callId:String,digits:String)=checkNotNull(media[callId]).sendDtmf(digits)
    override fun setMute(callId:String,muted:Boolean)=checkNotNull(media[callId]).mute(muted)
    override fun enableAudio(callId:String){audioAllowed.add(callId);media[callId]?.enableAudio(true)}
    override fun suspendAudio(callId:String){audioAllowed.remove(callId);media[callId]?.enableAudio(false)}
    override fun refreshNetwork()=wake()
    internal companion object {
        private val TERMINAL=setOf(EngineCallState.DISCONNECTED,EngineCallState.FAILED)
        private const val LIVE_POLL_MS=750L
        // A ringing call expires 25 s after the gateway offers it; a lost wake
        // must still leave most of that window to ring and answer.
        private const val IDLE_POLL_MS=8_000L
        private const val IDLE_POLL_WITHOUT_WAKE_MS=3_000L
        /** With FCM wake-ups registered on this node, polling is only the last backup. */
        private const val IDLE_POLL_WITH_PUSH_MS=30_000L
        private const val IDLE_POLL_WITH_PUSH_WITHOUT_WAKE_MS=10_000L

        /** When call control last answered, on the elapsed-realtime clock. */
        @Volatile internal var lastPollSucceededAt = 0L

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
        wakeLease.close()
        com.callagent.host.mesh.NodeMonitor.maintain(context, force = true)
        if (networkRegistered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        worker.shutdownNow();media.values.forEach{it.close()};media.clear()
    }
}
