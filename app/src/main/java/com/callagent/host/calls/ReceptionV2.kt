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

    /** The node must be told: it is not on version 2 yet, or has another choice than [incoming]. */
    fun needsSync(context: Context, session: HostSession, incoming: Boolean): Boolean =
        confirmed(context, session.sessionInstanceId)?.let { it.version != 2 || it.incoming != incoming } ?: true

    /**
     * Writes the phone's choice to the node of [session] and records the answer.
     * After a conflict it reads the node again and writes [desired] again; after
     * each write [desired] is read again, so a choice changed meanwhile is
     * written too and an older answer never stands for a newer choice.
     */
    fun sync(context: Context, session: HostSession, api: ApiClient, desired: () -> Boolean): Boolean {
        repeat(ATTEMPTS) {
            val incoming = desired()
            if (!needsSync(context, session, incoming)) return true
            val current = api.reception()
            val answer = if (current.version == 2 && current.incoming == incoming && current.sms) current else try {
                api.putReception(current.revision, incoming, sms = true)
            } catch (failure: ApiFailure) {
                if (failure.httpStatus == 409) return@repeat
                throw failure
            }
            prefs(context).edit().putString(session.sessionInstanceId, answer.encode()).apply()
        }
        return !needsSync(context, session, desired())
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun resetForTest(context: Context) {
        advertised.clear(); withdrawn.clear()
        prefs(context).edit().clear().commit()
    }
}
