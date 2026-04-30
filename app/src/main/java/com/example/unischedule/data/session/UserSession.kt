package com.example.unischedule.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Phase 2: Persistent session management using EncryptedSharedPreferences.
 * Session survives app restarts. All data is AES-256 encrypted.
 *
 * Legacy in-memory fields are kept for backward compatibility with existing code
 * that reads UserSession.userId / UserSession.userRole directly.
 * save() and load() synchronize the two layers.
 */
object UserSession {
    private const val PREFS_NAME = "user_session_encrypted"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ROLE = "user_role"
    private const val KEY_USERNAME = "username"
    private const val KEY_MUST_CHANGE = "must_change_password"

    // Legacy in-memory fields — kept for backward compatibility
    var userId: Long? = null
    var userRole: Role? = null
    var userName: String? = null

    enum class Role {
        ADMIN, LECTURER, INSTRUCTOR, STUDENT
    }

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Save session to both memory and encrypted persistent storage.
     */
    fun save(context: Context, id: Long, role: Role, username: String, mustChange: Boolean = false) {
        // In-memory (backward compat)
        userId = id
        userRole = role
        userName = username

        // Persistent encrypted storage
        getPrefs(context).edit()
            .putLong(KEY_USER_ID, id)
            .putString(KEY_ROLE, role.name)
            .putString(KEY_USERNAME, username)
            .putBoolean(KEY_MUST_CHANGE, mustChange)
            .apply()
    }

    /**
     * Load session from encrypted persistent storage into memory fields.
     * Returns true if a valid session was found.
     */
    fun load(context: Context): Boolean {
        val prefs = getPrefs(context)
        val storedId = prefs.getLong(KEY_USER_ID, -1L)
        if (storedId == -1L) return false

        val roleName = prefs.getString(KEY_ROLE, null) ?: return false
        userId = storedId
        userRole = try { Role.valueOf(roleName) } catch (_: Exception) { null }
        userName = prefs.getString(KEY_USERNAME, null)
        return true
    }

    fun mustChangePassword(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MUST_CHANGE, false)
    }

    fun getUserIdFromPrefs(context: Context): Long? {
        val id = getPrefs(context).getLong(KEY_USER_ID, -1L)
        return if (id == -1L) null else id
    }

    fun getRoleFromPrefs(context: Context): Role? {
        val roleName = getPrefs(context).getString(KEY_ROLE, null) ?: return null
        return try { Role.valueOf(roleName) } catch (_: Exception) { null }
    }

    /**
     * Logout: clear both memory and persistent storage.
     */
    fun logout(context: Context) {
        userId = null
        userRole = null
        userName = null
        getPrefs(context).edit().clear().apply()
    }

    /**
     * Legacy logout (no context) — clears memory only.
     * Kept for backward compatibility with existing code.
     */
    fun logout() {
        userId = null
        userRole = null
        userName = null
    }
}
