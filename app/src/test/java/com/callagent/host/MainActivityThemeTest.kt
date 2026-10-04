package com.callagent.host

import android.view.View
import android.content.pm.PackageManager
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

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

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
