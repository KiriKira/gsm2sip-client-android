package com.callagent.host.mesh

import com.callagent.host.RobolectricAndroidKeyStoreProvider
import com.callagent.host.TestAndroidKeyStoreBacking
import com.callagent.host.background.sessionStamp
import com.callagent.host.data.HostSession
import com.callagent.host.data.SessionStore
import com.callagent.host.data.clientDatabaseName
import com.callagent.host.data.databaseName
import com.callagent.host.data.sameProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class HostNodesTest {
    private val app = RuntimeEnvironment.getApplication()
    private var originalProvider: Provider? = null
    private val home = session("home", "https://home.example.test/v1")
    private val vps = session("vps", "https://vps.example.test/v1")

    @Before
    fun setUp() {
        originalProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)
        clearPreferences()
        assertTrue(SessionStore(app).writeIfCurrent(null, home))
    }

    @After
    fun tearDown() {
        clearPreferences()
        TestAndroidKeyStoreBacking.keys.clear()
        Security.removeProvider("AndroidKeyStore")
        originalProvider?.let { Security.insertProviderAt(it, 1) }
    }

    @Test
    fun addingANodeTurnsThePairingIntoAProfileWithoutMovingItsData() {
        val stampBefore = sessionStamp(home)
        val node = HostNodes.register(app, "key-vps", vps, "install-1")

        val active = SessionStore(app).read()!!
        assertEquals(home.sessionInstanceId, active.profileId)
        assertEquals("the first node keeps its database", clientDatabaseName(home.apiBaseUrl, home.ownerId, home.deviceId),
            active.databaseName())
        assertEquals("", active.nodeScope)
        assertEquals("background binding is unchanged", stampBefore, sessionStamp(active))

        val nodes = HostNodes.nodes(app)
        assertEquals(listOf(home.sessionInstanceId, vps.sessionInstanceId), nodes.map { it.sessionInstanceId })
        assertEquals(node, nodes[1])
        val stored = HostNodes.inactiveStore(app, "key-vps").read()!!
        assertTrue(stored.sameProfile(active))
        assertEquals(node.nodeScope, stored.nodeScope)
        assertTrue(HostNodes.isMultiNode(app))
        assertEquals("install-1", HostNodes.installId(app))
    }

    @Test
    fun switchingMovesSessionsBetweenStoresAndKeepsTheProfile() {
        HostNodes.register(app, "key-vps", vps, "install-1")
        val homeKey = HostNodes.nodes(app).first().key

        val switched = HostNodes.activate(app, "key-vps")!!
        assertEquals(vps.sessionInstanceId, switched.sessionInstanceId)
        val active = SessionStore(app).read()!!
        assertEquals(vps.sessionInstanceId, active.sessionInstanceId)
        assertEquals(vps.refreshToken, active.refreshToken)
        assertEquals("key-vps", HostNodes.activeNode(app)?.key)
        assertEquals(sessionStamp(home), sessionStamp(active))
        assertNull("the active node's own store is emptied", HostNodes.inactiveStore(app, "key-vps").read())
        assertEquals(home.refreshToken, HostNodes.inactiveStore(app, homeKey).read()?.refreshToken)

        assertEquals(home.sessionInstanceId, HostNodes.activate(app, homeKey)?.sessionInstanceId)
        assertEquals(home.sessionInstanceId, SessionStore(app).read()?.sessionInstanceId)
        assertEquals(vps.refreshToken, HostNodes.inactiveStore(app, "key-vps").read()?.refreshToken)
    }

    @Test
    fun losingTheActivePairingFallsBackToAnotherNode() {
        HostNodes.register(app, "key-vps", vps, "install-1")
        assertTrue(SessionStore(app).clearIfCurrent(SessionStore(app).read()!!))
        val recovered = HostNodes.recoverActive(app)
        assertEquals(vps.sessionInstanceId, recovered?.sessionInstanceId)
        assertNull("nothing to recover while a node is active", HostNodes.recoverActive(app))
    }

    @Test
    fun reorderAcceptsOnlyAPermutation() {
        HostNodes.register(app, "key-vps", vps, "install-1")
        val keys = HostNodes.nodes(app).map { it.key }
        assertFalse(HostNodes.reorder(app, keys.take(1)))
        assertTrue(HostNodes.reorder(app, keys.reversed()))
        assertEquals(keys.reversed(), HostNodes.nodes(app).map { it.key })
    }

    @Test
    fun aFreshStandalonePairingIgnoresTheOldNodeList() {
        HostNodes.register(app, "key-vps", vps, "install-1")
        val active = SessionStore(app).read()!!
        assertTrue(SessionStore(app).clearIfCurrent(active))
        val other = session("other", "https://other.example.test/v1")
        assertTrue(SessionStore(app).writeIfCurrent(null, other))
        assertTrue(HostNodes.nodes(app).isEmpty())
        assertFalse(HostNodes.isMultiNode(app))
        assertNotEquals(sessionStamp(home), sessionStamp(other))
    }

    @Test
    fun healthWithProbeHistorySurvivesAReload() {
        HostNodes.register(app, "key-vps", vps, "install-1")
        val health = NodeHealth(consecutiveSuccesses = 2, rttMillis = 70, gatewayOnline = true, checkedAt = 5,
            gatewayId = "gw-vps", callReady = true, recent = listOf(ProbeSample(true, 70, 4), ProbeSample(false, null, 5)))
        HostNodes.recordHealth(app, mapOf("key-vps" to health))
        assertEquals(health, HostNodes.health(app)["key-vps"])
    }

    private fun clearPreferences() {
        listOf("host-session", "host-nodes", "host-node-key-vps").forEach {
            app.getSharedPreferences(it, 0).edit().clear().commit()
        }
        app.getSharedPreferences("host-nodes", 0).all.clear()
        java.io.File(app.applicationInfo.dataDir, "shared_prefs").listFiles()
            ?.filter { it.name.startsWith("host-node-") }
            ?.forEach { app.getSharedPreferences(it.name.removeSuffix(".xml"), 0).edit().clear().commit() }
    }

    private fun session(id: String, url: String) = HostSession(
        sessionInstanceId = "instance-$id",
        apiBaseUrl = url,
        ownerId = "owner-$id",
        deviceId = "device-$id",
        role = "client",
        accessToken = "access-$id",
        accessExpiresAt = "2099-01-01T00:00:00Z",
        refreshToken = "refresh-$id",
        refreshExpiresAt = "2099-01-02T00:00:00Z",
        sipAvailable = false,
        sipReason = null
    )
}
