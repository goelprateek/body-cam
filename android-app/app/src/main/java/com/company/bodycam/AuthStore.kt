package com.company.bodycam

import android.content.Context

class AuthStore(context: Context) {

    private val prefs = context.getSharedPreferences("bodycam-auth", Context.MODE_PRIVATE)

    fun load(): StoredSession = StoredSession(
        backendUrl = prefs.getString(KEY_BACKEND_URL, BuildConfig.DEFAULT_BACKEND_URL).orEmpty(),
        token = prefs.getString(KEY_TOKEN, null),
        userId = prefs.getString(KEY_USER_ID, null),
        username = prefs.getString(KEY_USERNAME, "worker1").orEmpty(),
        displayName = prefs.getString(KEY_DISPLAY_NAME, null),
        role = prefs.getString(KEY_ROLE, null)
    )

    fun saveBackendUrl(backendUrl: String) {
        prefs.edit()
            .putString(KEY_BACKEND_URL, backendUrl)
            .apply()
    }

    fun saveAuthenticatedSession(
        backendUrl: String,
        token: String,
        user: StoredUser
    ) {
        prefs.edit()
            .putString(KEY_BACKEND_URL, backendUrl)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.userId)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putString(KEY_ROLE, user.role)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ROLE = "role"
    }
}

data class StoredSession(
    val backendUrl: String,
    val token: String?,
    val userId: String?,
    val username: String,
    val displayName: String?,
    val role: String?
) {
    fun toUser(): StoredUser? {
        return if (token != null && userId != null && displayName != null && role != null) {
            StoredUser(userId = userId, username = username, displayName = displayName, role = role)
        } else {
            null
        }
    }
}

data class StoredUser(
    val userId: String,
    val username: String,
    val displayName: String,
    val role: String
)
