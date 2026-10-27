package com.callagent.host.data

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One wall-clock budget across capability, auth refresh and control fetch.
 * Disconnect outstanding sockets at the deadline, even if bytes keep arriving.
 * The watchdog is a normal timer; it neither wakes a sleeping device nor holds power. */
internal class RequestBudget private constructor(val deadline: Long) : AutoCloseable {
    private val connections = mutableSetOf<HttpURLConnection>()
    @Volatile private var cancelled = false
    private val expiry = watchdog.schedule({ cancel() }, remainingMillis(), TimeUnit.MILLISECONDS)

    fun remainingMillis(): Long {
        val remaining = deadline - SystemClock.elapsedRealtime()
        if (cancelled || remaining <= 0 || Thread.currentThread().isInterrupted) throw SocketTimeoutException("Work budget exhausted")
        return remaining
    }

    @Synchronized fun attach(connection: HttpURLConnection) {
        remainingMillis()
        connections.add(connection)
    }

    @Synchronized fun detach(connection: HttpURLConnection) { connections.remove(connection) }

    private fun cancel() {
        val sockets = synchronized(this) {
            cancelled = true
            connections.toList().also { connections.clear() }
        }
        sockets.forEach { runCatching { it.disconnect() } }
    }

    fun <T> run(block: () -> T): T {
        val previous = active.get()
        active.set(this)
        try { remainingMillis(); return block() } finally {
            if (previous == null) active.remove() else active.set(previous)
        }
    }

    override fun close() { expiry.cancel(false); cancel() }

    companion object {
        private val active = ThreadLocal<RequestBudget>()
        val current: RequestBudget? get() = active.get()
        private val watchdog = ScheduledThreadPoolExecutor(2) { work ->
            Thread(work, "HTTP-work-budget").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
        fun until(deadline: Long): RequestBudget = RequestBudget(minOf(deadline, current?.deadline ?: Long.MAX_VALUE))
    }
}
