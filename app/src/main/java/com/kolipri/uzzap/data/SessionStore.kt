package com.kolipri.uzzap.data

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small encrypted session store. Passwords are never persisted. */
object SessionStore {
    private const val PREFS = "uzzap_session"
    private const val KEY_ALIAS = "uzzap_session_key"
    private const val ACCESS = "access_token"
    private const val REFRESH = "refresh_token"
    private const val EXPIRES_AT = "expires_at"
    private const val EMAIL = "email"
    private const val USERNAME = "username"
    private const val NICKNAME = "nickname"
    private const val ROLE = "role"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureKey()
    }

    private fun ensureReady() = check(::prefs.isInitialized) { "SessionStore.init() must be called first" }

    private fun ensureKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        ensureReady()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, ensureKey())
        val combined = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        ensureReady()
        val combined = Base64.decode(value, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, ensureKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }.getOrNull()

    /** username is the canonical account/login name; nickname is display-only. */
    fun save(
        accessToken: String,
        refreshToken: String?,
        expiresAtMs: Long,
        email: String,
        nickname: String,
        role: String = "user",
        username: String = ""
    ) {
        ensureReady()
        prefs.edit()
            .putString(ACCESS, encrypt(accessToken))
            .putString(REFRESH, refreshToken?.let(::encrypt))
            .putLong(EXPIRES_AT, expiresAtMs)
            .putString(EMAIL, email)
            .putString(USERNAME, username)
            .putString(NICKNAME, nickname)
            .putString(ROLE, role)
            .apply()
    }

    fun accessToken(): String? = if (::prefs.isInitialized) prefs.getString(ACCESS, null)?.let(::decrypt) else null
    fun refreshToken(): String? = if (::prefs.isInitialized) prefs.getString(REFRESH, null)?.let(::decrypt) else null
    fun expiresAtMs(): Long = if (::prefs.isInitialized) prefs.getLong(EXPIRES_AT, 0L) else 0L
    fun email(): String = if (::prefs.isInitialized) prefs.getString(EMAIL, "").orEmpty() else ""
    fun username(): String = if (::prefs.isInitialized) prefs.getString(USERNAME, "").orEmpty() else ""
    fun nickname(): String = if (::prefs.isInitialized) prefs.getString(NICKNAME, "").orEmpty() else ""
    fun role(): String = if (::prefs.isInitialized) prefs.getString(ROLE, "user").orEmpty() else "user"

    fun clear() {
        if (::prefs.isInitialized) prefs.edit().clear().apply()
    }
}
