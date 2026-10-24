package com.callagent.host.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Bounded, in-memory counters. Never retain URLs, identifiers, payloads or credentials. */
object StandbyDiagnostics {
    data class Counts(val requests: Long, val failures: Long, val elapsedNanos: Long)
    private class Counter {
        val requests = AtomicLong()
        val failures = AtomicLong()
        val nanos = AtomicLong()
    }
    private val counters = ConcurrentHashMap<String, Counter>()
    private val cause = ThreadLocal<String>()

    fun <T> withCause(reason: String, block: () -> T): T {
        val previous = cause.get()
        cause.set(if (reason in CAUSES) reason else "other")
        try { return block() } finally {
            if (previous == null) cause.remove() else cause.set(previous)
        }
    }

    internal fun record(method: String, path: String, success: Boolean, nanos: Long) {
        val category = when {
            path == "/capabilities" -> "capabilities"
            path.startsWith("/rtc/control/") -> "rtc_control"
            path.startsWith("/events") -> "events"
            path.startsWith("/gateways") -> "metadata"
            else -> "other"
        }
        val verb = if (method == "GET") "read" else "write"
        val counter = counters.computeIfAbsent("${cause.get() ?: "other"}:$verb:$category") { Counter() }
        counter.requests.incrementAndGet()
        if (!success) counter.failures.incrementAndGet()
        counter.nanos.addAndGet(nanos.coerceAtLeast(0))
    }

    fun snapshot(): Map<String, Counts> = counters.mapValues { (_, c) ->
        Counts(c.requests.get(), c.failures.get(), c.nanos.get())
    }

    private val CAUSES = setOf("periodic", "push", "websocket", "network", "node_probe", "foreground", "rtc")
}
