package com.callagent.host

import android.view.View
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.security.keystore.KeyGenParameterSpec
import android.view.ViewGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.ViewModelProvider
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.GatewaySnapshot
import com.callagent.host.data.HostSession
import com.callagent.host.data.SessionStore
import com.callagent.host.data.SimLine
import com.callagent.host.data.clientDatabaseName
import com.callagent.host.ui.PairingFormViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class MainActivityThemeTest {
    @Test
    fun unpairedScreenInflatesMaterialInputsAndButtonsUnderExpressiveTheme() {
        val application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("host-session", 0).edit().clear().commit()
        val receiverPermission = "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val declared = application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(receiverPermission in declared)
        // Android grants the application's own signature permission at install time;
        // Robolectric API 28 requires us to simulate that grant explicitly.
        Shadows.shadowOf(application).grantPermissions(receiverPermission)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = activity.findViewById<View>(android.R.id.content)
            val views = descendants(root).toList()
            assertTrue(views.any { it is TextInputLayout })
            assertTrue(views.any { it is MaterialButton })
            assertSmsArchiveEntry(activity)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun pairingFormSurvivesConfigurationRecreationButSecretIsNotSavedInBundle() {
        val application = RuntimeEnvironment.getApplication()
        val receiverPermission = "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        Shadows.shadowOf(application).grantPermissions(receiverPermission)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val server = activity.findViewById<TextInputEditText>(R.id.pairing_server)
            val code = activity.findViewById<TextInputEditText>(R.id.pairing_code)
            val deviceName = activity.findViewById<TextInputEditText>(R.id.pairing_device_name)
            server.setText("https://pairing.example.test")
            code.setText("one-time-secret-482731")
            deviceName.setText("Fold host test")

            assertFalse("pairing code view must opt out of Android view-state saving", code.isSaveEnabled)
            assertFalse("parent hierarchy saving must also skip the pairing code", code.isSaveFromParentEnabled)
            val savedState = Bundle()
            controller.saveInstanceState(savedState)
            assertEquals("https://pairing.example.test", savedState.getString("pairing.server"))
            assertEquals("Fold host test", savedState.getString("pairing.deviceName"))
            assertFalse("pairing secret must not be serialized with Activity state", containsParcelString(savedState, "one-time-secret-482731"))

            controller.recreate()
            val recreated = controller.get()
            assertEquals("https://pairing.example.test", recreated.findViewById<TextInputEditText>(R.id.pairing_server).text.toString())
            assertEquals("Fold host test", recreated.findViewById<TextInputEditText>(R.id.pairing_device_name).text.toString())
            assertEquals("one-time-secret-482731", recreated.findViewById<TextInputEditText>(R.id.pairing_code).text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun inFlightPairingCompletesOnRecreatedActivityWithoutTouchingDestroyedActivity() {
        val application = RuntimeEnvironment.getApplication()
        val receiverPermission = "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        Shadows.shadowOf(application).grantPermissions(receiverPermission)
        application.getSharedPreferences("host-session", 0).edit().clear().commit()
        application.getSharedPreferences("host-settings", 0).edit().clear().commit()

        val claimStarted = CountDownLatch(1)
        val finishClaim = CountDownLatch(1)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val oldActivity = controller.get()
            val viewModel = ViewModelProvider(oldActivity)[PairingFormViewModel::class.java]
            viewModel.setPairingClaimForTest { _, server, _, _ ->
                claimStarted.countDown()
                if (!finishClaim.await(5, TimeUnit.SECONDS)) throw java.io.IOException("Test pairing claim timed out")
                HostSession(
                    sessionInstanceId = "test-instance",
                    apiBaseUrl = server,
                    ownerId = "test-owner",
                    deviceId = "test-device",
                    role = "client",
                    accessToken = "test-access",
                    accessExpiresAt = "2099-01-01T00:00:00Z",
                    refreshToken = "test-refresh",
                    refreshExpiresAt = "2099-01-02T00:00:00Z",
                    sipAvailable = false,
                    sipReason = "test",
                )
            }
            oldActivity.findViewById<TextInputEditText>(R.id.pairing_server).setText("https://pairing.example.test")
            oldActivity.findViewById<TextInputEditText>(R.id.pairing_code).setText("test-only-code")
            oldActivity.findViewById<TextInputEditText>(R.id.pairing_device_name).setText("Retained test host")
            val pairButton = descendants(oldActivity.findViewById<View>(android.R.id.content))
                .filterIsInstance<MaterialButton>().first { it.text.toString() == "配对此手机" }
            pairButton.performClick()
            assertTrue("test claim should start", claimStarted.await(2, TimeUnit.SECONDS))

            controller.recreate()
            val recreated = controller.get()
            assertTrue("old Activity must be destroyed after configuration recreation", oldActivity.isDestroyed)
            assertFalse("duplicate pairing stays disabled while retained work is running", descendants(recreated.findViewById(android.R.id.content))
                .filterIsInstance<MaterialButton>().first { it.text.toString() == "配对此手机" }.isEnabled)

            finishClaim.countDown()
            val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
            val deadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline && !hasDashboard(recreated)) {
                mainLooper.idle()
                Thread.sleep(10)
            }
            mainLooper.idle()
            assertTrue("the retained operation should render the paired screen on the new Activity", hasDashboard(recreated))
        } finally {
            finishClaim.countDown()
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [34])
    fun pairedDashboardWithTwoCachedSimsStartsAndRecreatesWithSelectableSingleLineChips() {
        val application = RuntimeEnvironment.getApplication()
        val receiverPermission = "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        Shadows.shadowOf(application).grantPermissions(receiverPermission)
        application.getSharedPreferences("host-session", 0).edit().clear().commit()
        application.getSharedPreferences("host-settings", 0).edit().clear().commit()

        val originalProvider = Security.getProvider("AndroidKeyStore")
        Security.removeProvider("AndroidKeyStore")
        TestAndroidKeyStoreBacking.keys.clear()
        Security.insertProviderAt(RobolectricAndroidKeyStoreProvider(), 1)

        val session = HostSession(
            sessionInstanceId = "robolectric-paired-dashboard-instance",
            apiBaseUrl = "https://127.0.0.1:1",
            ownerId = "robolectric-dashboard-owner",
            deviceId = "robolectric-dashboard-client",
            role = "client",
            accessToken = "test-only-access-token",
            accessExpiresAt = "2099-01-01T00:00:00Z",
            refreshToken = "test-only-refresh-token",
            refreshExpiresAt = "2099-01-01T00:00:00Z",
            sipAvailable = false,
            sipReason = "test-only fixture",
        )
        val databaseName = clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
        application.deleteDatabase(databaseName)
        var controller: org.robolectric.android.controller.ActivityController<MainActivity>? = null
        try {
            assertTrue(SessionStore(application).writeIfCurrent(null, session))
            ClientDatabase(application, databaseName).apply {
                saveGateway(GatewaySnapshot(
                    gatewayId = "robolectric-gateway",
                    deviceName = "Synthetic cached gateway",
                    online = false,
                    lastSeenAt = "2026-10-04T09:30:00Z",
                    mappingRevision = 42,
                    root = false,
                    sipRegistered = false,
                    batteryPercent = 84,
                    charging = false,
                ))
                replaceSims(listOf(
                    SimLine("robo-sim-primary", 0, "Synthetic primary line with a readable long identity",
                        "Synthetic Carrier A", "+15550001001", "active", 42, true, "in_service"),
                    SimLine("robo-sim-secondary", 1, "Synthetic secondary line",
                        "Synthetic Carrier B", "+15550001002", "active", 42, true, "in_service"),
                ))
                close()
            }

            val activityController = Robolectric.buildActivity(MainActivity::class.java)
            controller = activityController
            activityController.setup()
            assertDashboardSims(activityController.get())
            assertSmsArchiveEntry(activityController.get())

            val initialChips = chips(activityController.get())
            initialChips[1].performClick()
            assertTrue("second cached SIM should remain selectable", chips(activityController.get())[1].isChecked)
            assertDashboardSims(activityController.get())

            activityController.recreate()
            assertDashboardSims(activityController.get())
            val recreatedChips = chips(activityController.get())
            assertEquals("single-selection dashboard should check only one cached SIM", 1, recreatedChips.count { it.isChecked })
            assertTrue("selected SIM should survive Activity recreation", recreatedChips[1].isChecked)
        } finally {
            controller?.let { runCatching { it.pause().stop().destroy() } }
            runCatching { SessionStore(application).clearIfCurrent(session) }
            application.deleteDatabase(databaseName)
            TestAndroidKeyStoreBacking.keys.clear()
            Security.removeProvider("AndroidKeyStore")
            originalProvider?.let { Security.insertProviderAt(it, 1) }
        }
    }

    private fun assertDashboardSims(activity: MainActivity) {
        assertTrue("paired dashboard should be rendered", hasDashboard(activity))
        val chips = chips(activity)
        assertEquals(2, chips.size)
        assertEquals("SIM 1", chips[0].text.toString())
        assertEquals("SIM 2", chips[1].text.toString())
        assertTrue("selection chips must stay single-line", chips.all { it.isSingleLine && it.maxLines == 1 })
        assertTrue(chips[0].isCheckable)
        assertTrue(chips[0].contentDescription.toString().contains("Synthetic primary line with a readable long identity"))
        assertTrue(chips[0].contentDescription.toString().contains("+15550001001"))
        val visibleText = descendants(activity.findViewById<View>(android.R.id.content))
            .filterIsInstance<android.widget.TextView>()
            .map { it.text.toString() }
        assertTrue("full SIM identity should remain readable outside the compact selector",
            visibleText.any { it.contains("Synthetic primary line with a readable long identity") && it.contains("+15550001001") })
    }

    private fun assertSmsArchiveEntry(activity: MainActivity) {
        val entry = activity.findViewById<MaterialButton>(R.id.sms_backup_archive_entry)
        assertEquals("短信备份与归档", entry.text.toString())
        assertTrue("archive entry must remain reachable on paired and unpaired screens", entry.isEnabled)
    }

    private fun chips(activity: MainActivity): List<Chip> {
        val group = activity.findViewById<ChipGroup>(R.id.remote_sim_selector)
        return (0 until group.childCount).map { group.getChildAt(it) as Chip }
    }

    private fun containsParcelString(bundle: Bundle, value: String): Boolean {
        val parcel = Parcel.obtain()
        return try {
            bundle.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            val candidates = listOf(value.toByteArray(Charsets.UTF_8), value.toByteArray(Charsets.UTF_16LE))
            candidates.any { needle ->
                if (needle.isEmpty() || needle.size > bytes.size) false
                else (0..bytes.size - needle.size).any { offset ->
                    needle.indices.all { bytes[offset + it] == needle[it] }
                }
            }
        } finally {
            parcel.recycle()
        }
    }

    private fun hasDashboard(activity: MainActivity): Boolean =
        descendants(activity.findViewById(android.R.id.content)).filterIsInstance<android.widget.TextView>()
            .any { it.text.toString() == "远程 SIM 卡" }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}

internal object TestAndroidKeyStoreBacking {
    val keys = ConcurrentHashMap<String, Key>()
}

internal class RobolectricAndroidKeyStoreProvider : Provider(
    "AndroidKeyStore", 1.0, "In-memory AndroidKeyStore replacement for Robolectric tests"
) {
    init {
        put("KeyStore.AndroidKeyStore", RobolectricAndroidKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", RobolectricAndroidAesKeyGenerator::class.java.name)
    }
}

public class RobolectricAndroidKeyStoreSpi : KeyStoreSpi() {
    override fun engineGetKey(alias: String, password: CharArray?): Key? = TestAndroidKeyStoreBacking.keys[alias]
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
    override fun engineGetCertificate(alias: String): Certificate? = null
    override fun engineGetCreationDate(alias: String): Date? = null
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) {
        TestAndroidKeyStoreBacking.keys[alias] = key
    }
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<out Certificate>?) {
        throw UnsupportedOperationException("byte-array key import is not used in this test")
    }
    override fun engineSetCertificateEntry(alias: String, cert: Certificate) = Unit
    override fun engineDeleteEntry(alias: String) { TestAndroidKeyStoreBacking.keys.remove(alias) }
    override fun engineAliases(): Enumeration<String> = Collections.enumeration(TestAndroidKeyStoreBacking.keys.keys)
    override fun engineContainsAlias(alias: String): Boolean = TestAndroidKeyStoreBacking.keys.containsKey(alias)
    override fun engineSize(): Int = TestAndroidKeyStoreBacking.keys.size
    override fun engineIsKeyEntry(alias: String): Boolean = TestAndroidKeyStoreBacking.keys.containsKey(alias)
    override fun engineIsCertificateEntry(alias: String): Boolean = false
    override fun engineGetCertificateAlias(cert: Certificate): String? = null
    override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
}

public class RobolectricAndroidAesKeyGenerator : KeyGeneratorSpi() {
    private var random: SecureRandom = SecureRandom()
    private var alias: String? = null

    override fun engineInit(random: SecureRandom?) {
        if (random != null) this.random = random
    }

    override fun engineInit(params: java.security.spec.AlgorithmParameterSpec, random: SecureRandom?) {
        alias = (params as? KeyGenParameterSpec)?.keystoreAlias
        if (random != null) this.random = random
    }

    override fun engineInit(keysize: Int, random: SecureRandom?) {
        if (random != null) this.random = random
    }

    override fun engineGenerateKey(): SecretKey {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return SecretKeySpec(bytes, "AES").also { key -> alias?.let { TestAndroidKeyStoreBacking.keys[it] = key } }
    }
}
