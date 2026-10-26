package com.callagent.host.backup

import android.content.Context
import com.callagent.backup.FullBackupActivity
import com.callagent.backup.FullBackupProfile
import com.callagent.host.data.SessionStore
import com.callagent.host.mesh.HostNodes
import org.json.JSONObject
import java.io.File

class HostFullBackupActivity : FullBackupActivity() {
    override fun profile(): FullBackupProfile = HostFullBackupProfile
    override val appName = "Madoka"
}

/**
 * Madoka's full backup: every host-* preferences file, every database, and
 * the pairing of each node (sealed by Android Keystore, so carried as
 * plaintext inside the password-sealed backup). Push registration is left
 * out: it belongs to this install's FCM token, and the restored phone
 * registers its own.
 */
internal object HostFullBackupProfile : FullBackupProfile {
    override val appId = "madoka"
    private const val PUSH_PREFS = "host-push"

    private fun isSessionStore(name: String) =
        name == SessionStore.ACTIVE_PREFS || name.startsWith(HostNodes.NODE_PREFS_PREFIX)

    override fun includePreferences(name: String): Boolean =
        name.startsWith("host-") && !isSessionStore(name) && name != PUSH_PREFS

    override fun exportSecrets(context: Context): Map<String, JSONObject> =
        sessionStoreNames(context).mapNotNull { name ->
            runCatching { SessionStore(context, name).read() }.getOrNull()?.let { name to SessionStore.encode(it) }
        }.toMap()

    override fun importSecrets(context: Context, secrets: Map<String, JSONObject>) {
        // Stores the backup does not have are pairings of the replaced data.
        sessionStoreNames(context).filter { it !in secrets }.forEach { SessionStore(context, it).clear() }
        secrets.forEach { (name, json) ->
            if (isSessionStore(name)) SessionStore(context, name).replace(SessionStore.decode(json))
        }
    }

    override fun manifestExtras(context: Context): JSONObject =
        JSONObject().put("nodes", runCatching { HostNodes.nodes(context).size }.getOrDefault(0))
            .put("pairings", sessionStoreNames(context).count { runCatching { SessionStore(context, it).read() }.getOrNull() != null })

    override fun describe(manifest: JSONObject): String {
        val extras = manifest.optJSONObject("extras") ?: return ""
        val pairings = extras.optInt("pairings")
        return if (pairings > 0) "包含 $pairings 个服务器配对" else "不含服务器配对"
    }

    private fun sessionStoreNames(context: Context): List<String> =
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
            ?.map { it.name.removeSuffix(".xml") }?.filter(::isSessionStore).orEmpty()
}
