package com.walid.applock.data

import android.content.Context

enum class CredentialType { PIN, PATTERN }

class CredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("credential", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains(KEY_HASH)

    val type: CredentialType
        get() = if (prefs.getString(KEY_TYPE, null) == CredentialType.PATTERN.name) {
            CredentialType.PATTERN
        } else {
            CredentialType.PIN
        }

    fun save(type: CredentialType, secret: String) {
        val h = CredentialHasher.hash(secret)
        prefs.edit()
            .putString(KEY_TYPE, type.name)
            .putString(KEY_ALG, h.algorithm)
            .putInt(KEY_ITER, h.iterations)
            .putString(KEY_SALT, h.saltHex)
            .putString(KEY_HASH, h.hashHex)
            .apply()
    }

    fun verify(secret: String): Boolean {
        val stored = CredentialHasher.Hashed(
            algorithm = prefs.getString(KEY_ALG, null) ?: return false,
            iterations = prefs.getInt(KEY_ITER, CredentialHasher.ITERATIONS),
            saltHex = prefs.getString(KEY_SALT, null) ?: return false,
            hashHex = prefs.getString(KEY_HASH, null) ?: return false,
        )
        return CredentialHasher.verify(secret, stored)
    }

    private companion object {
        const val KEY_TYPE = "type"
        const val KEY_ALG = "alg"
        const val KEY_ITER = "iter"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
    }
}
