package com.callagent.lab

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.SessionStore
import com.callagent.host.data.normalizeApiBaseUrl
import com.callagent.host.mesh.HostNodes
import com.callagent.host.push.NodePushStatus
import com.callagent.host.push.PushRegistration
import org.json.JSONObject

/**
 * One-shot mesh setup through production APIs, independent of call readiness.
 *
 * Input: target files/mesh-setup-fixture.json with server_url and pairing_code.
 * Output: target files/mesh-setup-result.json and a redacted instrumentation result.
 * The input is deleted on exit. No Activity, call service, PCM factory, incoming
 * preference, or endpoint-withdrawal operation is started by this runner.
 */
class MeshSetupInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val app = targetContext.applicationContext
        val fixture = app.filesDir.resolve("mesh-setup-fixture.json")
        val output = app.filesDir.resolve("mesh-setup-result.json")
        val result = JSONObject()
            .put("scope", "mesh_setup_only")
            .put("status", "FAIL")
            .put("node_added", false)
        var phase = "fixture"
        var succeeded = false
        try {
            check(fixture.isFile && fixture.length() in 1L..16_384L) { "INVALID_FIXTURE" }
            val input = JSONObject(fixture.readText())
            val serverUrl = input.getString("server_url").trim()
            val normalizedUrl = normalizeApiBaseUrl(serverUrl)
            val code = input.getString("pairing_code").trim()
            check(code.length in 20..128 && code.none(Char::isWhitespace)) { "INVALID_FIXTURE" }

            phase = "preflight"
            val active = checkNotNull(SessionStore(app).read()) { "PAIRING_REQUIRED" }
            check(active.role == "client") { "CLIENT_REQUIRED" }
            // runSync withdraws endpoints on builds without push; never enter
            // that path while setting up an FCM-capable phone.
            check(PushRegistration.available(app)) { "PUSH_UNAVAILABLE" }
            val existing = HostNodes.nodes(app)
            result.put("node_count_before", existing.size.coerceAtLeast(1))
            val alreadyPresent = (existing.map { it.apiBaseUrl } + active.apiBaseUrl)
                .any { normalizeApiBaseUrl(it) == normalizedUrl }
            if (alreadyPresent) {
                result.put("outcome", "ALREADY_PRESENT")
            } else {
                phase = "add_node"
                val node = HostNodes.add(app, serverUrl, code, "R1 mesh setup host")
                result.put("node_added", true)
                phase = "catch_up"
                HostNodes.catchUpSilently(app, node)
                phase = "push_registration"
                PushRegistration.sync(app, force = true)
                // Registration is asynchronous. Bound this wait without
                // keeping call signaling alive or manufacturing a push.
                val deadline = SystemClock.elapsedRealtime() + 65_000L
                var push = PushRegistration.status(app, node.sessionInstanceId)
                while (push.kind in setOf(NodePushStatus.Kind.PENDING, NodePushStatus.Kind.NO_ENDPOINT) &&
                    SystemClock.elapsedRealtime() < deadline
                ) {
                    Thread.sleep(200)
                    push = PushRegistration.status(app, node.sessionInstanceId)
                }
                result.put("push_state", push.kind.name)
                    .put("push_ready", PushRegistration.isReady(app, node.sessionInstanceId))
                    .put("outcome", "ADDED")
            }
            result.put("node_count_after", HostNodes.nodes(app).size.coerceAtLeast(1))
                .put("status", "PASS")
            succeeded = true
        } catch (failure: Throwable) {
            // Exception messages can contain the server URL or one-time code.
            // Report only fixed categories, never a message or stack trace.
            result.put("error_type", failure.javaClass.simpleName)
            if (failure is ApiFailure) {
                result.put("http_status", failure.httpStatus)
                val safeCode = failure.code.takeIf { it.matches(Regex("[A-Z0-9_]{1,64}")) }
                if (safeCode != null) result.put("error_code", safeCode)
            }
        } finally {
            result.put("phase", phase)
            fixture.delete()
            result.put("fixture_removed", !fixture.exists())
            // A successfully registered node stays paired, even if a later
            // catch-up/registration step failed. Use normal node removal for
            // rollback; never revoke or change the active node on exit.
            runCatching { output.writeText(result.toString()) }
            finish(
                if (succeeded) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                Bundle().apply { putString("mesh_setup_result", result.toString()) }
            )
        }
    }
}
