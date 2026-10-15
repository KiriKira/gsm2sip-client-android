package com.callagent.host.calls

import android.content.Context
import com.callagent.host.data.ClientDatabase
import com.callagent.host.data.SessionStore
import com.callagent.host.data.databaseName

/** What a call shows for its remote SIM: slot and number from the cached SIM list, never the SIM's internal id. */
internal object SimLabels {
    fun describe(context: Context, simId: String?): String {
        if (simId == null) return ""
        return runCatching {
            val session = SessionStore(context).read() ?: return@runCatching FALLBACK
            ClientDatabase(context, session.databaseName()).use { db ->
                db.loadSims().firstOrNull { it.simId == simId }
                    ?.let { "SIM${it.slotIndex + 1}${it.phoneNumber?.let { number -> " · $number" }.orEmpty()}" }
                    ?: FALLBACK
            }
        }.getOrDefault(FALLBACK)
    }

    private const val FALLBACK = "远程 SIM"
}
