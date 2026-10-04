package com.callagent.host

import android.view.View
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.ViewModelProvider
import com.callagent.host.data.HostSession
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class MainActivityThemeTest {
    @Test
    fun unpairedScreenInflatesMaterialInputsAndButtonsUnderExpressiveTheme() {
        val application = RuntimeEnvironment.getApplication()
        val receiverPermission = "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val declared = application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(receiverPermission in declared)
        // Android grants the application's own signature permission at install time;
        // Robolectric API 28 requires us to simulate that grant explicitly.
        Shadows.shadowOf(application).grantPermissions(receiverPermission)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val root = controller.get().findViewById<View>(android.R.id.content)
            val views = descendants(root).toList()
            assertTrue(views.any { it is TextInputLayout })
            assertTrue(views.any { it is MaterialButton })
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
