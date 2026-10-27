package com.callagent.host.calls

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One in-flight fetch and at most one trailing wake, including wakes arriving
 * during completion. Cancelling a timer never cancels a running HTTP request. */
internal class CoalescedCallPoll(
    private val worker: ScheduledExecutorService,
    private val poll: () -> Unit,
    private val delay: () -> Long
) : AutoCloseable {
    private var timer: ScheduledFuture<*>? = null
    private var running = false
    private var pendingWake = false
    private var closed = false
    private var generation = 0L

    @Synchronized fun wake() {
        if (closed) return
        if (running) { pendingWake = true; return }
        schedule(0)
    }

    private fun schedule(wait: Long) {
        timer?.cancel(false)
        val ticket = ++generation
        timer = worker.schedule({ run(ticket) }, wait, TimeUnit.MILLISECONDS)
    }

    private fun run(ticket: Long) {
        synchronized(this) {
            if (closed || running || ticket != generation) return
            running = true
            timer = null
        }
        try { poll() } finally {
            synchronized(this) {
                running = false
                if (!closed) {
                    val next = if (pendingWake) 0L else delay()
                    pendingWake = false
                    schedule(next)
                }
            }
        }
    }

    @Synchronized override fun close() {
        closed = true
        pendingWake = false
        timer?.cancel(false)
        timer = null
    }
}
