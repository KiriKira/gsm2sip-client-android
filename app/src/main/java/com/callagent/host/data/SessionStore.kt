package com.callagent.host.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("host-session", Context.MODE_PRIVATE)
    private val alias = "gsm2sip-host-session-v1"

    fun read(): HostSession? = synchronized(processLock) { readLocked() }

    private fun readLocked(): HostSession? {
        val ivText = preferences.getString("iv", null) ?: return null
        val ciphertextText = preferences.getString("ciphertext", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            val clear = cipher.doFinal(Base64.decode(ciphertextText, Base64.NO_WRAP))
            val json = JSONObject(String(clear, Charsets.UTF_8))
            HostSession(
                sessionInstanceId = json.getString("session_instance_id"),
                apiBaseUrl = json.getString("api_base_url"),
                ownerId = json.getString("owner_id"),
                deviceId = json.getString("device_id"),
                role = json.getString("role"),
                accessToken = json.getString("access_token"),
                accessExpiresAt = json.getString("access_expires_at"),
                refreshToken = json.getString("refresh_token"),
                refreshExpiresAt = json.getString("refresh_expires_at"),
                sipAvailable = json.optJSONObject("sip")?.optBoolean("available", false) ?: false,
                sipReason = json.optJSONObject("sip")?.optString("reason")?.takeIf { it.isNotBlank() && it != "null" },
                pendingRefreshKey = json.optString("pending_refresh_key")
                    .takeIf { it.isNotBlank() && it != "null" }
            )
        } catch (_: Exception) {
            clearLocked()
            null
        }
    }

    fun writeIfCurrent(expected: HostSession?, replacement: HostSession): Boolean = synchronized(processLock) {
        val current = readLocked()
        val stillExpected = if (expected == null) current == null else
            current != null && current.sameSessionInstance(expected) && current.refreshToken == expected.refreshToken
        if (!stillExpected) return@synchronized false
        writeLocked(replacement)
        true
    }

    fun updateTokens(
        expected: HostSession,
        access: String,
        accessExpiry: String,
        refresh: String,
        refreshExpiry: String
    ): HostSession? = synchronized(processLock) {
        val current = readLocked() ?: return@synchronized null
        if (!current.sameSessionInstance(expected)) return@synchronized null
        if (current.refreshToken != expected.refreshToken) return@synchronized current
        val updated = current.copy(
            accessToken = access,
            accessExpiresAt = accessExpiry,
            refreshToken = refresh,
            refreshExpiresAt = refreshExpiry,
            pendingRefreshKey = null
        )
        writeLocked(updated)
        updated
    }

    /** Persist a retry key before refresh so process death and transport loss
     * reuse the same key with the still-current refresh token. */
    fun beginRefresh(expected: HostSession): HostSession? = synchronized(processLock) {
        val current = readLocked() ?: return@synchronized null
        if (!current.sameSessionInstance(expected) || current.refreshToken != expected.refreshToken) {
            return@synchronized null
        }
        if (current.pendingRefreshKey != null) return@synchronized current
        val pending = current.copy(pendingRefreshKey = UUID.randomUUID().toString())
        writeLocked(pending)
        pending
    }

    fun clearIfCurrent(expected: HostSession): Boolean = synchronized(processLock) {
        val current = readLocked() ?: return@synchronized false
        if (!current.sameSessionInstance(expected)) return@synchronized false
        clearLocked()
        true
    }

    private fun writeLocked(session: HostSession) {
        val json = JSONObject()
            .put("session_instance_id", session.sessionInstanceId)
            .put("api_base_url", session.apiBaseUrl)
            .put("owner_id", session.ownerId)
            .put("device_id", session.deviceId)
            .put("role", session.role)
            .put("access_token", session.accessToken)
            .put("access_expires_at", session.accessExpiresAt)
            .put("refresh_token", session.refreshToken)
            .put("refresh_expires_at", session.refreshExpiresAt)
            .put("sip", JSONObject().put("available", session.sipAvailable).put("reason", session.sipReason))
        session.pendingRefreshKey?.let { json.put("pending_refresh_key", it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val saved = preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
        if (!saved) throw java.io.IOException("Could not store the encrypted session")
    }

    private fun clearLocked() {
        if (!preferences.edit().clear().commit()) {
            throw java.io.IOException("Could not clear the encrypted session")
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private val processLock = Any()
    }
}

class ClientPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("host-settings", Context.MODE_PRIVATE)
    var apiBaseUrl: String
        get() = preferences.getString("api_base_url", "") ?: ""
        set(value) { preferences.edit().putString("api_base_url", value.trim().trimEnd('/')).apply() }
}
