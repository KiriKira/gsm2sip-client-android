package com.callagent.host.calls

import android.content.Context
import android.util.Log
import com.callagent.host.data.ApiClient
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SipConfiguration
import com.callagent.host.sip.*
import com.callagent.rtcmedia.MediaApi
import com.callagent.rtcmedia.MediaApiException
import com.callagent.rtcmedia.RtcAudioSession
import java.util.concurrent.ConcurrentHashMap

/** SIP keeps the established call authority/lifecycle. Each new call freezes
 * either its existing RTP audio or a real, independently authorized WebRTC leg. */
internal class SipCallEngine(
    private val sip: SipEngine,
    private val context: Context? = null,
    private val mediaApi: MediaApi? = null,
    private val pcmForCall: ((String) -> com.callagent.rtcmedia.PcmBridge?)? = null,
    private val requestedMode: (String?) -> CallMediaTransport = { CallMediaTransport.SIP }
) : CallEngine {
    @Volatile private var listener: CallEngine.Listener? = null
    private val modes=ConcurrentHashMap<String,CallMediaTransport>()
    private val serverIds=ConcurrentHashMap<String,String>()
    private val rtc=ConcurrentHashMap<String,RtcAudioSession>()
    private val confirmed=ConcurrentHashMap<String,EngineCallSnapshot>()
    private val connectedDelivered=ConcurrentHashMap.newKeySet<String>()
    private val audioAllowed=ConcurrentHashMap.newKeySet<String>()

    fun start(config:SipConfiguration,listener:CallEngine.Listener){
        this.listener=listener
        try {
            sip.start(config,object:SipEngine.Listener{
                override fun onRegistration(state:SipRegistrationState,statusCode:Int?,reason:String?){
                    val connection=when(state){
                        SipRegistrationState.STOPPED->EngineConnectionState.STOPPED
                        SipRegistrationState.REGISTERING->EngineConnectionState.CONNECTING
                        SipRegistrationState.REGISTERED->EngineConnectionState.READY
                        SipRegistrationState.FAILED->EngineConnectionState.FAILED
                    }
                    listener.onConnectionState(connection,statusCode,reason)
                }
                override fun onCallState(snapshot:SipCallSnapshot){handle(snapshot)}
                override fun onEngineError(error:Throwable)=listener.onEngineError(error)
            })
        }catch(failure:Throwable){runCatching{sip.close()};throw failure}
    }
    private fun handle(snapshot:SipCallSnapshot){
        if(snapshot.state==SipCallState.INCOMING)modes.putIfAbsent(snapshot.callId,requestedMode(null))
        snapshot.serverCallIdHeader?.let{serverIds[snapshot.callId]=it}
        val mode=modes[snapshot.callId]?:CallMediaTransport.SIP
        val state=when(snapshot.state){
            SipCallState.INCOMING->EngineCallState.INCOMING
            SipCallState.RINGING->EngineCallState.RINGING
            SipCallState.EARLY->EngineCallState.EARLY
            SipCallState.CONFIRMED->EngineCallState.CONNECTED
            SipCallState.DISCONNECTED->EngineCallState.DISCONNECTED
            SipCallState.FAILED->EngineCallState.FAILED
        }
        val mapped=EngineCallSnapshot(snapshot.callId,
            if(snapshot.direction==SipCallDirection.INBOUND)EngineCallDirection.INBOUND else EngineCallDirection.OUTBOUND,
            state,snapshot.serverCallIdHeader,snapshot.dialogCallId,snapshot.terminalStatusCode,snapshot.terminalReason,mode)
        if(context!=null)Log.i("GsmRtc","media=${mode.label.lowercase()} call_control=sip call_id=${serverIds[snapshot.callId]?:snapshot.callId} event=control_${state.name.lowercase()}")
        if(state==EngineCallState.CONNECTED && mode==CallMediaTransport.WEBRTC){
            confirmed[snapshot.callId]=mapped;reportConnected(snapshot.callId);return
        }
        if(state in setOf(EngineCallState.DISCONNECTED,EngineCallState.FAILED)){
            rtc.remove(snapshot.callId)?.close();confirmed.remove(snapshot.callId);audioAllowed.remove(snapshot.callId)
            connectedDelivered.remove(snapshot.callId)
        }
        listener?.onCallState(mapped)
    }
    private fun reportConnected(key:String){
        val snapshot=confirmed[key]?:return
        val media=rtc[key]?:return
        if(media.isConnected && media.cellularActive && connectedDelivered.add(key))listener?.onCallState(snapshot)
    }
    private fun startRtc(key:String,serverCallId:String,direction:EngineCallDirection){
        val app=checkNotNull(context){"RTC context missing"}
        check(RtcAudioSession.isAvailable(app)){"WebRTC native engine unavailable on this device"}
        val api=checkNotNull(mediaApi)
        val admission=try{RtcAudioSession.select(api,serverCallId)}catch(failure:MediaApiException){
            Log.w("GsmRtc","media=webrtc call_control=sip call_id=$serverCallId event=admission_failed reason=${failure.code}")
            throw IllegalStateException("WebRTC unavailable: ${failure.code}; SIP fallback disabled")
        }
        sip.setExternalMedia(key,true)
        serverIds[key]=serverCallId
        val media=RtcAudioSession(app,admission,api,false,pcmForCall?.invoke(serverCallId),onReady={reportConnected(key)},onFailure={reason->
            Log.w("GsmRtc","media=webrtc call_control=sip call_id=$serverCallId event=failed reason=$reason")
            // Media failure belongs to this call. Reporting a global SIP
            // engine error after hangup would turn off incoming listening.
            listener?.onCallState(EngineCallSnapshot(key,direction,EngineCallState.FAILED,
                serverCallId=serverCallId,terminalReason="WebRTC audio failed: $reason",
                mediaTransport=CallMediaTransport.WEBRTC))
            runCatching{sip.hangup(key)}
        })
        check(rtc.putIfAbsent(key,media)==null){"WebRTC media already exists"}
        media.enableAudio(key in audioAllowed)
        media.start()
    }
    override fun connect()=sip.register()
    override fun dial(serverCallId:String,destination:String){
        val mode=requestedMode(serverCallId);modes[serverCallId]=mode;serverIds[serverCallId]=serverCallId
        if(context!=null)Log.i("GsmRtc","media=${mode.label.lowercase()} call_control=sip call_id=$serverCallId event=selected")
        if(mode==CallMediaTransport.WEBRTC)startRtc(serverCallId,serverCallId,EngineCallDirection.OUTBOUND)
        try{sip.makeCall(serverCallId,destination)}catch(failure:Throwable){rtc.remove(serverCallId)?.close();throw failure}
    }
    override fun answer(callId:String){
        if(modes[callId]==CallMediaTransport.WEBRTC)startRtc(callId,checkNotNull(serverIds[callId]),EngineCallDirection.INBOUND)
        try{sip.answer(callId)}catch(failure:Throwable){rtc.remove(callId)?.close();throw failure}
    }
    override fun reject(callId:String,reason:CallRejection){rtc.remove(callId)?.close();sip.reject(callId,if(reason==CallRejection.DECLINED)603 else 408)}
    override fun hangup(callId:String){rtc.remove(callId)?.close();sip.hangup(callId)}
    override fun sendDtmf(callId:String,digits:String){
        if(modes[callId]==CallMediaTransport.WEBRTC)checkNotNull(rtc[callId]).sendDtmf(digits) else sip.sendDtmf(callId,digits)
    }
    override fun setMute(callId:String,muted:Boolean){
        if(modes[callId]==CallMediaTransport.WEBRTC)checkNotNull(rtc[callId]).mute(muted) else sip.setMute(callId,muted)
    }
    override fun enableAudio(callId:String){
        audioAllowed.add(callId)
        modes.putIfAbsent(callId,requestedMode(callId))
        if(modes[callId]==CallMediaTransport.WEBRTC)rtc[callId]?.enableAudio(true) else sip.enableAudio(callId)
    }
    override fun suspendAudio(callId:String){
        audioAllowed.remove(callId);rtc[callId]?.enableAudio(false);sip.suspendAudio(callId)
    }
    override fun refreshNetwork()=sip.refreshNetwork()
    override fun close(){
        listener=null;rtc.values.forEach{it.close()};rtc.clear();confirmed.clear();modes.clear();serverIds.clear();audioAllowed.clear();connectedDelivered.clear();sip.close()
    }
}

