package com.callagent.host.calls

import android.content.Context
import com.callagent.host.data.HostSession
import com.callagent.host.data.profileKey

/**
 * Incoming call signaling is account-scoped (one mesh profile counts as one
 * account) and never restored by BOOT_COMPLETED.
 */
internal class CallPreferences(context: Context) {
    private val contextForMaintenance = context.applicationContext
    private val preferences = context.applicationContext.getSharedPreferences("host-call-settings", Context.MODE_PRIVATE)

    fun enabledFor(session: HostSession?): Boolean {
        if (session == null) return false
        return preferences.getBoolean(KEY_ENABLED, false) &&
            preferences.getString(KEY_SESSION, null) == session.profileKey
    }

    fun setEnabled(session: HostSession?, enabled: Boolean): Boolean {
        if (session == null) {
            return if (enabled) false else preferences.edit().clear().commit()
        }
        val editor = preferences.edit()
        if (enabled) {
            editor.putBoolean(KEY_ENABLED, true).putString(KEY_SESSION, session.profileKey)
        } else if (preferences.getString(KEY_SESSION, null) == session.profileKey) {
            editor.putBoolean(KEY_ENABLED, false).remove(KEY_SESSION)
        }
        return editor.commit().also { saved ->
            if (saved) com.callagent.host.mesh.NodeMonitor.maintain(contextForMaintenance, force = true)
        }
    }

    fun ensureEnabledFor(session: HostSession?): Boolean {
        if (session == null || session.role != "client") return false
        return enabledFor(session) || setEnabled(session, true)
    }

    fun canAutoListenFor(session: HostSession?, signalingAvailable: Boolean): Boolean =
        session?.role == "client" && signalingAvailable

    /** The SIM the last call was dialled from; the dial pad offers it first. */
    var lastDialSim: String?
        get() = preferences.getString(KEY_LAST_DIAL_SIM, null)
        set(value) { preferences.edit().putString(KEY_LAST_DIAL_SIM, value).apply() }

    fun clear() = preferences.edit().clear().commit().also { saved ->
        if (saved) com.callagent.host.mesh.NodeMonitor.maintain(contextForMaintenance, force = true)
    }

    private companion object {
        // Stored name kept from the SIP releases so the setting survives updates and rollbacks.
        const val KEY_ENABLED = "foreground_sip_opt_in"
        const val KEY_SESSION = "session_instance"
        const val KEY_LAST_DIAL_SIM = "dialer_last_sim"
    }
}
