package dev.balaji.moonrose.logtrain.auth

import java.util.prefs.Preferences

private const val TOKEN_KEY = "token"

class PreferencesAuthTokenStore : AuthTokenStore {
    private val prefs = Preferences.userRoot().node("dev/balaji/moonrose/logtrain/auth")

    override fun getToken(): String? = prefs.get(TOKEN_KEY, null)

    override fun setToken(token: String) {
        prefs.put(TOKEN_KEY, token)
    }

    override fun clearToken() {
        prefs.remove(TOKEN_KEY)
    }
}

actual fun createAuthTokenStore(): AuthTokenStore = PreferencesAuthTokenStore()
