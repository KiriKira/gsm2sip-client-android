package com.callagent.host.contacts

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class HostContactsRepositoryTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun deniedPermissionSkipsProviderAndReturnsEmptyListAsynchronously() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val providerCalled = AtomicBoolean(false)
            val result = AtomicReference<Result<List<ContactEntry>>>()
            val callback = CountDownLatch(1)
            val callerThread = Thread.currentThread()
            val callbackThread = AtomicReference<Thread>()
            val repository = HostContactsRepository(
                context = context,
                worker = worker,
                callbackExecutor = Executor { it.run() },
                hasReadPermission = { false },
                readContacts = {
                    providerCalled.set(true)
                    emptyList()
                }
            )

            repository.queryAsync("Alex") {
                callbackThread.set(Thread.currentThread())
                result.set(it)
                callback.countDown()
            }

            assertTrue(callback.await(2, TimeUnit.SECONDS))
            assertEquals(emptyList<ContactEntry>(), result.get().getOrThrow())
            assertFalse(providerCalled.get())
            assertNotEquals(callerThread, callbackThread.get())
        } finally {
            worker.shutdownNow()
        }
    }

    @Test
    fun revokedPermissionDuringProviderReadDoesNotEscapeAsFailure() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val result = AtomicReference<Result<List<ContactEntry>>>()
            val callback = CountDownLatch(1)
            val repository = HostContactsRepository(
                context = context,
                worker = worker,
                callbackExecutor = Executor { it.run() },
                hasReadPermission = { true },
                readContacts = { throw SecurityException("permission revoked") }
            )

            repository.queryAsync {
                result.set(it)
                callback.countDown()
            }

            assertTrue(callback.await(2, TimeUnit.SECONDS))
            assertEquals(emptyList<ContactEntry>(), result.get().getOrThrow())
        } finally {
            worker.shutdownNow()
        }
    }

    @Test
    fun localProviderResultsRetainContactAndNumberForDialAndHistoryMatching() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            val result = AtomicReference<Result<List<ContactEntry>>>()
            val callback = CountDownLatch(1)
            val localEntry = ContactEntry(42L, 7L, "Alex Chen", "+1 555 0100", "+15550100")
            var requestedQuery: String? = null
            val repository = HostContactsRepository(
                context = context,
                worker = worker,
                callbackExecutor = Executor { it.run() },
                hasReadPermission = { true },
                readContacts = { query ->
                    requestedQuery = query
                    listOf(localEntry)
                }
            )

            repository.queryAsync("555") {
                result.set(it)
                callback.countDown()
            }

            assertTrue(callback.await(2, TimeUnit.SECONDS))
            assertEquals("555", requestedQuery)
            assertEquals(listOf(localEntry), result.get().getOrThrow())
        } finally {
            worker.shutdownNow()
        }
    }

    @Test
    fun numberQueryMatchesDigitsAcrossTheContactsOwnFormatting() {
        val entry = ContactEntry(1L, 1L, "Bob", "123 4567 8901", null)
        for (query in listOf("123", "1234", "12345678901", "4567 89", "8901")) {
            assertTrue(query, HostContactsRepository.matches(entry, query))
        }
        assertFalse(HostContactsRepository.matches(entry, "1235"))
        assertTrue(HostContactsRepository.matches(entry, "bo"))
        assertTrue(HostContactsRepository.matches(ContactEntry(2L, 2L, "Ann", "138-0013", "+8613800138000"), "13800138"))
    }
}
