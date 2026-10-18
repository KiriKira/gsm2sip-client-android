package com.callagent.lab

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import com.callagent.host.MainActivity
import com.callagent.host.R
import com.callagent.host.data.ApiClient
import com.callagent.host.data.SessionStore
import com.callagent.host.mesh.HostNodes
import com.callagent.host.calls.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Real host service/UI + native SIP control and RTC media; replaces only the
 * microphone/speaker hardware through the production engine's PCM dependency. */
class HostJointInstrumentation:Instrumentation(){
    @Volatile private var latestHost:MainActivity?=null
    @Volatile private var resumedActivity:Activity?=null
    override fun callActivityOnResume(activity:Activity){
        super.callActivityOnResume(activity)
        resumedActivity=activity
        if(activity is MainActivity)latestHost=activity
    }
    override fun callActivityOnPause(activity:Activity){
        if(resumedActivity===activity)resumedActivity=null
        super.callActivityOnPause(activity)
    }
    private fun showMain():MainActivity{
        onMain{targetContext.startActivity(Intent(targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120)
        while(System.nanoTime()<deadline){
            latestHost?.takeIf{!it.isDestroyed && it.hasWindowFocus()}?.let{return it}
            Thread.sleep(100)
        }
        error("current_main_activity_focus_timeout")
    }
    private var stage="fixture"
        set(value){field=value;runCatching{targetContext.filesDir.resolve("joint-stage.txt").writeText(value)}}
    private fun onMain(action:()->Unit){
        var failure:Throwable?=null
        runOnMainSync{try{action()}catch(error:Throwable){failure=error}}
        failure?.let{throw it}
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        val tones=CopyOnWriteArrayList<LabPcmTone>()
        var activity:Activity?=null
        var activeTone:LabPcmTone?=null
        val result=JSONObject().put("scope","real_host_ui_service_sip_rtc_with_synthetic_pcm")
        try{
            check(android.os.Build.SUPPORTED_ABIS.contains("x86_64")){"emulator_only"}
            val config=JSONObject(targetContext.filesDir.resolve("joint-fixture.json").readText())
            onMain{targetContext.stopService(Intent(targetContext,HostCallService::class.java))}
            Thread.sleep(500)
            stage="pair"
            val session=if(config.optBoolean("reuse_pairing")){
                checkNotNull(SessionStore(targetContext).read()){"resume_host_pairing_missing"}
            }else{
                HostNodes.forgetAll(targetContext);SessionStore(targetContext).clear()
                ApiClient(config.getString("api_url"),SessionStore(targetContext)).pair(config.getString("pairing_code"),"RTC joint emulator host")
            }
            check(com.callagent.host.data.normalizeApiBaseUrl(session.apiBaseUrl)==com.callagent.host.data.normalizeApiBaseUrl(config.getString("api_url"))){"resume_host_origin_changed"}
            val api=ApiClient(session.apiBaseUrl,SessionStore(targetContext))
            check(api.listGateways().single().gatewayId==config.getString("gateway_id")){"fixture_gateway_mismatch"}
            CallEngineFactory.pcmForInstrumentation={
                activeTone?.close()
                LabPcmTone(500.0,900.0).also{tone->activeTone=tone;tones.add(tone);tone.start()}.pcm
            }
            stage="real_activity"
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            val initialHost=checkNotNull(activity)
            val focusDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
            while(!initialHost.hasWindowFocus()){check(System.nanoTime()<focusDeadline){"activity_focus_timeout"};Thread.sleep(100)}
            onMain{check(CallRuntime.enableForegroundListening(initialHost)){"visible_listening_start_refused"}}
            stage="sip_registration"
            val registrationDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90)
            while(!CallRuntime.currentStatus.startsWith("SIP 已注册")){
                check(System.nanoTime()<registrationDeadline){"actual_sip_registration_timeout"}
                Thread.sleep(200)
            }
            targetContext.filesDir.resolve("joint-ready.json").writeText(JSONObject().put("status","READY").toString())
            var last="";val deadline=System.nanoTime()+TimeUnit.HOURS.toNanos(6)
            stage="commands"
            while(System.nanoTime()<deadline){
                Thread.sleep(200)
                if(CallRuntime.currentSession?.phase in setOf(CallPhase.ENDED,CallPhase.FAILED)){activeTone?.close();activeTone=null}
                val command=runCatching{JSONObject(targetContext.filesDir.resolve("joint-command.json").readText())}.getOrNull()?:continue
                val id=command.getString("id");if(id==last)continue;last=id
                val reply=JSONObject().put("id",id)
                try{
                    val host=if(command.getString("op") in setOf("rtc","dial"))showMain()
                        else resumedActivity?:latestHost?:initialHost
                    when(command.getString("op")){
                        "rtc"->{
                            onMain{if(host.findViewById<MaterialSwitch>(R.id.webrtc_audio_switch)==null)host.findViewById<BottomNavigationView>(R.id.main_bottom_navigation).selectedItemId=R.id.tab_settings}
                            onMain{
                                val toggle=checkNotNull(host.findViewById<MaterialSwitch>(R.id.webrtc_audio_switch)){"actual_ui_switch_missing"}
                                check(toggle.isEnabled){"actual_ui_switch_disabled"}
                                if(toggle.isChecked!=command.getBoolean("enabled"))toggle.performClick()
                                check(toggle.isChecked==command.getBoolean("enabled")){"actual_ui_switch_did_not_toggle"}
                            }
                            check(CallPreferences(targetContext).webRtcFor(SessionStore(targetContext).read())==command.getBoolean("enabled")){"ui_preference_not_saved"}
                        }
                        "dial"->{
                            onMain{if(host.findViewById<android.view.View>(R.id.dialer_call_button)==null)host.findViewById<BottomNavigationView>(R.id.main_bottom_navigation).selectedItemId=R.id.tab_phone}
                            onMain{if(host.findViewById<android.view.View>(R.id.dialer_call_button)==null)check(host.findViewById<android.view.View>(R.id.dialer_open).performClick())}
                            onMain{
                                val selector=checkNotNull(host.findViewById<com.google.android.material.chip.ChipGroup>(R.id.dialer_sim_selector))
                                check(selector.childCount==1){"expected_one_emulated_sim:"+selector.childCount}
                                check(selector.getChildAt(0).performClick())
                            }
                            onMain{
                                host.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialer_destination).setText("+15550100200")
                                val button=checkNotNull(host.findViewById<android.view.View>(R.id.dialer_call_button)){"actual_dial_button_missing"}
                                check(button.isEnabled){"dial_disabled:"+host.findViewById<android.widget.TextView>(R.id.dialer_readiness)?.text}
                                check(button.performClick())
                            }
                        }
                        "answer"->onMain{check(CallRuntime.answerFromVisibleUser(host,checkNotNull(CallRuntime.currentCallId))){"answer_refused"}}
                        "reject"->onMain{CallRuntime.rejectFromUi(host,checkNotNull(CallRuntime.currentCallId))}
                        "hangup"->onMain{CallRuntime.hangupFromUi(host,checkNotNull(CallRuntime.currentCallId))}
                        "mute"->onMain{CallRuntime.setMuted(host,checkNotNull(CallRuntime.currentCallId),command.getBoolean("enabled"))}
                        "dtmf"->onMain{CallRuntime.sendDtmf(host,checkNotNull(CallRuntime.currentCallId),"5")}
                        "clear"->onMain{CallRuntime.currentSession?.let{CallRuntime.clearTerminal(it.callId)}}
                        "status"->Unit
                        "stop"->break
                        else->error("unknown_op")
                    }
                    val call=CallRuntime.currentSession
                    reply.put("status","OK").put("listening",CallRuntime.isForegroundListening).put("phase",call?.phase?.name)
                        .put("call_id",call?.callId).put("media",call?.mediaTransport?.label).put("call_failure",call?.failure)
                        .put("runtime_status",CallRuntime.currentStatus).put("pcm",JSONArray(tones.map{it.report()}))
                }catch(failure:Throwable){reply.put("status","FAIL").put("error",failure.javaClass.simpleName+":"+failure.message)}
                targetContext.filesDir.resolve("joint-result.json").writeText(reply.toString())
            }
            result.put("status","PASS")
        }catch(failure:Throwable){result.put("status","FAIL").put("error",failure.javaClass.simpleName+":"+failure.message)}
        finally{
            CallEngineFactory.pcmForInstrumentation=null
            runCatching{onMain{CallRuntime.disableForegroundListening(targetContext);activity?.finish()}}
            tones.forEach{it.close()};result.put("stage",stage)
            targetContext.filesDir.resolve("joint-finished.json").writeText(result.toString())
            finish(Activity.RESULT_OK,Bundle().apply{putString("joint_result",result.toString())})
        }
    }
}
