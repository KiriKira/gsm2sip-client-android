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
        callbacks.forEach { it.onLinkPropertiesChanged(wifi, LinkProperties().apply { interfaceName = "wlan0" }) }
        assertEquals("link details of a path that is not the default do not restart the window", onVpn, NetworkEnvironment.token)
        val mobile = org.robolectric.shadows.ShadowNetwork.newInstance(103)
        callbacks.forEach { it.onLost(wifi) }
        callbacks.forEach { it.onAvailable(mobile); it.onCapabilitiesChanged(mobile,
            NetworkCapabilities().apply { shadowOf(this).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR) }) }
        assertNotEquals("the VPN moving from Wi-Fi to mobile matters", onVpn, NetworkEnvironment.token)
    }
}
