package com.callagent.host.data

import com.callagent.host.RobolectricAndroidKeyStoreProvider
import com.callagent.host.TestAndroidKeyStoreBacking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.Provider
import java.security.Security

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStoreReadCacheTest {
    private val app = RuntimeEnvironment.getApplication()
    private val name = "read-cache-test"
    private var oldProvider: Provider? = null
    private val initial = HostSession("instance", "https://example.test/v1", "owner", "device", "client",
        "access", "2099-01-01T00:00:00Z", "refresh", "2099-01-02T00:00:00Z", false, null)

    @Before fun setUp() {
        oldProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        SessionStore(app, name).clear()
    }
    @After fun tearDown() {
        SessionStore(app, name).clear()
        SessionStore(app, name + "-other").clear()
        TestAndroidKeyStoreBacking.keys.clear()
        Security.removeProvider("AndroidKeyStore")
        oldProvider?.let { Security.insertProviderAt(it, 1) }
    }

    @Test fun repeatedReadersReuseDecryptionButObserveTokenRefreshAndClear() {
        val first = SessionStore(app, name)
        val second = SessionStore(app, name)
        assertTrue(first.writeIfCurrent(null, initial))
        assertSame(initial, second.read())
        val updated = second.updateTokens(initial, "new-access", "2099-02-01T00:00:00Z",
            "new-refresh", "2099-02-02T00:00:00Z")!!
        assertSame(updated, first.read())
        assertFalse(first.writeIfCurrent(initial, initial))
        assertTrue(second.clearIfCurrent(updated))
        assertNull(first.read())
    }

    @Test fun changedCiphertextFailsClosedAndCannotReturnCachedCredentials() {
        val store = SessionStore(app, name)
        store.replace(initial)
        assertNotNull(store.read())
        app.getSharedPreferences(name, 0).edit().putString("ciphertext", "AA==").commit()
        assertNull(SessionStore(app, name).read())
        assertNull(store.read())
        assertTrue(app.getSharedPreferences(name, 0).all.isEmpty())
    }

    @Test fun SeparateNodeStoresCannotShareTheCachedIdentity() {
        val first = SessionStore(app, name)
        val other = SessionStore(app, name + "-other")
        first.replace(initial)
        val backup = initial.copy(sessionInstanceId = "backup", deviceId = "other-device")
        other.replace(backup)
        assertEquals(initial, first.read())
        assertEquals(backup, other.read())
        other.clear()
        assertEquals(initial, first.read())
    }
}
