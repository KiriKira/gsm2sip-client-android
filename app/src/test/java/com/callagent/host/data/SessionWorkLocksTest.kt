package com.callagent.host.data

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SessionWorkLocksTest {
    @Test fun collidingPairingHashesCannotBlockIndependentNodeWork() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val pool = Executors.newFixedThreadPool(3)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val first = pool.submit { SessionWorkLocks.withLock("Aa") {
                held.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            } }
            assertTrue(held.await(2, TimeUnit.SECONDS))
            val same = pool.submit { SessionWorkLocks.withLock("Aa") { true } }
            val independent = pool.submit<Boolean> { SessionWorkLocks.withLock("BB") { true } }
            assertTrue(independent.get(2, TimeUnit.SECONDS))
            assertFalse(same.isDone)
            release.countDown()
            first.get(2, TimeUnit.SECONDS)
            same.get(2, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }
}
