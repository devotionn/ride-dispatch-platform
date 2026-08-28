package com.funccrypto.ridedispatch.driver.auth

import android.content.Context
import com.funccrypto.ridedispatch.driver.domain.DriverSession

class SessionStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val token: String?
        get() = preferences.getString(KEY_TOKEN, null)

    fun save(session: DriverSession) {
        preferences.edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_EXPIRES_AT, session.expiresAt)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES = "driver_session"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}
