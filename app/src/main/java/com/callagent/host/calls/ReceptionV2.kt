package com.callagent.host.calls

import android.content.Context
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.HostSession
import com.callagent.host.data.normalizeApiBaseUrl
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** A node's reception settings for this phone, as the node answered them. */
data class ReceptionConfig(val version: Int, val incoming: Boolean, val sms: Boolean, val revision: Long) {
    internal fun encode() = "$version,$incoming,$sms,$revision"

    companion object {
        fun parse(json: JSONObject) = ReceptionConfig(json.getInt("version"), json.getBoolean("incoming_enabled"),
            json.getBoolean("sms_enabled"), json.getLong("revision"))

        internal fun decode(value: String?): ReceptionConfig? {
            val parts = value?.split(',')?.takeIf { it.size == 4 } ?: return null
            return runCatching {
                ReceptionConfig(parts[0].toInt(), parts[1].toBooleanStrict(), parts[2].toBooleanStrict(), parts[3].toLong())
            }.getOrNull()
        }
    }
}

/**
 * Reception v2: a node that advertises reception_v2 keeps this phone's choice
 * to receive calls until the phone changes it. Asleep or offline, the phone
 * stays in every incoming call there and is woken by push; it no longer
 * renews a call-control capability, asks for sleep grants, holds the
 * compatibility lease or beats an alarm to stay eligible.
 *
 * The choice is [CallPreferences]; this writes it to each node and remembers
 * each node's answer across restarts, so an idle phone knows without asking
 * which nodes need no renewals. A node seen not to advertise reception_v2 (a
 * rollback) is renewed the old way again.
 */
internal object ReceptionV2 {
    private const val PREFS = "host-reception-v2"
    private const val ATTEMPTS = 4

    private val advertised = ConcurrentHashMap.newKeySet<String>()
    private val withdrawn = ConcurrentHashMap.newKeySet<String>()

    /** GET /capabilities of [apiRoot] said whether it offers reception v2. */
    fun observed(apiRoot: String, offered: Boolean) {
        if (offered) { advertised.add(apiRoot); withdrawn.remove(apiRoot) }
        else { advertised.remove(apiRoot); withdrawn.add(apiRoot) }
    }

    fun advertises(apiRoot: String): Boolean = apiRoot in advertised

    /** This process has seen [apiRoot]'s capabilities, either way. */
    fun known(apiRoot: String): Boolean = apiRoot in advertised || apiRoot in withdrawn

    /** The node of [session] confirmed version 2 and has not been seen to withdraw it. */
    fun active(context: Context, session: HostSession): Boolean =
        normalizeApiBaseUrl(session.apiBaseUrl) !in withdrawn && confirmed(context, session.sessionInstanceId)?.version == 2

    fun confirmed(context: Context, sessionId: String): ReceptionConfig? =
        ReceptionConfig.decode(prefs(context).getString(sessionId, null))

    /** What the phone wants a node to do. */
    data class Choice(val incoming: Boolean, val sms: Boolean)

    /** The node must be told: it is not on version 2 yet, or has another choice. */
    fun needsSync(context: Context, session: HostSession, choice: Choice): Boolean =
        confirmed(context, session.sessionInstanceId)?.let { it.version != 2 || it.incoming != choice.incoming || it.sms != choice.sms } ?: true

    /**
     * The phone's choice for [session]'s node: incoming calls as set in
     * [CallPreferences]; SMS wake-ups while background sync, which syncs and
     * notifies messages, is on for this account.
     */
    fun choice(context: Context, session: HostSession): Choice =
        Choice(CallPreferences(context).enabledFor(session), com.callagent.host.background.HostBackgroundRuntime.smsWanted(context, session))

    /**
     * Writes the phone's choice to the node of [session] and records the answer.
     * After a conflict it reads the node again and writes [desired] again; after
     * each write [desired] is read again, so a choice changed meanwhile is
     * written too and an older answer never stands for a newer choice.
     */
    fun sync(context: Context, session: HostSession, api: ApiClient, desired: () -> Choice): Boolean {
        repeat(ATTEMPTS) {
            val choice = desired()
            if (!needsSync(context, session, choice)) return true
            val current = api.reception()
            val answer = if (current.version == 2 && current.incoming == choice.incoming && current.sms == choice.sms) current else try {
                api.putReception(current.revision, choice.incoming, choice.sms)
            } catch (failure: ApiFailure) {
                if (failure.httpStatus == 409) return@repeat
                throw failure
            }
            prefs(context).edit().putString(session.sessionInstanceId, answer.encode()).apply()
        }
        return !needsSync(context, session, desired())
    }

    /** How far the phone's choice has reached its nodes. */
    data class Status(val synced: Int, val pending: Int, val legacy: Int)

    fun status(context: Context): Status {
        var synced = 0; var pending = 0; var legacy = 0
        runCatching { com.callagent.host.push.PushRegistration.pairings(context) }.getOrDefault(emptyList()).forEach { (session, _) ->
            val root = normalizeApiBaseUrl(session.apiBaseUrl)
            when {
                !active(context, session) && (root in withdrawn || confirmed(context, session.sessionInstanceId) == null && root !in advertised) -> legacy++
                needsSync(context, session, choice(context, session)) -> pending++
                else -> synced++
            }
        }
        return Status(synced, pending, legacy)
    }

    /**
     * Per node, for the standby report: the node's answer, whether the
     * phone's choice still has to reach it, and how calls get to this phone
     * from there: "push" (woken by push, nothing kept running), "stream"
     * (v2 without push: the WebSocket with its heartbeat) or "legacy"
     * (capability renewals).
     */
    fun diagnostics(context: Context): org.json.JSONArray {
        val nodes = org.json.JSONArray()
        runCatching { com.callagent.host.push.PushRegistration.pairings(context) }.getOrDefault(emptyList()).forEachIndexed { index, (session, _) ->
            val confirmed = confirmed(context, session.sessionInstanceId)
            val v2 = active(context, session)
            val push = runCatching { com.callagent.host.push.PushRegistration.isReady(context, session.sessionInstanceId) }.getOrDefault(false)
            nodes.put(org.json.JSONObject().put("node", index + 1)
                .put("version", confirmed?.version ?: 1)
                .put("incoming", confirmed?.incoming).put("sms", confirmed?.sms).put("revision", confirmed?.revision)
                .put("pending", v2 && needsSync(context, session, choice(context, session)))
                .put("mode", if (!v2) "legacy" else if (push) "push" else "stream")
                .put("heartbeat_s", if (v2 && !push) com.callagent.host.background.WakeHeartbeat.seconds(context, normalizeApiBaseUrl(session.apiBaseUrl)) else null))
        }
        return nodes
    }

    /** One line for the call panel, or null when no node offers reception v2. */
    fun describe(status: Status): String? {
        if (status.synced + status.pending == 0) return null
        val parts = buildList {
            add(if (status.pending == 0) "接收设置已同步到 ${status.synced} 个节点" else "${status.pending} 个节点的接收设置待同步（联网后自动提交，本机设置已生效）")
            if (status.legacy > 0) add("${status.legacy} 个旧版节点仍按原方式保持接收")
        }
        return parts.joinToString("；") + "。"
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun resetForTest(context: Context) {
        advertised.clear(); withdrawn.clear()
        prefs(context).edit().clear().commit()
    }
}
