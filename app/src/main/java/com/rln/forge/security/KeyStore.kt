package com.rln.forge.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API keys, encrypted at rest with EncryptedSharedPreferences (AES256_GCM).
 *
 * Rules, no exceptions:
 * - Keys are stored per provider id, never alongside the provider config.
 * - A key value is NEVER logged, NEVER written to a file, NEVER put in an
 *   error message. Only presence ("has key" / "missing key") is reported.
 */
class KeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "rlnforge_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveKey(providerId: String, apiKey: String) {
        prefs.edit().putString(keyFor(providerId), apiKey).apply()
    }

    fun getKey(providerId: String): String? {
        return prefs.getString(keyFor(providerId), null)?.takeIf { it.isNotBlank() }
    }

    fun hasKey(providerId: String): Boolean = getKey(providerId) != null

    fun deleteKey(providerId: String) {
        prefs.edit().remove(keyFor(providerId)).apply()
    }

    private fun keyFor(providerId: String): String = "key_$providerId"
}