object CallEngineFactory {
    /** Only an instrumentation process can inject hardware in memory. Ordinary
     * app builds have no synthetic PCM implementation or external control. */
    internal var pcmForInstrumentation: ((String) -> com.callagent.rtcmedia.PcmBridge?)? = null
    fun isAvailable(context:Context):Boolean = if(CallPreferences(context).webRtcFor(SessionStore(context).read()))
        RtcAudioSession.isAvailable(context) else SipEngineFactory.isAvailable(context)
    fun startSip(context:Context,config:SipConfiguration,listener:CallEngine.Listener, sipMediaOnly:Boolean=false):CallEngine {
        val app=context.applicationContext
        val store=SessionStore(app)
        val session=checkNotNull(store.read()){"Paired session missing"}
        val api=ApiClient(session.apiBaseUrl,store,5000,5000)
        return SipCallEngine(SipEngineFactory.create(app),app,MediaApi{method,path,body->api.rtcRequest(method,path,body)},pcmForInstrumentation){id->
            val active=CallRuntime.currentSession?.takeIf{it.callId==id}
            if(sipMediaOnly) CallMediaTransport.SIP else active?.mediaTransport?:if(CallPreferences(app).webRtcFor(store.read()))CallMediaTransport.WEBRTC else CallMediaTransport.SIP
        }.also{it.start(config,listener)}
    }
}
