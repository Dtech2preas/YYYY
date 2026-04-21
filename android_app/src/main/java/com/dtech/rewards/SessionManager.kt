package com.dtech.rewards

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dtech_prefs", Context.MODE_PRIVATE)

    fun saveUsername(username: String) {
        prefs.edit().putString("username", username).apply()
    }

    fun getUsername(): String? {
        return prefs.getString("username", null)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
