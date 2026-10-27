package com.callagent.host.calls

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CoalescedCallPollTest {
    @Test fun burstDuringFetchProducesOneTrailingFetchAndCloseFencesQueuedWakes() {
        val worker = Executors.newSingleThreadScheduledExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val trailing = CountDownLatch(1)
        val requests = AtomicInteger()
        val polling = CoalescedCallPoll(worker, {
            if (requests.incrementAndGet() == 1) {
                entered.countDown()
                check(release.await(3, TimeUnit.SECONDS))
            } else trailing.countDown()
        }, { 60_000L })
        try {
            polling.wake()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            repeat(100) { polling.wake() }
            release.countDown()
            assertTrue(trailing.await(3, TimeUnit.SECONDS))
            polling.close()
            repeat(10) { polling.wake() }
            worker.submit {}.get(3, TimeUnit.SECONDS)
            assertEquals(2, requests.get())
        } finally {
            release.countDown()
            polling.close()
            worker.shutdownNow()
        }
    }
}
