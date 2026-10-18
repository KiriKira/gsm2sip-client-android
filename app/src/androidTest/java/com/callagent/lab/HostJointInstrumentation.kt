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
    private var stage="fixture"
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        val tones=CopyOnWriteArrayList<LabPcmTone>()
        var activity:Activity?=null
        var activeTone:LabPcmTone?=null
        val result=JSONObject().put("scope","real_host_ui_service_sip_rtc_with_synthetic_pcm")
        try{
            check(android.os.Build.SUPPORTED_ABIS.contains("x86_64")){"emulator_only"}
            val config=JSONObject(targetContext.filesDir.resolve("joint-fixture.json").readText())
            runOnMainSync{targetContext.stopService(Intent(targetContext,HostCallService::class.java))}
            Thread.sleep(500)
            HostNodes.forgetAll(targetContext);SessionStore(targetContext).clear()
            stage="pair"
            val session=ApiClient(config.getString("api_url"),SessionStore(targetContext)).pair(config.getString("pairing_code"),"RTC joint emulator host")
            val api=ApiClient(session.apiBaseUrl,SessionStore(targetContext))
            CallEngineFactory.pcmForInstrumentation={
                activeTone?.close()
                LabPcmTone(500.0,900.0).also{tone->activeTone=tone;tones.add(tone);tone.start()}.pcm
            }
            stage="real_activity"
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val host=checkNotNull(activity)
            val focusDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
            while(!host.hasWindowFocus()){check(System.nanoTime()<focusDeadline){"activity_focus_timeout"};Thread.sleep(100)}
            runOnMainSync{check(CallRuntime.enableForegroundListening(host)){"visible_listening_start_refused"}}
            targetContext.filesDir.resolve("joint-ready.json").writeText(JSONObject().put("status","READY").toString())
            var last="";val deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(25)
            stage="commands"
            while(System.nanoTime()<deadline){
                Thread.sleep(200)
                if(CallRuntime.currentSession?.phase in setOf(CallPhase.ENDED,CallPhase.FAILED)){activeTone?.close();activeTone=null}
                val command=runCatching{JSONObject(targetContext.filesDir.resolve("joint-command.json").readText())}.getOrNull()?:continue
                val id=command.getString("id");if(id==last)continue;last=id
                val reply=JSONObject().put("id",id)
                try{
                    when(command.getString("op")){
                        "rtc"->{
                            runOnMainSync{host.findViewById<BottomNavigationView>(R.id.main_bottom_navigation).selectedItemId=R.id.tab_settings}
                            runOnMainSync{
                                val toggle=checkNotNull(host.findViewById<MaterialSwitch>(R.id.webrtc_audio_switch)){"actual_ui_switch_missing"}
                                check(toggle.isEnabled){"actual_ui_switch_disabled"}
                                if(toggle.isChecked!=command.getBoolean("enabled"))check(toggle.performClick())
                            }
                            check(CallPreferences(targetContext).webRtcFor(SessionStore(targetContext).read())==command.getBoolean("enabled")){"ui_preference_not_saved"}
                        }
                        "dial"->{
                            runOnMainSync{host.findViewById<BottomNavigationView>(R.id.main_bottom_navigation).selectedItemId=R.id.tab_phone}
                            runOnMainSync{check(host.findViewById<android.view.View>(R.id.dialer_open).performClick())}
                            runOnMainSync{
                                host.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.dialer_destination).setText("+15550100200")
                                val button=checkNotNull(host.findViewById<android.view.View>(R.id.dialer_call_button)){"actual_dial_button_missing"}
                                check(button.isEnabled){"dial_disabled:"+host.findViewById<android.widget.TextView>(R.id.dialer_readiness)?.text}
                                check(button.performClick())
                            }
                        }
                        "answer"->runOnMainSync{check(CallRuntime.answerFromVisibleUser(host,checkNotNull(CallRuntime.currentCallId))){"answer_refused"}}
                        "reject"->runOnMainSync{CallRuntime.rejectFromUi(host,checkNotNull(CallRuntime.currentCallId))}
                        "hangup"->runOnMainSync{CallRuntime.hangupFromUi(host,checkNotNull(CallRuntime.currentCallId))}
                        "mute"->runOnMainSync{CallRuntime.setMuted(host,checkNotNull(CallRuntime.currentCallId),command.getBoolean("enabled"))}
                        "dtmf"->runOnMainSync{CallRuntime.sendDtmf(host,checkNotNull(CallRuntime.currentCallId),"5")}
                        "clear"->runOnMainSync{CallRuntime.currentSession?.let{CallRuntime.clearTerminal(it.callId)}}
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
            runCatching{runOnMainSync{CallRuntime.disableForegroundListening(targetContext);activity?.finish()}}
            tones.forEach{it.close()};result.put("stage",stage)
            targetContext.filesDir.resolve("joint-finished.json").writeText(result.toString())
            finish(Activity.RESULT_OK,Bundle().apply{putString("joint_result",result.toString())})
        }
    }
}
