package com.callagent.host.calls

import android.content.Context
import android.os.SystemClock
import com.callagent.host.mesh.HostNodes
import com.callagent.host.push.PushRegistration
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Push sleep: a node that advertises rtc_sleep keeps offering calls to a
 * client whose push endpoint works, for as long as its sleep grant runs,
 * without the 90 s capability refresh. A call then arrives as a push.
 *
 * The phone asks for a grant on every capability renewal once push is
 * registered with a node. It relies on grants (beats of
 * [RtcSleepyListening.TRUSTED_INTERVAL_MS] instead of a minute) only while
 * every reachable node granted one and recently delivered a push: the node
 * sends a hint at least hourly to a sleeping client for exactly this.
 * Everything is per process; after a restart the phone beats every minute
 * until the next hint arrives.
 */
internal object RtcSleep {
    const val REQUEST_SECONDS = 1800
    /** A grant this close to its end no longer counts. */
    private const val GRANT_MARGIN_MS = 5 * 60_000L
    /** Hints come at least hourly while sleeping; allow one to be missed. */
    private const val EVIDENCE_MS = 2 * 60 * 60_000L

    private val advertised = ConcurrentHashMap.newKeySet<String>()
    private val grants = ConcurrentHashMap<String, Long>()
    private val pushes = ConcurrentHashMap<String, Long>()
    @Volatile private var app: Context? = null

    fun install(context: Context) { app = context.applicationContext }

    /** GET /capabilities of [apiRoot] said whether it grants sleep. */
    fun observed(apiRoot: String, sleep: Boolean) {
        if (sleep) advertised.add(apiRoot) else advertised.remove(apiRoot)
    }

    fun advertises(apiRoot: String): Boolean = apiRoot in advertised

    /** Whether a capability renewal for [sessionId] at [apiRoot] should ask to sleep. */
    fun wanted(sessionId: String, apiRoot: String): Boolean {
        wantedForTest?.let { return it(sessionId, apiRoot) }
        val context = app ?: return false
        return advertises(apiRoot) && StandbyPowerPolicy.sleepyListening(context) &&
            runCatching { PushRegistration.isReady(context, sessionId) }.getOrDefault(false)
    }

    /** The node's answer to a renewal: a grant, or none (which ends an earlier one). */
    fun answered(sessionId: String, response: JSONObject, enabled: Boolean) {
        val until = response.optString("sleep_until").takeIf { enabled && it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        if (until == null) { grants.remove(sessionId); return }
        grants[sessionId] = SystemClock.elapsedRealtime() + (until - System.currentTimeMillis())
    }

    /** A push from the node of [sessionId] reached this phone. */
    fun pushArrived(sessionId: String) { pushes[sessionId] = SystemClock.elapsedRealtime() }

    fun trusted(sessionId: String, now: Long = SystemClock.elapsedRealtime()): Boolean {
        val grant = grants[sessionId] ?: return false
        val push = pushes[sessionId] ?: return false
        return grant - now > GRANT_MARGIN_MS && now - push < EVIDENCE_MS
    }

    /**
     * Every node that could offer a call can wake the phone by push. A node the
     * last probe could not reach cannot offer one either, so it does not count.
     */
    fun phoneMaySleep(context: Context): Boolean {
        val pairings = runCatching { PushRegistration.pairings(context) }.getOrDefault(emptyList())
        if (pairings.isEmpty()) return false
        val health = HostNodes.health(context)
        val unreachable = HostNodes.nodes(context)
            .filter { node -> health[node.key]?.let { it.checkedAt > 0L && !it.healthy } == true }
            .map { it.sessionInstanceId }.toSet()
        return pairings.all { (session, _) -> session.sessionInstanceId in unreachable || trusted(session.sessionInstanceId) }
    }

    /** Push registration needs a relay build; tests decide directly. */
    @Volatile internal var wantedForTest: ((String, String) -> Boolean)? = null

    internal fun resetForTest() { advertised.clear(); grants.clear(); pushes.clear(); wantedForTest = null }
}
