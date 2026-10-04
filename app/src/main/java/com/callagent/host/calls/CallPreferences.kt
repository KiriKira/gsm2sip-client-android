package com.callagent.host.calls

import android.content.Context
import com.callagent.host.data.HostSession

/** User opt-in is account-scoped and never restored by BOOT_COMPLETED. */
internal class CallPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("host-call-settings", Context.MODE_PRIVATE)

    fun enabledFor(session: HostSession?): Boolean {
        if (session == null) return false
        return preferences.getBoolean(KEY_ENABLED, false) &&
            preferences.getString(KEY_SESSION, null) == session.sessionInstanceId
    }

    fun setEnabled(session: HostSession?, enabled: Boolean): Boolean {
        if (session == null) {
            return if (enabled) false else preferences.edit().clear().commit()
        }
        val editor = preferences.edit()
        if (enabled) {
            editor.putBoolean(KEY_ENABLED, true).putString(KEY_SESSION, session.sessionInstanceId)
        } else if (preferences.getString(KEY_SESSION, null) == session.sessionInstanceId) {
            editor.putBoolean(KEY_ENABLED, false).remove(KEY_SESSION)
        }
        return editor.commit()
    }

    fun clear() = preferences.edit().clear().commit()

    private companion object {
        const val KEY_ENABLED = "foreground_sip_opt_in"
        const val KEY_SESSION = "session_instance"
    }
}
