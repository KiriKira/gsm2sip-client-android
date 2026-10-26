package com.callagent.host.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Bounded, in-memory counters. Never retain URLs, identifiers, payloads or credentials. */
object StandbyDiagnostics {
    data class Counts(val requests: Long, val failures: Long, val elapsedNanos: Long, val sentBytes: Long = 0, val receivedBytes: Long = 0)
    private class Counter {
        val requests = AtomicLong()
        val failures = AtomicLong()
        val nanos = AtomicLong()
        val sent = AtomicLong()
        val received = AtomicLong()
    }
    private val counters = ConcurrentHashMap<String, Counter>()
    private val cause = ThreadLocal<String>()
    private val nodeLabels = mutableMapOf<String, String>()
    @Synchronized private fun nodeLabel(source: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return nodeLabels[digest] ?: if (nodeLabels.size < 32) "node-${nodeLabels.size + 1}".also { nodeLabels[digest] = it } else "other-node"
    }
    private val startedAt = System.currentTimeMillis()
    private val events = ConcurrentHashMap<String, AtomicLong>()
    private data class Held(val tag: String, val start: Long, var deadline: Long)
    private data class Wake(var count: Long = 0, var millis: Long = 0, var longest: Long = 0)
    private val held = mutableMapOf<Long, Held>()
    private val wakes = mutableMapOf<String, Wake>()
    private var nextToken = 0L
    private var socketStart: Long? = null
    private var socketMillis = 0L
    @Volatile private var lastCheckpoint = -300_000L
    @Volatile private var lastPushAt = 0L
    @Volatile private var actualPriority = 0
    @Volatile private var originalPriority = 0

    @Synchronized fun event(kind: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (kind == "wss_open") socketStart = now
        if (kind == "wss_closed") {
            socketStart?.let { socketMillis += now - it }
            socketStart = null
        }
        if (kind in setOf("wss_open", "wss_closed", "network_changed")) events.computeIfAbsent(kind) { AtomicLong() }.incrementAndGet()
    }

    fun pushReceived(actual: Int, original: Int) {
        lastPushAt = System.currentTimeMillis()
        actualPriority = actual
        originalPriority = original
    }

