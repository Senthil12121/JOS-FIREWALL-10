package com.jos.firewall.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Manages secure application lock, passcode hashing, and authentication state.
 */
class PasscodeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "jos_security_prefs"
        private const val KEY_PASSCODE_ENABLED = "passcode_enabled"
        private const val KEY_PASSCODE_HASH = "passcode_hash"
        private const val KEY_PASSCODE_SALT = "passcode_salt"
        private const val KEY_PASSCODE_TYPE = "passcode_type" // "pin" or "password"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LOCK_TIMEOUT = "lock_timeout_ms"
        private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"

        @Volatile
        private var isUnlockedSession: Boolean = false

        fun isSessionUnlocked(): Boolean = isUnlockedSession

        fun setSessionUnlocked(unlocked: Boolean) {
            isUnlockedSession = unlocked
        }
    }

    fun isPasscodeEnabled(): Boolean {
        return prefs.getBoolean(KEY_PASSCODE_ENABLED, false) &&
                !prefs.getString(KEY_PASSCODE_HASH, null).isNullOrEmpty()
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getPasscodeType(): String {
        return prefs.getString(KEY_PASSCODE_TYPE, "pin") ?: "pin"
    }

    fun setPasscode(passcode: String, isPin: Boolean = true) {
        val salt = generateSalt()
        val hash = hashPasscode(passcode, salt)

        prefs.edit()
            .putBoolean(KEY_PASSCODE_ENABLED, true)
            .putString(KEY_PASSCODE_HASH, hash)
            .putString(KEY_PASSCODE_SALT, salt)
            .putString(KEY_PASSCODE_TYPE, if (isPin) "pin" else "password")
            .apply()

        isUnlockedSession = true
    }

    fun verifyPasscode(enteredPasscode: String): Boolean {
        val storedHash = prefs.getString(KEY_PASSCODE_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PASSCODE_SALT, null) ?: return false

        val enteredHash = hashPasscode(enteredPasscode, storedSalt)
        val matches = storedHash == enteredHash
        if (matches) {
            isUnlockedSession = true
        }
        return matches
    }

    fun removePasscode() {
        prefs.edit()
            .putBoolean(KEY_PASSCODE_ENABLED, false)
            .remove(KEY_PASSCODE_HASH)
            .remove(KEY_PASSCODE_SALT)
            .remove(KEY_PASSCODE_TYPE)
            .apply()

        isUnlockedSession = true
    }

    fun recordBackgroundTime() {
        prefs.edit().putLong(KEY_LAST_BACKGROUND_TIME, System.currentTimeMillis()).apply()
    }

    fun shouldLockOnResume(): Boolean {
        if (!isPasscodeEnabled()) return false
        if (!isUnlockedSession) return true

        val lastBg = prefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L)
        val timeout = prefs.getLong(KEY_LOCK_TIMEOUT, 0L) // 0 = immediately on leave
        if (lastBg > 0 && timeout > 0) {
            val elapsed = System.currentTimeMillis() - lastBg
            if (elapsed > timeout) {
                isUnlockedSession = false
                return true
            }
            return false
        }
        // Immediate lock
        isUnlockedSession = false
        return true
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(salt)
        } else {
            android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
        }
    }

    private fun hashPasscode(passcode: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt.toByteArray(Charsets.UTF_8))
        val digest = md.digest(passcode.toByteArray(Charsets.UTF_8))
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(digest)
        } else {
            android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
        }
    }
}
