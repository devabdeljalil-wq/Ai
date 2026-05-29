package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object SecurePreferenceHelper {
    private const val PREFS_FILE = "secure_safe_vault_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"
    private const val KEY_MASTER_HASH = "master_hash"
    private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
    private const val KEY_SELF_DESTRUCT_ENABLED = "self_destruct_enabled"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_CLIPBOARD_DELAY = "clipboard_delay"
    private const val KEY_LANGUAGE = "language_code"
    private const val KEY_DARK_MODE = "dark_mode_enabled"
    private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
    private const val KEY_CREATED_FOLDERS = "created_folders"

    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        if (!::sharedPreferences.isInitialized) {
            try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                sharedPreferences = EncryptedSharedPreferences.create(
                    PREFS_FILE,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                sharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }
    }

    fun isConfigured(): Boolean {
        return sharedPreferences.contains(KEY_MASTER_HASH)
    }

    /**
     * Set up the app master password and generate the secure random SQLCipher db passphrase
     */
    fun setupMasterPassword(password: String): Boolean {
        if (password.isBlank()) return false
        val hashed = hashPassword(password)
        
        // Ensure secure random database passphrase exists and is persisted
        val dbPass = getDbPassphrase()

        sharedPreferences.edit()
            .putString(KEY_MASTER_HASH, hashed)
            .putString(KEY_DB_PASSPHRASE, dbPass)
            .putBoolean(KEY_BIOMETRICS_ENABLED, false)
            .putBoolean(KEY_SELF_DESTRUCT_ENABLED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putInt(KEY_CLIPBOARD_DELAY, 30)
            .putString(KEY_LANGUAGE, "en")
            .putBoolean(KEY_DARK_MODE, true)
            .putInt(KEY_AUTO_LOCK_TIMEOUT, 0)
            .apply()
        return true
    }

    fun verifyMasterPassword(password: String): Boolean {
        val storedHash = sharedPreferences.getString(KEY_MASTER_HASH, null) ?: return false
        val attemptHash = hashPassword(password)
        return storedHash == attemptHash
    }

    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyMasterPassword(oldPassword)) return false
        val hashed = hashPassword(newPassword)
        sharedPreferences.edit().putString(KEY_MASTER_HASH, hashed).apply()
        return true
    }

    fun getDbPassphrase(): String {
        var dbPass = sharedPreferences.getString(KEY_DB_PASSPHRASE, null)
        if (dbPass == null) {
            val secureRandom = SecureRandom()
            val randomBytes = ByteArray(16)
            secureRandom.nextBytes(randomBytes)
            dbPass = randomBytes.joinToString("") { "%02x".format(it) }
            sharedPreferences.edit().putString(KEY_DB_PASSPHRASE, dbPass).apply()
        }
        return dbPass
    }

    fun isBiometricsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_BIOMETRICS_ENABLED, false)
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRICS_ENABLED, enabled).apply()
    }

    fun isSelfDestructEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SELF_DESTRUCT_ENABLED, false)
    }

    fun setSelfDestructEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SELF_DESTRUCT_ENABLED, enabled).apply()
    }

    fun getFailedAttempts(): Int {
        return sharedPreferences.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun incrementFailedAttempts(): Int {
        val current = getFailedAttempts() + 1
        sharedPreferences.edit().putInt(KEY_FAILED_ATTEMPTS, current).apply()
        return current
    }

    fun resetFailedAttempts() {
        sharedPreferences.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply()
    }

    fun getClipboardDelay(): Int {
        return sharedPreferences.getInt(KEY_CLIPBOARD_DELAY, 30)
    }

    fun setClipboardDelay(seconds: Int) {
        sharedPreferences.edit().putInt(KEY_CLIPBOARD_DELAY, seconds).apply()
    }

    fun getLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun setLanguage(lang: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    fun isDarkMode(): Boolean {
        return sharedPreferences.getBoolean(KEY_DARK_MODE, true)
    }

    fun setDarkMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun getAutoLockTimeout(): Int {
        return sharedPreferences.getInt(KEY_AUTO_LOCK_TIMEOUT, 0)
    }

    fun setAutoLockTimeout(minutes: Int) {
        sharedPreferences.edit().putInt(KEY_AUTO_LOCK_TIMEOUT, minutes).apply()
    }

    fun getCreatedFolders(): Set<String> {
        return sharedPreferences.getStringSet(KEY_CREATED_FOLDERS, emptySet()) ?: emptySet()
    }

    fun addCreatedFolder(path: String) {
        val current = getCreatedFolders().toMutableSet()
        current.add(path)
        sharedPreferences.edit().putStringSet(KEY_CREATED_FOLDERS, current).apply()
    }

    fun removeCreatedFolder(path: String) {
        val current = getCreatedFolders().toMutableSet()
        current.remove(path)
        sharedPreferences.edit().putStringSet(KEY_CREATED_FOLDERS, current).apply()
    }

    fun wipeEverything() {
        sharedPreferences.edit().clear().apply()
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
}
