package com.callagent.host.background

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostUserStopPersistenceTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun cleanUp() {
        RuntimeState.preferences(context).edit().clear().commit()
    }

    @Test
    fun observingSystemStopPersistsItAcrossLaterOrdinaryExits() {
        val state = RuntimeState.preferences(context)
        state.edit().putBoolean(RuntimeState.KEY_ENABLED, true)
            .putLong(RuntimeState.KEY_EXPLICIT_ENABLE_AT, 100L).commit()
        addExit(ApplicationExitInfo.REASON_USER_REQUESTED, 200L)
        assertFalse(HostBackgroundRuntime.snapshot(context).enabled)
        assertTrue(state.getBoolean(RuntimeState.KEY_TASK_MANAGER_STOPPED, false))
        addExit(ApplicationExitInfo.REASON_EXIT_SELF, 300L)
        assertFalse(HostBackgroundRuntime.snapshot(context).enabled)
        assertTrue(state.getBoolean(RuntimeState.KEY_TASK_MANAGER_STOPPED, false))
        assertFalse(HostBackgroundRuntime.restoreAllowed(context))
    }

    @Test
    fun olderSystemStopDoesNotOverrideANewerExplicitEnable() {
        RuntimeState.preferences(context).edit().putBoolean(RuntimeState.KEY_ENABLED, true)
            .putLong(RuntimeState.KEY_EXPLICIT_ENABLE_AT, 300L).commit()
        addExit(ApplicationExitInfo.REASON_USER_REQUESTED, 200L)
        assertTrue(HostBackgroundRuntime.snapshot(context).enabled)
    }

    private fun addExit(reason: Int, timestamp: Long) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exit = ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
            .setProcessName(context.packageName).setReason(reason).setTimestamp(timestamp).build()
        Shadows.shadowOf(manager).addApplicationExitInfo(exit)
    }
}
