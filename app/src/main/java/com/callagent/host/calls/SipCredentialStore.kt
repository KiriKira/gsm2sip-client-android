package com.callagent.host.calls

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.callagent.host.data.HostSession
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores SIP secrets and an in-flight credential-rotation key with a non-exportable Keystore key. */
internal class SipCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("host-sip-credential", Context.MODE_PRIVATE)

    @Synchronized
    fun readPassword(session: HostSession, endpointId: String, username: String): String? {
        val ivText = preferences.getString(KEY_IV, null) ?: return null
        val ciphertextText = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val sessionStamp = preferences.getString(KEY_SESSION, null) ?: return null
        if (sessionStamp != session.sessionInstanceId) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            cipher.updateAAD(aad(session, endpointId, username))
            val json = JSONObject(String(cipher.doFinal(Base64.decode(ciphertextText, Base64.NO_WRAP)), Charsets.UTF_8))
            if (json.optString("endpoint_id") != endpointId || json.optString("username") != username) return null
            json.getString("password").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    @Synchronized
    fun write(session: HostSession, endpointId: String, username: String, password: String) {
        require(endpointId.isNotBlank() && username.isNotBlank() && password.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad(session, endpointId, username))
        val clear = JSONObject()
            .put("endpoint_id", endpointId)
            .put("username", username)
            .put("password", password)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(clear)
        val committed = preferences.edit()
            .putString(KEY_SESSION, session.sessionInstanceId)
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .remove(KEY_PENDING_SESSION)
            .remove(KEY_PENDING_IV)
            .remove(KEY_PENDING_CIPHERTEXT)
            .commit()
        check(committed) { "Could not protect SIP credentials" }
    }

    /** Return the same idempotency key after a lost rotate response for this login generation. */
    @Synchronized
    fun readPendingBootstrapKey(session: HostSession): String? {
        val pendingSession = preferences.getString(KEY_PENDING_SESSION, null) ?: return null
        if (pendingSession != session.sessionInstanceId) return null
        val ivText = preferences.getString(KEY_PENDING_IV, null) ?: return null
        val ciphertextText = preferences.getString(KEY_PENDING_CIPHERTEXT, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            cipher.updateAAD(pendingAad(session))
            JSONObject(String(cipher.doFinal(Base64.decode(ciphertextText, Base64.NO_WRAP)), Charsets.UTF_8))
                .getString("idempotency_key")
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    @Synchronized
    fun writePendingBootstrapKey(session: HostSession, idempotencyKey: String) {
        require(idempotencyKey.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(pendingAad(session))
        val ciphertext = cipher.doFinal(JSONObject().put("idempotency_key", idempotencyKey).toString().toByteArray(Charsets.UTF_8))
        val committed = preferences.edit()
            .putString(KEY_PENDING_SESSION, session.sessionInstanceId)
            .putString(KEY_PENDING_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_PENDING_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
        check(committed) { "Could not protect pending SIP credential request" }
    }

    @Synchronized
    fun clearPendingBootstrapKey() {
        check(preferences.edit()
            .remove(KEY_PENDING_SESSION)
            .remove(KEY_PENDING_IV)
            .remove(KEY_PENDING_CIPHERTEXT)
            .commit()) { "Could not clear pending SIP credential request" }
    }

    @Synchronized
    fun clear() {
        check(preferences.edit().clear().commit()) { "Could not clear SIP credentials" }
    }

    @Synchronized
    fun clearForDifferentSession(session: HostSession?) {
        val acceptedSession = session?.sessionInstanceId
        val credentialSession = preferences.getString(KEY_SESSION, null)
        val pendingSession = preferences.getString(KEY_PENDING_SESSION, null)
        if (acceptedSession == null ||
            (credentialSession != null && credentialSession != acceptedSession) ||
            (pendingSession != null && pendingSession != acceptedSession)) {
            clear()
        }
    }

    private fun aad(session: HostSession, endpointId: String, username: String): ByteArray =
        "${session.sessionInstanceId}\n$endpointId\n$username".toByteArray(Charsets.UTF_8)

    private fun pendingAad(session: HostSession): ByteArray =
        "sip-bootstrap\n${session.sessionInstanceId}".toByteArray(Charsets.UTF_8)

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "gsm2sip-host-sip-credential-v1"
        const val KEY_SESSION = "session"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_PENDING_SESSION = "pending_session"
        const val KEY_PENDING_IV = "pending_iv"
        const val KEY_PENDING_CIPHERTEXT = "pending_ciphertext"
    }
}
