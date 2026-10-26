package com.callagent.host.network

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NetworkEnvironmentTest {
    @Test fun observesVpnAndItsUnderlyingWifiButIgnoresRepeatedCapabilities() {
        val app = RuntimeEnvironment.getApplication()
        NetworkEnvironment.start(app)
        val callbacks = shadowOf(app.applicationContext.getSystemService(ConnectivityManager::class.java)).networkCallbacks.toList()
        assertTrue(callbacks.isNotEmpty())
        val wifi = org.robolectric.shadows.ShadowNetwork.newInstance(101)
        val wifiCaps = NetworkCapabilities().apply { shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_WIFI) }
        callbacks.forEach { it.onAvailable(wifi); it.onCapabilitiesChanged(wifi, wifiCaps) }
        val onWifi = NetworkEnvironment.token
        callbacks.forEach { it.onCapabilitiesChanged(wifi, wifiCaps) }
        assertEquals("unchanged callbacks must not restart the stability window", onWifi, NetworkEnvironment.token)
        val vpn = org.robolectric.shadows.ShadowNetwork.newInstance(102)
        callbacks.forEach { it.onAvailable(vpn); it.onCapabilitiesChanged(vpn,
            NetworkCapabilities().apply { shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_VPN) }) }
        val onVpn = NetworkEnvironment.token
        assertNotEquals(onWifi, onVpn)
        callbacks.forEach { it.onLinkPropertiesChanged(wifi, LinkProperties().apply { interfaceName = "wlan1" }) }
        assertNotEquals("underlying Wi-Fi changes matter while VPN stays present", onVpn, NetworkEnvironment.token)
    }
}
