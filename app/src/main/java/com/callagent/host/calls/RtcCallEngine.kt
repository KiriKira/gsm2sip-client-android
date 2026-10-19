package com.callagent.host.calls

import android.content.Context
import android.util.Log
import com.callagent.rtcmedia.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Own call control and native media. This class never creates a SIP engine,
 * registers an endpoint, reads SIP credentials or dials a SIP URI. */
internal class RtcCallEngine(
    private val context: Context,
    private val api: MediaApi,
    private val listener: CallEngine.Listener,
    private val pcmForCall: ((String) -> PcmBridge?)? = null
) : CallEngine {
    private val control = RtcCallControl(api)
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it,"RTC-Call-Control") }
    private val closed = AtomicBoolean()
    private val started = AtomicBoolean()
    private val wakeLease = RtcSignalingWakeLease(context)
    private val media = ConcurrentHashMap<String,RtcAudioSession>()
    private val snapshots = ConcurrentHashMap<String,JSONObject>()
    private val delivered = ConcurrentHashMap<String,EngineCallState>()
    private val audioAllowed = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var lastControl = System.nanoTime()
    private var lastCapability = 0L
    @Volatile private var ready = false

    override fun connect() {
        if (!started.compareAndSet(false,true)) return
        wakeLease.renew()
        worker.scheduleWithFixedDelay(::poll,0,750,TimeUnit.MILLISECONDS)
    }
    private fun poll() {
        if(closed.get())return
        try {
            wakeLease.renew()
            val now=System.nanoTime()
            if(now-lastCapability>25_000_000_000L){control.capability(true);lastCapability=now}
            val all=control.calls();lastControl=System.nanoTime()
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
            if(System.nanoTime()-lastControl>15_000_000_000L){
                media.values.forEach{it.enableAudio(false)}
                snapshots.keys.forEach{fail(it,"control_authorization_unavailable")}
                ready=false
                listener.onConnectionState(EngineConnectionState.FAILED,503,"RTC control unavailable")
            }
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
        Log.i("GsmRtc","media=webrtc call_control=rtc call_id=$id event=control_${state.name.lowercase()}")
        listener.onCallState(EngineCallSnapshot(id,direction(snapshot),state,serverCallId=id,transportCallId=id,
            terminalReason=snapshot.optString("reason").takeIf{it.isNotBlank()},mediaTransport=CallMediaTransport.WEBRTC,
            controlTransport=CallControlTransport.RTC))
    }
    private fun startMedia(id:String){
        val admission=RtcAudioSession.select(api,id)
        check(admission.getString("call_control")=="rtc"){"Independent RTC control required"}
        val rtc=RtcAudioSession(context,admission,api,false,pcmForCall?.invoke(id),onReady={report(id)},onFailure={reason->
            fail(id,reason)
            runCatching{worker.execute{runCatching{control.operation(id,"hangup")}}}
        })
        check(media.putIfAbsent(id,rtc)==null)
        rtc.enableAudio(id in audioAllowed);rtc.start()
    }
    private fun fail(id:String,reason:String){
        val snapshot=snapshots[id]?:return
        if(delivered.put(id,EngineCallState.FAILED) in setOf(EngineCallState.FAILED,EngineCallState.DISCONNECTED))return
        media.remove(id)?.close();audioAllowed.remove(id)
        Log.w("GsmRtc","media=webrtc call_control=rtc call_id=$id event=failed reason=$reason")
        listener.onCallState(EngineCallSnapshot(id,direction(snapshot),EngineCallState.FAILED,serverCallId=id,
            terminalReason=reason,mediaTransport=CallMediaTransport.WEBRTC,controlTransport=CallControlTransport.RTC))
    }
    override fun dial(serverCallId:String,destination:String){
        check(!closed.get())
        store(serverCallId,control.get(serverCallId))
        startMedia(serverCallId)
        store(serverCallId,control.operation(serverCallId,"start"))
        report(serverCallId)
    }
    override fun answer(callId:String){
        store(callId,control.operation(callId,"accept"))
        startMedia(callId)
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
    override fun refreshNetwork(){worker.execute(::poll)}
    private companion object { val TERMINAL=setOf(EngineCallState.DISCONNECTED,EngineCallState.FAILED) }
    override fun close(){
        if(!closed.compareAndSet(false,true))return
        wakeLease.close()
        worker.shutdownNow();media.values.forEach{it.close()};media.clear()
    }
}
