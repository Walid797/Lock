package com.walid.applock.data

import android.content.Context
import com.walid.applock.auth.Method

class LockRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("applock", Context.MODE_PRIVATE)

    var lockedPackages: Set<String>
        get() = prefs.getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LOCKED, value.toSet()).apply()

    fun setLocked(pkg: String, locked: Boolean) {
        lockedPackages = if (locked) lockedPackages + pkg else lockedPackages - pkg
    }

    /** Priority order of the biometric methods (PIN/pattern is always last). */
    var methodOrder: List<Method>
        get() = parseOrder(prefs.getString(KEY_ORDER, null))
        set(value) = prefs.edit().putString(KEY_ORDER, value.joinToString(",") { it.name }).apply()

    fun isEnabled(method: Method) = prefs.getBoolean(KEY_ENABLED + method.name, true)

    fun setEnabled(method: Method, enabled: Boolean) =
        prefs.edit().putBoolean(KEY_ENABLED + method.name, enabled).apply()

    /** Try the system prompt for the face step even if the phone does not announce face unlock. */
    var forceFace: Boolean
        get() = prefs.getBoolean(KEY_FORCE_FACE, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_FACE, value).apply()

    fun enabledOrder(): List<Method> = methodOrder.filter(::isEnabled)

    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILS, 0)
        set(value) = prefs.edit().putInt(KEY_FAILS, value).apply()

    /** Wall-clock time (ms) until which PIN/pattern entry is blocked. */
    var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKOUT, value).apply()

    companion object {
        private const val KEY_LOCKED = "locked"
        private const val KEY_ORDER = "order"
        private const val KEY_ENABLED = "enabled_"
        private const val KEY_FORCE_FACE = "force_face"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCKOUT = "lockout"

        val DEFAULT_ORDER = listOf(Method.FACE, Method.FINGERPRINT)
        const val MAX_FREE_ATTEMPTS = 5

        fun parseOrder(raw: String?): List<Method> {
            val parsed = raw.orEmpty().split(",")
                .mapNotNull { name -> DEFAULT_ORDER.firstOrNull { it.name == name } }
                .distinct()
            return parsed + DEFAULT_ORDER.filter { it !in parsed }
        }

        /** 0 for the first few wrong attempts, then 30s, 60s, 120s… capped at 32 min. */
        fun lockoutMillis(failedAttempts: Int): Long {
            if (failedAttempts < MAX_FREE_ATTEMPTS) return 0L
            return 30_000L shl minOf(failedAttempts - MAX_FREE_ATTEMPTS, 6)
        }
    }
}