    @Synchronized fun wakeAcquired(tag: String, timeout: Long): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        val token = ++nextToken
        held[token] = Held(tag, now, now + timeout)
        wakes.getOrPut(tag) { Wake() }.count++
        return token
    }

    @Synchronized fun wakeRenewed(token: Long, timeout: Long) {
        held[token]?.let { interval ->
            interval.deadline = android.os.SystemClock.elapsedRealtime() + timeout
            wakes.getValue(interval.tag).count++
        }
    }

    @Synchronized fun wakeReleased(token: Long) {
        val interval = held.remove(token) ?: return
        val elapsed = (minOf(android.os.SystemClock.elapsedRealtime(), interval.deadline) - interval.start).coerceAtLeast(0)
        val total = wakes.getOrPut(interval.tag) { Wake() }
        total.millis += elapsed
        total.longest = maxOf(total.longest, elapsed)
    }

    /** An interrupted process is a separate segment; no invented duration after its last checkpoint. */
    @Synchronized fun json(): org.json.JSONObject {
        val now = android.os.SystemClock.elapsedRealtime()
        val locks = org.json.JSONObject()
        for ((tag, total) in wakes) {
            val intervals = held.values.filter { it.tag == tag }
            val durations = intervals.map { (minOf(now, it.deadline) - it.start).coerceAtLeast(0) }
            locks.put(tag, org.json.JSONObject().put("acquisitions", total.count)
                .put("held_ms", total.millis + durations.sum())
                .put("longest_ms", maxOf(total.longest, durations.maxOrNull() ?: 0))
                .put("currently_held", intervals.any { now < it.deadline }))
        }
        val requests = org.json.JSONObject()
        snapshot().forEach { (key, count) -> requests.put(key, org.json.JSONObject()
            .put("requests", count.requests).put("failures", count.failures).put("elapsed_ns", count.elapsedNanos).put("sent_bytes", count.sentBytes).put("received_bytes", count.receivedBytes)) }
        return org.json.JSONObject().put("version", com.callagent.host.BuildConfig.VERSION_NAME)
            .put("segment_started_at", startedAt).put("sampled_at", System.currentTimeMillis())
            .put("http", requests).put("wake_locks", locks)
            .put("events", org.json.JSONObject(events.mapValues { it.value.get() }))
            .put("wss_connected_ms", socketMillis + (socketStart?.let { now - it } ?: 0L))
            .put("last_fcm_received_at", lastPushAt).put("fcm_priority", actualPriority).put("fcm_original_priority", originalPriority)
            .put("scope", "process segment; wake intervals capped at lease expiry; application HTTP only")
    }

    /** Cache at most eight process segments, once per five minutes, never per poll. */
    @Synchronized fun checkpoint(context: android.content.Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastCheckpoint < 300_000L) return
        lastCheckpoint = now
        runCatching {
            val dir = java.io.File(context.cacheDir, "standby-diagnostics").apply { mkdirs() }
            val target = java.io.File(dir, "$startedAt.json")
            val temporary = java.io.File(dir, "$startedAt.tmp")
            temporary.writeText(json().toString())
            temporary.renameTo(target)
            val oldSegments = dir.listFiles().orEmpty().filter { it.extension == "json" }.sortedByDescending { it.name }
            oldSegments.drop(8).forEach { it.delete() }
        }
    }

    fun summary(): String {
        val data = json()
        val locks = data.getJSONObject("wake_locks")
        val lines = mutableListOf("本次进程运行以来的统计（耗时不是电量）：")
        locks.keys().forEach { tag ->
            val lock = locks.getJSONObject(tag)
            lines += "$tag：累计 ${lock.getLong("held_ms") / 1000} 秒" + if (lock.getBoolean("currently_held")) "，正在持锁" else ""
        }
        lines += "HTTP 请求：${snapshot().values.sumOf { it.requests }} 次"
        lines += if (lastPushAt == 0L) "本次运行尚未收到 FCM；这不等于注册失败。"
            else "最近收到 FCM：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastPushAt))}"
        lines += "分享统计可导出详细 JSON；进程异常结束前未采样的时间不计入历史。"
        return lines.joinToString("\n\n")
    }

    fun export(context: android.content.Context): String {
        val segments = org.json.JSONArray()
        java.io.File(context.cacheDir, "standby-diagnostics").listFiles()
            ?.filter { it.extension == "json" && it.name != "$startedAt.json" }
            ?.sortedBy { it.name }?.takeLast(7)?.forEach { file ->
                runCatching { segments.put(org.json.JSONObject(file.readText())) }
            }
        segments.put(json())
        return org.json.JSONObject().put("segments", segments)
            .put("history_note", "Cache may be cleared. Earlier segments end at last checkpoint; abrupt endings are incomplete.")
            .put("node_count", com.callagent.host.mesh.HostNodes.nodes(context).size.coerceAtLeast(1))
            .put("listening_requested", com.callagent.host.calls.CallRuntime.isListeningRequested)
            .put("wss_live", com.callagent.host.calls.CallRuntime.wakeChannelLive)
            .put("stable_network", com.callagent.host.network.NetworkEnvironment.stable).toString(2)
    }

    fun <T> withCause(reason: String, block: () -> T): T {
        val previous = cause.get()
        cause.set(if (reason in CAUSES) reason else "other")
        try { return block() } finally {
            if (previous == null) cause.remove() else cause.set(previous)
        }
    }

    internal fun record(method: String, path: String, success: Boolean, nanos: Long, sent: Long = 0, received: Long = 0, source: String = "") {
        val category = when {
            path == "/capabilities" -> "capabilities"
            path.startsWith("/rtc/control/") -> "rtc_control"
            path.startsWith("/events") -> "events"
            path.startsWith("/gateways") -> "metadata"
            else -> "other"
        }
        val verb = if (method == "GET") "read" else "write"
        val counter = counters.computeIfAbsent("${cause.get() ?: "other"}:$verb:$category:${nodeLabel(source)}") { Counter() }
        counter.requests.incrementAndGet()
        if (!success) counter.failures.incrementAndGet()
        counter.nanos.addAndGet(nanos.coerceAtLeast(0))
        counter.sent.addAndGet(sent)
        counter.received.addAndGet(received)
    }

    fun snapshot(): Map<String, Counts> = counters.mapValues { (_, c) ->
        Counts(c.requests.get(), c.failures.get(), c.nanos.get(), c.sent.get(), c.received.get())
    }

    private val CAUSES = setOf("periodic", "push", "websocket", "network", "node_probe", "foreground", "rtc")
}
