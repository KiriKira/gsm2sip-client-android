package com.callagent.host.data

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RequestBudgetTest {
    @Test fun expirationFencesFollowupRequestsAndClosingCancelsTheOutstandingSocket() {
        var disconnected = false
        val socket = object : HttpURLConnection(URL("https://budget.invalid")) {
            override fun disconnect() { disconnected = true }
            override fun usingProxy() = false
            override fun connect() = Unit
        }
        val budget = RequestBudget.until(SystemClock.elapsedRealtime() + 15_000L)
        budget.use {
            it.run {
                it.attach(socket)
                ShadowSystemClock.advanceBy(Duration.ofSeconds(16))
                try { it.remainingMillis(); fail("an expired budget must prevent the next HTTP request") }
                catch (_: SocketTimeoutException) { /* expected */ }
            }
        }
        assertTrue(disconnected)
        assertNull("the worker must not carry a completed budget into unrelated work", RequestBudget.current)
    }
}
